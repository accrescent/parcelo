// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import app.accrescent.parcelo.console.Config
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
import java.io.InputStream

// Socket timeout for publishing the app, since processing occurs in the background before a
// response is returned. 1 minute.
private const val SOCKET_TIMEOUT: Long = 60_000

/**
 * A [PublishService] that publishes to a remote repository application server
 */
class RepositoryPublishService(
    private val config: Config,
    private val httpClient: HttpClient,
) : PublishService {
    override suspend fun publishDraft(
        apkSet: InputStream,
        icon: InputStream,
        shortDescription: String,
    ) {
        val publishUrl = URLBuilder(config.repository.url)
            .appendPathSegments("api", "v1", "apps")
            .buildString()

        httpClient.submitFormWithBinaryData(publishUrl, formData {
            append("apk_set", apkSet.readBytes(), headers {
                append(HttpHeaders.ContentDisposition, "filename=\"app.apks\"")
            })
            append("icon", icon.readBytes(), headers {
                append(HttpHeaders.ContentDisposition, "filename=\"icon.png\"")
            })
            append("short_description", shortDescription)
        }) {
            timeout { socketTimeoutMillis = SOCKET_TIMEOUT }
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }

    override suspend fun publishUpdate(apkSet: InputStream, appId: String) {
        val publishUrl = URLBuilder(config.repository.url)
            .appendPathSegments("api", "v1", "apps", appId)
            .buildString()

        httpClient.submitFormWithBinaryData(publishUrl, formData {
            append("apk_set", apkSet.readBytes(), headers {
                append(HttpHeaders.ContentDisposition, "filename=\"app.apks\"")
            })
        }) {
            timeout { socketTimeoutMillis = SOCKET_TIMEOUT }
            method = HttpMethod.Put
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }

    override suspend fun publishEdit(appId: String, shortDescription: String?) {
        val publishUrl = URLBuilder(config.repository.url)
            .appendPathSegments("api", "v1", "apps", appId, "metadata")
            .buildString()

        httpClient.submitFormWithBinaryData(publishUrl, formData {
            if (shortDescription != null) {
                append("short_description", shortDescription)
            }
        }) {
            method = HttpMethod.Patch
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }
}
