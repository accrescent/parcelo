// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.publish.PublishService
import app.accrescent.parcelo.console.storage.ObjectStorageService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID
import app.accrescent.parcelo.console.data.Draft as DraftDao
import app.accrescent.parcelo.console.data.Update as UpdateDao

/**
 * Publishes the draft with the given ID, making it available for download
 */
fun registerPublishAppJob(draftId: UUID) {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val draft = transaction { DraftDao.findById(draftId) } ?: return
    val iconFileId =
        transaction { Icon.findById(draft.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository
    runBlocking {
        storageService.loadObject(draft.fileId) { draftStream ->
            storageService.loadObject(iconFileId) { iconStream ->
                publishService.publishDraft(
                    draftStream,
                    iconStream,
                    draft.label,
                    draft.shortDescription,
                )
            }
        }
    }
}

/**
 * Publishes the update with the given ID, making it available for download
 */
fun registerPublishUpdateJob(updateId: UUID) {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val update = transaction { UpdateDao.findById(updateId) } ?: return
    val appListing = transaction {
        Listing.find { Listings.appId eq update.appId and (Listings.locale eq "en-US") }.single()
    }
    val iconFileId = transaction { Icon.findById(appListing.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository
    runBlocking {
        storageService.loadObject(update.fileId!!) { updateStream ->
            storageService.loadObject(iconFileId) { iconStream ->
                runBlocking {
                    publishService.publishUpdate(
                        apkSet = updateStream,
                        updateId = update.id.value,
                        currentIcon = iconStream,
                        currentAppName = appListing.label,
                        currentShortDescription = appListing.shortDescription,
                    )
                }
            }
        }
    }
}
