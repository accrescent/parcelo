// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.appstore.publish.v1alpha1.UploadAppDraftResult
import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftUploadProcessingJob
import app.accrescent.server.parcelo.data.AppPackage
import app.accrescent.server.parcelo.data.AppPackagePermission
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.parsers.ApkSet
import app.accrescent.server.parcelo.parsers.ApkSetParseError
import app.accrescent.server.parcelo.util.TempFile
import arrow.core.getOrElse
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.SubscriberInterface
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.pubsub.v1.PubsubMessage
import io.grpc.Status
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.io.path.Path
import com.google.rpc.Status as GoogleStatus

private const val EVENT_TIME_KEY = "eventTime"

private const val OBJECT_ORPHAN_TIMEOUT_DAYS = 1L

@ApplicationScoped
class AppDraftUploadedProcessor @Inject constructor(
    private val config: ParceloConfig,
    private val pubSubHelper: PubSubHelper,
    private val storage: Storage,
) {
    private companion object {
        private val LOG = Logger.getLogger(AppDraftUploadedProcessor::class.java)
    }

    private val messageReceiver =
        MessageReceiver { message, consumer -> processMessage(message, consumer) }
    private lateinit var subscriber: SubscriberInterface

    fun onStart(@Observes startupEvent: StartupEvent) {
        val config = config.objectStorageNotifications().appDraftUploads()
        subscriber = pubSubHelper.createSubscriber(
            config.pubSubProjectId(),
            config.pubSubSubscriptionName(),
            messageReceiver,
        )
        subscriber.startAsync().awaitRunning()
    }

    fun onStop(@Observes shutdownEvent: ShutdownEvent) {
        subscriber.stopAsync()
    }

    // There are three notable phenomena which this handler can observe that must be handled to
    // ensure it operates in a predictable and consistent way:
    //
    // 1. Pub/Sub message redelivery: Pub/Sub guarantees at-least-once delivery by default [1], so we
    //    must always be prepared to handle redelivery of object upload events.
    // 2. Pub/Sub message reordering: Pub/Sub has no message ordering guarantees by default [1], so
    //    we must always be prepared to handle, e.g., the second upload in a series of two object
    //    uploads being received before the first.
    // 3. Concurrent processing: It is possible that multiple server instances end up processing the
    //    same object upload event concurrently, e.g., if Pub/Sub considers a live server no longer
    //    active and leases an upload event it is processing to another server. Thus, we must ensure
    //    this concurrency doesn't result in an inconsistent state.
    //
    // We handle these phenomena in the following ways:
    //
    // 1. Make processing idempotent. Processing logic is designed such that it can be
    //    repeated, overwriting the result of previous processing.
    // 2. Process only new event timestamps. When processing succeeds, the timestamp of the
    //    triggering Pub/Sub event is saved to the new app package database entity. If an upload
    //    event for a given object arrives with an equal or earlier timestamp than previously
    //    recorded, it is skipped because it is obsoleted by the later upload event.
    // 3. Use proper transaction isolation. Saving a successful processing result occurs in a highly
    //    consistent PostgreSQL SERIALIZABLE transaction, ensuring that processing doesn't result
    //    in undesirable state, e.g., untracked orphan blobs.
    //
    // [1]: https://docs.cloud.google.com/pubsub/docs/subscription-overview#default_properties
    fun processMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        val eventType = message.attributesMap[EVENT_TYPE_KEY] ?: run {
            LOG.error("received message without $EVENT_TYPE_KEY key")
            consumer.nack()
            return
        }
        if (eventType != EVENT_TYPE_OBJECT_FINALIZE) {
            LOG.error("expected event type of $EVENT_TYPE_OBJECT_FINALIZE but got $eventType")
            consumer.nack()
            return
        }
        val bucketId = message.attributesMap[BUCKET_ID_KEY] ?: run {
            LOG.error("received message without $BUCKET_ID_KEY key")
            consumer.nack()
            return
        }
        val objectId = message.attributesMap[OBJECT_ID_KEY] ?: run {
            LOG.error("received message without $OBJECT_ID_KEY key")
            consumer.nack()
            return
        }
        val eventTime = message
            .attributesMap[EVENT_TIME_KEY]
            ?.let {
                try {
                    // Strictly speaking, Google's documentation
                    // (https://cloud.google.com/storage/docs/pubsub-notifications#attributes)
                    // guarantees that eventTime is in RFC 3339 format, which is not strictly
                    // compatible with ISO 8601. However, it appears that eventTime is formatted
                    // within the subset of ISO 8601 which is compatible with RFC 3339, so it is
                    // probably safe to use an ISO 8601 parser.
                    OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: DateTimeParseException) {
                    LOG.error("event time '$it' was not in in correct format: ${e.message}")
                    consumer.nack()
                    return
                }
            }
            ?: run {
                LOG.error("received message without $EVENT_TIME_KEY key")
                consumer.nack()
                return
            }

        val apkSet = TempFile(Path(config.fileProcessingDirectory()))
            .use { tempFile ->
                try {
                    storage.get(BlobId.of(bucketId, objectId)).downloadTo(tempFile.path)
                } catch (e: StorageException) {
                    LOG.error("error downloading object $objectId from bucket $bucketId: ${e.message}")
                    consumer.nack()
                    return
                }

                ApkSet.parse(tempFile.path, Path(config.fileProcessingDirectory()))
            }
            .getOrElse {
                QuarkusTransaction
                    .joiningExisting()
                    .call {
                        AppDraftUploadProcessingJob
                            .findByBucketIdAndObjectId(bucketId, objectId)
                            ?.backgroundOperation
                            ?.let { operation ->
                                operation.result = GoogleStatus
                                    .newBuilder()
                                    .setCode(Status.Code.INTERNAL.value())
                                    .setMessage("unknown internal error occurred parsing APK set")
                                    .build()
                                    .toByteArray()
                                operation.succeeded = false
                            }
                    }

                if (it is ApkSetParseError.IoError) {
                    // I/O errors are a result of problematic server state, not the APK set, so we
                    // shouldn't fail processing for them
                    consumer.nack()
                } else {
                    consumer.ack()
                }
                return
            }

        val oldBlobId = BlobId.of(bucketId, objectId)
        val newBlobId = BlobId.of(config.appPackageBucket(), UUID.randomUUID().toString())

        // Mark the new object as an orphan temporarily so that if it is persisted in GCS but never
        // attached to an app draft (e.g., because the app draft is deleted after object persistence
        // but before the package is persisted to it), it is still tracked in our database so it can
        // be deleted later.
        QuarkusTransaction
            .joiningExisting()
            .call {
                OrphanedBlob(
                    bucketId = newBlobId.bucket,
                    objectId = newBlobId.name,
                    // Orphan the object in the future so that it isn't culled before we've
                    // finished processing it in this handler
                    orphanedOn = OffsetDateTime
                        .now(ZoneOffset.UTC)
                        .plusDays(OBJECT_ORPHAN_TIMEOUT_DAYS),
                )
                    .persist()
            }

        val copyRequest = Storage
            .CopyRequest
            .newBuilder()
            .setSource(oldBlobId)
            .setTarget(newBlobId)
            .build()
        val newBlob = try {
            // We choose here to not delete the original object so that this message receiver can be
            // idempotent, i.e., so that it can successfully run again if Pub/Sub delivers the same
            // notification again.
            //
            // To prevent object accumulation in the upload bucket, we instead recommend using
            // Object Lifecycle Management.
            storage.copy(copyRequest).result
        } catch (e: StorageException) {
            LOG.error("failed to copy app to long-term storage: ${e.message}")
            consumer.nack()
            return
        }

        QuarkusTransaction
            .joiningExisting()
            .call {
                val appDraft = AppDraft
                    .findByProcessingJobBucketIdAndObjectId(bucketId, objectId)
                    ?: run {
                        LOG.warn(
                            "app draft upload job for object $objectId in bucket $bucketId " +
                                    "not found, skipping"
                        )
                        return@call
                    }
                val job = AppDraftUploadProcessingJob
                    .findByBucketIdAndObjectId(bucketId, objectId)
                    ?: return@call

                val uploadNotNew = appDraft
                    .appPackage
                    ?.uploadPubSubEventTime
                    ?.let { !eventTime.isAfter(it) } == true
                if (uploadNotNew) {
                    LOG.warn("upload did not occur after last recorded upload, skipping")
                    return@call
                }

                appDraft
                    .appPackage
                    ?.let { existingPackage ->
                        // Orphan the package's old blob since it is no longer needed
                        OrphanedBlob(
                            bucketId = existingPackage.bucketId,
                            objectId = existingPackage.objectId,
                            orphanedOn = OffsetDateTime.now(),
                        )
                            .persist()

                        existingPackage.bucketId = newBlob.bucket
                        existingPackage.objectId = newBlob.name
                        existingPackage.uploadPubSubEventTime = eventTime
                        existingPackage.appId = apkSet.applicationId
                    }
                    ?: run {
                        val appPackage = AppPackage(
                            id = UUID.randomUUID(),
                            bucketId = newBlob.bucket,
                            objectId = newBlob.name,
                            uploadPubSubEventTime = eventTime,
                            appId = apkSet.applicationId,
                            versionCode = apkSet.versionCode,
                            versionName = apkSet.versionName,
                            targetSdk = apkSet.targetSdk,
                            signingCertificate = apkSet.signingCert.encoded,
                            buildApksResult = apkSet.buildApksResult.toByteArray(),
                        )
                            .also { it.persist() }
                        for (permission in apkSet.permissions) {
                            AppPackagePermission(
                                appPackageId = appPackage.id,
                                name = permission.name,
                                maxSdkVersion = permission.maxSdkVersion,
                            )
                                .persist()
                        }
                        appDraft.appPackageId = appPackage.id
                    }

                OrphanedBlob.deleteByBucketIdAndObjectId(newBlob.bucket, newBlob.name)

                AppDraftUploadProcessingJob.findByBucketIdAndObjectId(bucketId, objectId)
                job.backgroundOperation.result = UploadAppDraftResult
                    .getDefaultInstance()
                    .toByteArray()
                job.backgroundOperation.succeeded = true
            }

        consumer.ack()
    }
}
