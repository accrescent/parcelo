// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.Draft as DraftDao
import app.accrescent.parcelo.console.data.Update as UpdateDao
import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.publish.PublishService
import app.accrescent.parcelo.console.storage.FileStorageService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

/**
 * Publishes the draft with the given ID, making it available for download
 */
fun registerPublishAppJob(draftId: UUID) {
    val storageService: FileStorageService by inject(FileStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val draft = transaction { DraftDao.findById(draftId) } ?: return
    val iconFileId =
        transaction { Icon.findById(draft.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository
    val metadata = runBlocking {
        storageService.loadFile(draft.fileId) { draftStream ->
            storageService.loadFile(iconFileId) { iconStream ->
                publishService.publishDraft(draftStream, iconStream, draft.shortDescription)
            }
        }
    }

    // Account for publication
    transaction {
        draft.delete()
        val app = App.new(draft.appId) {
            versionCode = draft.versionCode
            versionName = draft.versionName
            fileId = draft.fileId
            reviewIssueGroupId = draft.reviewIssueGroupId
            repositoryMetadata = ExposedBlob(metadata)
        }
        Listing.new {
            appId = app.id
            locale = "en-US"
            iconId = draft.iconId
            label = draft.label
            shortDescription = draft.shortDescription
        }
        AccessControlList.new {
            this.userId = draft.creatorId
            appId = app.id
            update = true
            editMetadata = true
        }
    }
}

/**
 * Publishes the update with the given ID, making it available for download
 */
fun registerPublishUpdateJob(updateId: UUID) {
    val storageService: FileStorageService by inject(FileStorageService::class.java)
    val publishService: PublishService by inject(PublishService::class.java)

    val update = transaction { UpdateDao.findById(updateId) } ?: return

    // Publish to the repository
    val updatedMetadata = runBlocking {
        storageService.loadFile(update.fileId!!) {
            runBlocking { publishService.publishUpdate(it, update.appId.value) }
        }
    }

    // Account for publication
    val oldAppFileId = transaction {
        App.findById(update.appId)?.run {
            versionCode = update.versionCode
            versionName = update.versionName
            repositoryMetadata = ExposedBlob(updatedMetadata)

            val oldAppFileId = fileId
            fileId = update.fileId!!

            update.published = true
            updating = false

            oldAppFileId
        }
    }

    // Delete old app file
    if (oldAppFileId != null) {
        runBlocking { storageService.markDeleted(oldAppFileId.value) }
        BackgroundJob.enqueue { cleanFile(oldAppFileId.value) }
    }
}
