// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.UploadAppDraftListingIconResult
import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.Image
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.util.TempFile
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.inputStream

private const val PNG_FORMAT_NAME = "PNG"
private const val REQUIRED_IMAGE_WIDTH = 512
private const val REQUIRED_IMAGE_HEIGHT = 512

@ApplicationScoped
class AppDraftListingIconUploadedProcessor(
    private val config: ParceloConfig,
    private val pubSubHelper: PubSubHelper,
    private val storage: Storage,
) {
    private val messageReceiver =
        MessageReceiver { message, consumer -> processMessage(message, consumer) }
    private lateinit var subscriber: SubscriberInterface

    fun onStart(@Observes startupEvent: StartupEvent) {
        val config = config.objectStorageNotifications().appDraftListingIconUploads()
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
            val job = AppDraftListingIconUploadJob.findByBucketIdAndObjectId(bucketId, objectId) ?: run {
                Log.warn("no job found for bucket $bucketId and object $objectId, skipping")
                return@call
            }
            if (job.backgroundOperation.result != null) {
                Log.warn("job for bucket $bucketId and object $objectId already completed, skipping")
                return@call
            }
            val uploadEventIsNew = job
                .appDraftListing
                .icon
                ?.uploadPubSubEventTime
                ?.let { eventTime.isAfter(it) } != false
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

    private fun processEvent(event: ObjectUploadEvent, job: AppDraftListingIconUploadJob) {
        if (job.appDraftListing.appDraft.submitted) {
            job.backgroundOperation.result = ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted app drafts cannot be modified",
            )
                .toStatus()
                .toByteArray()
            return
        }

        // Parse an image from the uploaded file
        val pngReader = ImageIO
            .getImageReadersByFormatName(PNG_FORMAT_NAME)
            .asSequence()
            .firstOrNull()
            ?: run {
                Log.error("failed to initialize PNG reader")
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_INTERNAL,
                    "failed to initialize PNG reader",
                )
                    .toStatus()
                    .toByteArray()
                return
            }
        val image = TempFile(Path(config.fileProcessingDirectory())).use { tempIcon ->
            val blob = storage.get(BlobId.of(event.bucketId, event.objectId)) ?: run {
                Log.warn(
                    "blob at bucket ${event.bucketId} and object ${event.objectId} not found, " +
                            "skipping"
                )
                return
            }
            blob.downloadTo(tempIcon.path)

            try {
                tempIcon.path.inputStream().use { iconInputStream ->
                    ImageIO.createImageInputStream(iconInputStream).use { imageInputStream ->
                        pngReader.input = imageInputStream
                        pngReader.read(0)
                    }
                }
            } catch (e: IIOException) {
                Log.warn("failed to parse file as image", e)
                job.backgroundOperation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_INVALID_IMAGE,
                    "file is not a valid PNG",
                )
                    .toStatus()
                    .toByteArray()
                return
            }
        }

        // Validate the image
        if (image.width != REQUIRED_IMAGE_WIDTH || image.height != REQUIRED_IMAGE_HEIGHT) {
            job.backgroundOperation.result = ConsoleApiError(
                ErrorReason.ERROR_REASON_INCORRECT_IMAGE_DIMENSIONS,
                "uploaded PNG is not ${REQUIRED_IMAGE_WIDTH}x$REQUIRED_IMAGE_HEIGHT pixels",
            )
                .toStatus()
                .toByteArray()
            return
        }

        // Copy the image into a separate bucket.
        //
        // We choose here to not delete the original object so that this message receiver can be
        // idempotent, i.e., so that it can successfully run again if Pub/Sub delivers the same
        // notification again.
        //
        // To prevent object accumulation in the upload bucket, we instead recommend using
        // Object Lifecycle Management.
        val copyRequest = Storage.CopyRequest.of(
            BlobId.of(event.bucketId, event.objectId),
            BlobId.of(config.buckets().listingImage(), UUID.randomUUID().toString()),
        )
        val newBlob = storage.copy(copyRequest).result

        // Create and persist the new icon
        val newIcon = Image(
            id = UUID.randomUUID(),
            bucketId = newBlob.bucket,
            objectId = newBlob.name,
            uploadPubSubEventTime = event.timestamp,
        )
            .also { it.persist() }
        val existingIcon = job.appDraftListing.icon
        if (existingIcon == null) {
            job.appDraftListing.iconImageId = newIcon.id
        } else {
            // Orphan the old icon's blob since it is no longer needed
            OrphanedBlob(
                bucketId = existingIcon.bucketId,
                objectId = existingIcon.objectId,
                orphanedOn = OffsetDateTime.now(),
            )
                .persist()
            job.appDraftListing.iconImageId = newIcon.id
            existingIcon.delete()
        }

        job.backgroundOperation.result = UploadAppDraftListingIconResult
            .getDefaultInstance()
            .toByteArray()
        job.backgroundOperation.succeeded = true
    }
}
