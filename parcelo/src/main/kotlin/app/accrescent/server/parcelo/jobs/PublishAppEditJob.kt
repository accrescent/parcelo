// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.jobs

import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.PublishAppEditResult
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.ListingId
import app.accrescent.server.parcelo.data.PublishedImage
import app.accrescent.server.parcelo.publish.PublishService
import app.accrescent.server.parcelo.util.TempFile
import app.accrescent.server.parcelo.util.apkPaths
import com.android.bundle.Commands
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.quarkus.logging.Log
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.OffsetDateTime
import kotlin.io.path.Path
import app.accrescent.server.parcelo.data.PublishedApk as DbPublishedApk

@DisallowConcurrentExecution
class PublishAppEditJob @Inject constructor(
    private val config: ParceloConfig,
    private val publishService: PublishService,
    private val storage: Storage,
) : Job {
    @Transactional
    override fun execute(context: JobExecutionContext) {
        val jobId = context.jobDetail.key.name
        val operation = BackgroundOperation.findById(jobId) ?: run {
            Log.error("background operation with job ID $jobId not found, skipping")
            return
        }
        val appEditId = try {
            context.mergedJobDataMap.getString(JobDataKey.APP_EDIT_ID) ?: run {
                Log.error("app edit ID not found in merged job data map")
                operation.result = ConsoleApiError(
                    ErrorReason.ERROR_REASON_INTERNAL,
                    "app edit ID parameter not found in job context",
                )
                    .toStatus()
                    .toByteArray()
                operation.succeeded = false
                return
            }
        } catch (_: ClassCastException) {
            Log.error("app edit ID not found in merged job data map")
            operation.result = ConsoleApiError(
                ErrorReason.ERROR_REASON_INTERNAL,
                "app edit ID parameter was not the right type",
            )
                .toStatus()
                .toByteArray()
            operation.succeeded = false
            return
        }

        try {
            publishAppEdit(appEditId)
            operation.result = PublishAppEditResult.getDefaultInstance().toByteArray()
            operation.succeeded = true
        } catch (t: Throwable) {
            AppEdit.findById(appEditId)?.publishing = false
            Log.warn("app edit publishing failed", t)
            operation.result = ConsoleApiError(
                ErrorReason.ERROR_REASON_INTERNAL,
                "an unknown internal error occurred",
            )
                .toStatus()
                .toByteArray()
            operation.succeeded = false
        }
    }

    private fun publishAppEdit(appEditId: String) {
        val appEdit = AppEdit
            .findById(appEditId)
            ?: throw Exception("app edit \"$appEditId\" not found")

        // If this edit contains a package change, publish it
        if (appEdit.appPackageId != appEdit.app.appPackageId) {
            // Parse the app package metadata
            val buildApksResult = Commands
                .BuildApksResult
                .parseFrom(appEdit.appPackage.buildApksResult)

            // Publish the APK set's APKs to an S3-compatible server.
            //
            // It is possible for this process to create orphan objects, i.e., objects that are
            // stored remotely but untracked by our database. However, this creation occurs only if
            // this job does not eventually complete successfully. That is, if this job is called
            // and completes successfully for a given edit, it is guaranteed that no orphan objects
            // exist for it even if previous calls have failed.
            TempFile(Path(config.fileProcessingDirectory()))
                .use { tempApkSet ->
                    val blob = storage
                        .get(BlobId.of(appEdit.appPackage.bucketId, appEdit.appPackage.objectId))
                        ?: run {
                            Log.error("blob for app package ${appEdit.appPackage.id} not found")
                            throw Exception("blob for app package ${appEdit.appPackage.id} not found")
                        }
                    blob.downloadTo(tempApkSet.path)

                    publishService.publishApks(
                        appId = appEdit.appPackage.appId,
                        versionCode = appEdit.appPackage.versionCode,
                        apkSetPath = tempApkSet.path,
                        apkPaths = buildApksResult.apkPaths(),
                    )
                }
                .forEach { (apkPath, apk) ->
                    DbPublishedApk(
                        appPackageId = appEdit.appPackage.id,
                        apkPath = apkPath,
                        bucketId = apk.bucketId,
                        objectId = apk.objectId,
                        size = apk.size,
                    )
                        .persist()
                }

            appEdit.app.appPackageId = appEdit.appPackageId
        }

        // Publish the app's listing icons to an S3-compatible server.
        //
        // This process has the same orphan object guarantees as publishing an APK set's APKs.
        val appListings = appEdit.app.listings.associateBy { it.id.language }
        for (editListing in appEdit.listings) {
            val editListingIcon = editListing
                .icon
                ?: throw Exception(
                    "no icon found for listing ${editListing.language} of edit ${editListing.appEditId}"
                )
            val appListing = appListings[editListing.language]
            if (appListing == null) {
                // Create a new app listing based on the edit listing, publishing the latter's icon
                val publishedIcon = TempFile(Path(config.fileProcessingDirectory()))
                    .use { tempIcon ->
                        val blob = storage
                            .get(BlobId.of(editListingIcon.bucketId, editListingIcon.objectId))
                            ?: run {
                                Log.error("blob for icon ${editListingIcon.id} not found")
                                throw Exception("blob for icon ${editListingIcon.id} not fogund")
                            }
                        blob.downloadTo(tempIcon.path)

                        publishService.publishIcon(
                            appId = appEdit.appPackage.appId,
                            listingLanguage = editListing.language,
                            iconPath = tempIcon.path,
                        )
                    }
                PublishedImage(
                    imageId = editListingIcon.id,
                    bucketId = publishedIcon.bucketId,
                    objectId = publishedIcon.objectId,
                )
                    .persist()
                AppListing(
                    id = ListingId(appEdit.appPackage.appId, editListing.language),
                    name = editListing.name,
                    shortDescription = editListing.shortDescription,
                    iconImageId = editListingIcon.id,
                )
                    .persist()
            } else {
                // Update the app listing based on the edit listing's contents, publishing a new
                // icon if necessary
                if (editListing.iconImageId != appListing.iconImageId) {
                    val publishedIcon = TempFile(Path(config.fileProcessingDirectory()))
                        .use { tempIcon ->
                            val blob = storage
                                .get(BlobId.of(editListingIcon.bucketId, editListingIcon.objectId))
                                ?: run {
                                    Log.error("blob for icon ${editListingIcon.id} not found")
                                    throw Exception("blob for icon ${editListingIcon.id} not found")
                                }
                            blob.downloadTo(tempIcon.path)

                            publishService.publishIcon(
                                appId = appEdit.appPackage.appId,
                                listingLanguage = editListing.language,
                                iconPath = tempIcon.path,
                            )
                        }

                    PublishedImage(
                        imageId = editListingIcon.id,
                        bucketId = publishedIcon.bucketId,
                        objectId = publishedIcon.objectId,
                    )
                        .persist()
                    appListing.iconImageId = editListingIcon.id
                }

                appListing.name = editListing.name
                appListing.shortDescription = editListing.shortDescription
            }
        }

        appEdit.app.entityTag += 1
        appEdit.publishing = false
        appEdit.publishedAt = OffsetDateTime.now()
    }
}
