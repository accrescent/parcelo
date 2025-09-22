// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only


package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.AccessControlLists
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Apps
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.data.Update
import app.accrescent.parcelo.console.publish.PublishService
import app.accrescent.parcelo.console.storage.ObjectStorageService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject

/**
 * Migrates apps one at a time to publish to the directory service.
 */
fun migrateAppsToDirectoryService() {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    while (true) {
        val noMoreApps = transaction {
            val app = App
                .find { Apps.migratedToDirectoryService eq false and (Apps.updating eq false) }
                .forUpdate()
                .firstOrNull()
                ?: return@transaction true
            val defaultListing = Listing
                .find { Listings.appId eq app.id and (Listings.locale eq "en-US") }
                .single()
            val iconFileId = Icon
                .findById(defaultListing.iconId)
                ?.fileId
                ?: throw IllegalStateException("${app.id}'s default listing icon id ${defaultListing.iconId} is invalid")

            // To migrate existing apps to the directory service, we create a dummy update which we
            // publish to invoke the migration code
            val dummyUpdate = Update.new {
                appId = app.id
                versionCode = app.versionCode
                versionName = app.versionName
                creatorId = AccessControlList.find { AccessControlLists.appId eq app.id }.single().userId
                fileId = app.fileId
                reviewIssueGroupId = app.reviewIssueGroupId
            }

            runBlocking {
                storageService.loadObject(app.fileId) { apkSet ->
                    storageService.loadObject(iconFileId) { icon ->
                        publishService.publishUpdate(
                            apkSet = apkSet,
                            updateId = dummyUpdate.id.value,
                            currentIcon = icon,
                            currentAppName = defaultListing.label,
                            currentShortDescription = defaultListing.shortDescription,
                        )
                    }
                }
            }

            app.updating = true

            false
        }

        if (noMoreApps) {
            break
        }
    }
}
