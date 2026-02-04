// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.appstore.publish.v1alpha1.UploadAppDraftListingIconResult
import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.Image
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.util.TempFile
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.SubscriberInterface
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.pubsub.v1.PubsubMessage
import io.grpc.Status
import io.quarkus.logging.Log
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import com.google.rpc.Status as GoogleStatus

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

    @Transactional
    fun processMessage(message: PubsubMessage, consumer: AckReplyConsumer) {
        val eventType = message.attributesMap[EVENT_TYPE_KEY] ?: run {
            Log.error("received message without $EVENT_TYPE_KEY key, skipping")
            consumer.ack()
            return
        }
        if (eventType != EVENT_TYPE_OBJECT_FINALIZE) {
            Log.error("expected event type of $EVENT_TYPE_OBJECT_FINALIZE but got $eventType, skipping")
            consumer.ack()
            return
        }
        val bucketId = message.attributesMap[BUCKET_ID_KEY] ?: run {
            Log.error("received message without $BUCKET_ID_KEY key, skipping")
            consumer.ack()
            return
        }
        val objectId = message.attributesMap[OBJECT_ID_KEY] ?: run {
            Log.error("received message without $OBJECT_ID_KEY key, skipping")
            consumer.ack()
            return
        }

        val job = AppDraftListingIconUploadJob.findByBucketIdAndObjectId(bucketId, objectId) ?: run {
            Log.warn("no job found for bucket $bucketId and object $objectId, skipping")
            consumer.ack()
            return
        }
        if (job.backgroundOperation.result != null) {
            Log.warn("job for bucket $bucketId and object $objectId already completed, skipping")
            consumer.ack()
            return
        }

        val pngReader = ImageIO
            .getImageReadersByFormatName(PNG_FORMAT_NAME)
            .asSequence()
            .firstOrNull()
            ?: run {
                Log.error("failed to initialize PNG reader")
                consumer.nack()
                return
            }

        val image = TempFile(Path(config.fileProcessingDirectory())).use { tempIcon ->
            try {
                val blob: Blob? = storage.get(BlobId.of(bucketId, objectId))
                if (blob == null) {
                    Log.warn("blob at bucket $bucketId and object $objectId not found, skipping")
                    consumer.ack()
                    return
                } else {
                    blob.downloadTo(tempIcon.path)
                }
            } catch (e: StorageException) {
                Log.error("error downloading object $objectId from bucket $bucketId", e)
                consumer.nack()
                return
            }

            try {
                tempIcon.path.inputStream().use { iconInputStream ->
                    ImageIO.createImageInputStream(iconInputStream).use { imageInputStream ->
                        pngReader.input = imageInputStream
                        pngReader.read(0)
                    }
                }
            } catch (e: IIOException) {
                Log.warn("failed to parse file as image", e)
                job.backgroundOperation.result = GoogleStatus
                    .newBuilder()
                    .setCode(Status.Code.INVALID_ARGUMENT.value())
                    .setMessage("file is not a valid PNG")
                    .build()
                    .toByteArray()
                job.backgroundOperation.succeeded = false
                consumer.ack()
                return
            }
        }

        if (image.width != REQUIRED_IMAGE_WIDTH || image.height != REQUIRED_IMAGE_HEIGHT) {
            job.backgroundOperation.result = GoogleStatus
                .newBuilder()
                .setCode(Status.Code.INVALID_ARGUMENT.value())
                .setMessage("uploaded PNG is not ${REQUIRED_IMAGE_WIDTH}x$REQUIRED_IMAGE_HEIGHT pixels")
                .build()
                .toByteArray()
            job.backgroundOperation.succeeded = false
            consumer.ack()
            return
        }

        // Save the image as the new app icon
        val copyRequest = Storage.CopyRequest.of(
            BlobId.of(bucketId, objectId),
            BlobId.of(config.listingImageBucket(), UUID.randomUUID().toString()),
        )
        try {
            storage.copy(copyRequest).result
        } catch (e: StorageException) {
            Log.error("storage error copying image to GCS", e)
            consumer.nack()
            return
        }

        val existingIcon = job.appDraftListing.icon
        if (existingIcon == null) {
            val icon = Image(
                id = UUID.randomUUID(),
                bucketId = bucketId,
                objectId = objectId,
            )
                .also { it.persist() }
            job.appDraftListing.iconImageId = icon.id
        } else {
            // Orphan the old icon's blob since it is no longer needed
            OrphanedBlob(
                bucketId = existingIcon.bucketId,
                objectId = existingIcon.objectId,
                orphanedOn = OffsetDateTime.now(),
            )
                .persist()
            existingIcon.bucketId = bucketId
            existingIcon.objectId = objectId
        }

        job.backgroundOperation.result = UploadAppDraftListingIconResult
            .getDefaultInstance()
            .toByteArray()
        job.backgroundOperation.succeeded = true

        consumer.ack()
    }
}
