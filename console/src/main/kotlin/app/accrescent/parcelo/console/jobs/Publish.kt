// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.Update
import app.accrescent.parcelo.console.storage.FileStorageService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.headers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

// Socket timeout for publishing the app, since processing occurs in the background before a
// response is returned. 1 minute.
private const val SOCKET_TIMEOUT: Long = 60_000

fun registerPublishAppJob(draftId: UUID) {
    val config: Config by inject(Config::class.java)
    val httpClient: HttpClient by inject(HttpClient::class.java)
    val storageService: FileStorageService by inject(FileStorageService::class.java)

    val draft = transaction { Draft.findById(draftId) } ?: return
    val iconFileId =
        transaction { Icon.findById(draft.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository server
    val publishUrl = URLBuilder(config.repository.url)
        .appendPathSegments("api", "v1", "apps")
        .buildString()
    runBlocking {
        httpClient.submitFormWithBinaryData(publishUrl, formData {
            storageService.loadFile(draft.fileId).use {
                append("apk_set", it.readBytes(), headers {
                    append(HttpHeaders.ContentDisposition, "filename=\"app.apks\"")
                })
            }
            storageService.loadFile(iconFileId).use {
                append("icon", it.readBytes(), headers {
                    append(HttpHeaders.ContentDisposition, "filename=\"icon.png\"")
                })
            }
            append("short_description", draft.shortDescription)
        }) {
            timeout { socketTimeoutMillis = SOCKET_TIMEOUT }
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }

    // Account for publication
    transaction {
        draft.delete()
        val app = App.new(draft.appId) {
            label = draft.label
            versionCode = draft.versionCode
            versionName = draft.versionName
            shortDescription = draft.shortDescription
            fileId = draft.fileId
            iconId = draft.iconId
            reviewIssueGroupId = draft.reviewIssueGroupId
        }
        AccessControlList.new {
            this.userId = draft.creatorId
            appId = app.id
            update = true
            editMetadata = true
        }
    }
}

fun registerPublishUpdateJob(updateId: UUID) {
    val config: Config by inject(Config::class.java)
    val httpClient: HttpClient by inject(HttpClient::class.java)
    val storageService: FileStorageService by inject(FileStorageService::class.java)

    val update = transaction { Update.findById(updateId) } ?: return

    // Publish to the repository server
    val publishUrl = URLBuilder(config.repository.url)
        .appendPathSegments("api", "v1", "apps", update.appId.toString())
        .buildString()
    runBlocking {
        httpClient.submitFormWithBinaryData(publishUrl, formData {
            storageService.loadFile(update.fileId).use {
                append("apk_set", it.readBytes(), headers {
                    append(HttpHeaders.ContentDisposition, "filename=\"app.apks\"")
                })
            }
        }) {
            timeout { socketTimeoutMillis = SOCKET_TIMEOUT }
            method = HttpMethod.Put
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }

    // Account for publication
    val oldAppFileId = transaction {
        App.findById(update.appId)?.run {
            versionCode = update.versionCode
            versionName = update.versionName

            val oldAppFileId = fileId
            fileId = update.fileId

            update.published = true
            updating = false

            oldAppFileId
        }
    }

    // Delete old app file
    if (oldAppFileId != null) {
        storageService.deleteFile(oldAppFileId)
    }
}
