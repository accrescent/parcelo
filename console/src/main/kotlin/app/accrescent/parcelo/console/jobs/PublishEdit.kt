// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.App
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
import app.accrescent.parcelo.console.data.Edit as EditDao

/**
 * Publishes the app metadata changes in the edit with the given ID
 */
fun publishEdit(editId: UUID) {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val edit = transaction { EditDao.findById(editId) } ?: return

    val app = transaction { App.findById(edit.appId) } ?: throw IllegalStateException()
    val appListing = transaction {
        Listing.find { Listings.appId eq edit.appId and (Listings.locale eq "en-US") }.single()
    }
    val iconFileId =
        transaction { Icon.findById(appListing.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository
    runBlocking {
        storageService.loadObject(app.fileId) { apkSet ->
            storageService.loadObject(iconFileId) { icon ->
                publishService.publishEdit(
                    appId = edit.appId.value,
                    editId = editId,
                    currentApkSet = apkSet,
                    currentIcon = icon,
                    shortDescription = edit.shortDescription,
                )
            }
        }
    }
}
