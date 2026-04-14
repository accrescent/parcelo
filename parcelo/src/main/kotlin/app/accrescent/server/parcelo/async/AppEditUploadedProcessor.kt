// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.console.v1.ErrorReason
import app.accrescent.console.v1.UploadAppEditResult
import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppEditUploadProcessingJob
import app.accrescent.server.parcelo.data.AppPackage
import app.accrescent.server.parcelo.data.AppPackagePermission
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.parsers.ApkSet
import app.accrescent.server.parcelo.util.TempFile
import arrow.core.getOrElse
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.SubscriberInterface
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.pubsub.v1.PubsubMessage
import io.quarkus.logging.Log
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.io.path.Path

@ApplicationScoped
class AppEditUploadedProcessor @Inject constructor(
    private val config: ParceloConfig,
    private val pubSubHelper: PubSubHelper,
    private val storage: Storage,
) {
    private val messageReceiver =
        MessageReceiver { message, consumer -> processMessage(message, consumer) }
    private lateinit var subscriber: SubscriberInterface

    fun onStart(@Observes startupEvent: StartupEvent) {
        val config = config.objectStorageNotifications().appEditUploads()
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
    //    in undesirable state, e.g., lost updates.
    //
    // [1]: https://docs.cloud.google.com/pubsub/docs/subscription-overview#default_properties
    private fun processMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        // Parse message details, skipping the message if it's invalid, i.e., if it isn't an object
        // storage notification of the expected type
        val eventType = message.attributesMap[EVENT_TYPE_KEY] ?: run {
            Log.warn("received message without $EVENT_TYPE_KEY key, skipping")
            consumer.ack()
            return
        }
        if (eventType != EVENT_TYPE_OBJECT_FINALIZE) {
            Log.warn("expected event type of $EVENT_TYPE_OBJECT_FINALIZE but got $eventType, skipping")
            consumer.ack()
            return
        }
        val bucketId = message.attributesMap[BUCKET_ID_KEY] ?: run {
            Log.warn("received message without $BUCKET_ID_KEY key, skipping")
            consumer.ack()
            return
        }
        val objectId = message.attributesMap[OBJECT_ID_KEY] ?: run {
            Log.warn("received message without $OBJECT_ID_KEY key, skipping")
            consumer.ack()
            return
        }
        val eventTime = message
            .attributesMap[EVENT_TIME_KEY]
            // Strictly speaking, Google's documentation
            // (https://cloud.google.com/storage/docs/pubsub-notifications#attributes) guarantees
            // that eventTime is in RFC 3339 format, which is not strictly compatible with ISO 8601.
            // However, it appears that eventTime is formatted within the subset of ISO 8601 which
            // is compatible with RFC 3339, so it is probably safe to use an ISO 8601 parser.
            ?.let { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            ?: run {
                Log.warn("received message without $EVENT_TIME_KEY key, skipping")
                consumer.ack()
                return
            }

        QuarkusTransaction.disallowingExisting().call {
            // Skip processing if the job is not found, processing has already completed, or the
            // upload event isn't new
            val job = AppEditUploadProcessingJob.findByBucketIdAndObjectId(bucketId, objectId) ?: run {
                Log.warn("no job found for bucket $bucketId and object $objectId, skipping")
                return@call
            }
            if (job.backgroundOperation.result != null) {
                Log.warn("job for bucket $bucketId and object $objectId already completed, skipping")
                return@call
            }
            val uploadEventIsNew = job
                .appEdit
                .appPackage
                .uploadPubSubEventTime
                .let { eventTime.isAfter(it) }
            if (!uploadEventIsNew) {
                Log.warn("upload did not occur after last recorded upload, skipping")
                return@call
            }

            try {
                processEvent(ObjectUploadEvent(bucketId, objectId, eventTime), job)
            } catch (t: Throwable) {
                Log.error("an error occurred processing job ${job.id}", t)
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_INTERNAL,
                    "an unknown internal error has occurred",
                )
                    .toStatus()
                    .toByteArray()
            }
        }

        consumer.ack()
    }

    private fun processEvent(event: ObjectUploadEvent, job: AppEditUploadProcessingJob) {
        if (job.appEdit.submitted) {
            job.backgroundOperation.result = ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted app edits cannot be modified",
            )
                .toStatus()
                .toByteArray()
            return
        }

        // Parse an APK set from the uploaded file
        val apkSet = TempFile(Path(config.fileProcessingDirectory()))
            .use { tempFile ->
                val blob = storage.get(BlobId.of(event.bucketId, event.objectId)) ?: run {
                    Log.warn(
                        "blob at bucket ${event.bucketId} and object ${event.objectId} not found," +
                                " skipping"
                    )
                    return
                }
                blob.downloadTo(tempFile.path)

                ApkSet.parse(tempFile.path, Path(config.fileProcessingDirectory()))
            }
            .getOrElse {
                job.backgroundOperation.result = it.toConsoleApiError().toStatus().toByteArray()
                return
            }

        // Verify the APK set is valid for this app
        when {
            apkSet.applicationId != job.appEdit.app.id -> {
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_APP_ID_MISMATCH,
                    "APK set app ID \"${apkSet.applicationId}\" does not match expected app ID " +
                            job.appEdit.app.id,
                )
                    .toStatus()
                    .toByteArray()
                return
            }

            apkSet.versionCode <= job.appEdit.app.appPackage.versionCode -> {
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_NOT_AN_UPGRADE,
                    "APK set version code ${apkSet.versionCode} is not more than app version code" +
                            " ${job.appEdit.appPackage.versionCode}"
                )
                    .toStatus()
                    .toByteArray()
                return
            }

            !apkSet
                .signingCert
                .encoded
                .contentEquals(job.appEdit.app.appPackage.signingCertificate) -> {
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_SIGNING_CERT_MISMATCH,
                    "APK set signing certificate does not match app signing certificate",
                )
                    .toStatus()
                    .toByteArray()
                return
            }
        }

        // Copy the APK set into a separate bucket.
        //
        // We choose here to not delete the original object so that this message receiver can be
        // idempotent, i.e., so that it can successfully run again if Pub/Sub delivers the same
        // notification again.
        //
        // To prevent object accumulation in the upload bucket, we instead recommend using
        // Object Lifecycle Management.
        val copyRequest = Storage.CopyRequest.of(
            BlobId.of(event.bucketId, event.objectId),
            BlobId.of(config.buckets().appPackage(), UUID.randomUUID().toString()),
        )
        val newBlob = storage.copy(copyRequest).result

        // Create and persist the new package
        val newPackage = AppPackage(
            id = UUID.randomUUID(),
            bucketId = newBlob.bucket,
            objectId = newBlob.name,
            uploadPubSubEventTime = event.timestamp,
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
                appPackageId = newPackage.id,
                name = permission.name,
                maxSdkVersion = permission.maxSdkVersion,
            )
                .persist()
        }
        val existingPackage = job.appEdit.appPackage
        if (existingPackage.id == job.appEdit.app.appPackageId) {
            job.appEdit.appPackageId = newPackage.id
        } else {
            // Orphan the package's old blob since it is no longer needed
            OrphanedBlob(
                bucketId = existingPackage.bucketId,
                objectId = existingPackage.objectId,
                orphanedOn = OffsetDateTime.now(),
            )
                .persist()
            job.appEdit.appPackageId = newPackage.id
            existingPackage.delete()
        }

        job.backgroundOperation.result = UploadAppEditResult.getDefaultInstance().toByteArray()
        job.backgroundOperation.succeeded = true
    }
}
