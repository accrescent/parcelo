package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.storage.FileStorageService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.headers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

fun registerPublishAppJob(draftId: UUID) {
    val config: Config by inject(Config::class.java)
    val httpClient: HttpClient by inject(HttpClient::class.java)
    val storageService: FileStorageService by inject(FileStorageService::class.java)

    val draft = transaction { Draft.findById(draftId) } ?: return
    val iconFileId =
        transaction { Icon.findById(draft.iconId)?.fileId } ?: throw IllegalStateException()

    // Publish to the repository server
    val publishUrl = URLBuilder(config.repositoryUrl)
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
        }) {
            header("Authorization", "token ${config.repositoryApiKey}")
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
            fileId = draft.fileId
            iconId = draft.iconId
            reviewIssueGroupId = draft.reviewIssueGroupId
        }
        AccessControlList.new {
            this.userId = draft.creatorId
            appId = app.id
            update = true
        }
    }
}
