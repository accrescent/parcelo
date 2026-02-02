// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.jobs

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftListing
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.Image
import app.accrescent.server.parcelo.data.PublishedApk
import app.accrescent.server.parcelo.data.PublishedImage
import app.accrescent.server.parcelo.publish.PublishService
import app.accrescent.server.parcelo.publish.PublishedIcon
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
import org.quartz.PersistJobDataAfterExecution
import java.time.OffsetDateTime
import kotlin.io.path.Path

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
class PublishAppDraftJob @Inject constructor(
    private val config: ParceloConfig,
    private val publishService: PublishService,
    private val storage: Storage,
) : Job {
    @Transactional
    override fun execute(context: JobExecutionContext) {
        val appDraftId = try {
            context.mergedJobDataMap.getString(JobDataKey.APP_DRAFT_ID) ?: run {
                Log.error("app draft ID not found in merged job data map")
                context.jobDetail.jobDataMap[JobDataKey.SUCCESS] = false.toString()
                return
            }
        } catch (_: ClassCastException) {
            Log.error("app draft ID not found in merged job data map")
            context.jobDetail.jobDataMap[JobDataKey.SUCCESS] = false.toString()
            return
        }

        try {
            publishAppDraft(appDraftId)
            context.jobDetail.jobDataMap[JobDataKey.SUCCESS] = true.toString()
        } catch (t: Throwable) {
            AppDraft.findById(appDraftId)?.publishing = false
            Log.warn("app draft publishing failed", t)
            context.jobDetail.jobDataMap[JobDataKey.SUCCESS] = false.toString()
        }
    }

    private fun publishAppDraft(appDraftId: String) {
        // Fetch the app draft from the database
        val appDraft = AppDraft
            .findById(appDraftId)
            ?: throw Exception("app draft \"$appDraftId\" not found")
        val appPackage = appDraft
            .appPackage
            ?: throw Exception("no app package associated with app draft \"$appDraftId\"")
        val defaultListingLanguage = appDraft
            .defaultListingLanguage
            ?: throw Exception("default listing language not set for app draft \"$appDraftId\"")

        // Parse the app package metadata
        val buildApksResult = Commands.BuildApksResult.parseFrom(appPackage.buildApksResult)

        // Publish the APK set's APKs to an S3-compatible server.
        //
        // It is possible for this process to create orphan objects, i.e., objects that are stored
        // remotely but untracked by our database. However, this creation occurs only if this RPC
        // does not eventually complete successfully. That is, if this RPC is called and completes
        // successfully for a given draft, it is guaranteed that no orphan objects exist for it even
        // if previous calls have failed.
        val pathsToApks = TempFile(Path(config.packageProcessingDirectory())).use { tempApkSet ->
            storage
                .get(BlobId.of(appPackage.bucketId, appPackage.objectId))
                .downloadTo(tempApkSet.path)

            publishService.publishApks(
                appId = buildApksResult.packageName,
                versionCode = appPackage.versionCode,
                apkSetPath = tempApkSet.path,
                apkPaths = buildApksResult.apkPaths(),
            )
        }

        // Publish the app's listing icons to an S3-compatible server.
        //
        // This process has the same orphan object guarantees as publishing an APK set's APKs.
        val publishedIcons = mutableListOf<Triple<AppDraftListing, Image, PublishedIcon>>()
        for (listing in appDraft.listings) {
            val icon = listing.icon ?: throw Exception("no icon found for listing ${listing.language}")

            TempFile(Path(config.packageProcessingDirectory())).use { tempIcon ->
                storage.get(BlobId.of(icon.bucketId, icon.objectId)).downloadTo(tempIcon.path)

                val publishedIcon = publishService.publishIcon(
                    appId = buildApksResult.packageName,
                    listingLanguage = listing.language,
                    iconPath = tempIcon.path,
                )
                publishedIcons.add(Triple(listing, icon, publishedIcon))
            }
        }

        // Publish draft
        App(
            id = appPackage.appId,
            defaultListingLanguage = defaultListingLanguage,
            organizationId = appDraft.organizationId,
            entityTag = 0,
            appPackageId = appPackage.id,
        )
            .persist()
        for ((listing, icon, publishedIcon) in publishedIcons) {
            PublishedImage(
                imageId = icon.id,
                bucketId = publishedIcon.bucketId,
                objectId = publishedIcon.objectId,
            )
                .persist()
            AppListing(
                appId = appPackage.appId,
                language = listing.language,
                name = listing.name,
                shortDescription = listing.shortDescription,
                iconImageId = icon.id,
            )
                .persist()
        }
        for ((apkPath, apk) in pathsToApks) {
            PublishedApk(
                appPackageId = appPackage.id,
                apkPath = apkPath,
                bucketId = apk.bucketId,
                objectId = apk.objectId,
                size = apk.size,
            )
                .persist()
        }
        appDraft.publishing = false
        appDraft.publishedAt = OffsetDateTime.now()
    }
}
