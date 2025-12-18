// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.Image
import app.accrescent.server.parcelo.data.OrphanedBlob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.inject.Inject
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import java.nio.file.Path as FsPath

// 1 MiB
private const val MAX_ICON_SIZE = 1 * 1024 * 1024
private const val PNG_FORMAT_NAME = "PNG"
private const val REQUIRED_IMAGE_WIDTH = 512
private const val REQUIRED_IMAGE_HEIGHT = 512

private const val OBJECT_ORPHAN_TIMEOUT_DAYS = 1L

@Path("/api/images")
class ImageUploadService @Inject constructor(
    private val config: ParceloConfig,
    private val storage: Storage,
) {
    companion object {
        private val LOG = Logger.getLogger(ImageUploadService::class.java)

        fun createUploadUrl(baseUrl: String, uploadKey: UUID): String {
            return "$baseUrl/api/images/app_draft_listing_icon/$uploadKey"
        }
    }

    @Path("app_draft_listing_icon/{uploadKey}")
    @PUT
    fun updateAppDraftListingIcon(uploadKey: String, imageFile: FsPath): Response {
        val key = try {
            UUID.fromString(uploadKey)
        } catch (_: IllegalArgumentException) {
            return Response.status(Response.Status.NOT_FOUND).build()
        }

        val job = QuarkusTransaction
            .joiningExisting()
            .call { AppDraftListingIconUploadJob.findByUploadKey(key) }
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (!job.expiresAt.isAfter(OffsetDateTime.now())) {
            return Response.status(Response.Status.FORBIDDEN).build()
        }

        // Verify icon file size
        if (imageFile.fileSize() > MAX_ICON_SIZE) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build()
        }

        // Parse image, verifying it's a PNG
        val pngReader = ImageIO
            .getImageReadersByFormatName(PNG_FORMAT_NAME)
            .asSequence()
            .firstOrNull()
            ?: return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        val image = try {
            imageFile.inputStream().use { iconInputStream ->
                ImageIO.createImageInputStream(iconInputStream).use { imageInputStream ->
                    pngReader.input = imageInputStream
                    pngReader.read(0)
                }
            }
        } catch (_: IIOException) {
            QuarkusTransaction
                .joiningExisting()
                .call { AppDraftListingIconUploadJob.markFailed(key) }
            return Response.ok().build()
        }

        // Verify icon dimensions
        if (image.width != REQUIRED_IMAGE_WIDTH || image.height != REQUIRED_IMAGE_HEIGHT) {
            QuarkusTransaction
                .joiningExisting()
                .call { AppDraftListingIconUploadJob.markFailed(key) }
            return Response.ok().build()
        }

        val blobId = BlobId.of(config.listingImageBucket(), UUID.randomUUID().toString())

        // Mark the new object as an orphan temporarily so that if it is persisted in GCS but never
        // attached to an app draft listing (e.g., because the server crashed after uploading the
        // object but before commiting the tracking transaction), it is still tracked in our
        // database so it can be deleted later.
        QuarkusTransaction
            .joiningExisting()
            .call {
                OrphanedBlob(
                    bucketId = blobId.bucket,
                    objectId = blobId.name,
                    // Orphan the object in the future so that it isn't culled before we've finished
                    // processing it in this handler
                    orphanedOn = OffsetDateTime.now().plusDays(OBJECT_ORPHAN_TIMEOUT_DAYS)
                )
            }

        // Upload image file to private storage bucket
        try {
            storage.createFrom(BlobInfo.newBuilder(blobId).build(), imageFile)
        } catch (e: IOException) {
            LOG.error("I/O error copying file to GCS: ${e.message}")
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        } catch (e: StorageException) {
            LOG.error("storage error copying file to GCS: ${e.message}")
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }

        QuarkusTransaction.joiningExisting().call {
            val job = AppDraftListingIconUploadJob.findByUploadKey(key) ?: return@call

            job
                .appDraftListing
                .icon
                ?.let { existingIcon ->
                    // Orphan the old icon's blob since it is no longer needed
                    OrphanedBlob(
                        bucketId = existingIcon.bucketId,
                        objectId = existingIcon.objectId,
                        orphanedOn = OffsetDateTime.now(),
                    )
                        .persist()

                    existingIcon.bucketId = blobId.bucket
                    existingIcon.objectId = blobId.name
                }
                ?: run {
                    val icon = Image(
                        id = UUID.randomUUID(),
                        bucketId = blobId.bucket,
                        objectId = blobId.name,
                    )
                        .also { it.persist() }
                    job.appDraftListing.iconImageId = icon.id
                }

            OrphanedBlob.deleteByBucketIdAndObjectId(blobId.bucket, blobId.name)
            AppDraftListingIconUploadJob.markSucceeded(key)
        }

        return Response.ok().build()
    }
}
