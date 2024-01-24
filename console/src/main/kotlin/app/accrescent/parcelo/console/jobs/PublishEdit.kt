// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Edit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent
import java.util.UUID

fun publishEdit(editId: UUID) {
    val config: Config by KoinJavaComponent.inject(Config::class.java)
    val httpClient: HttpClient by KoinJavaComponent.inject(HttpClient::class.java)

    val edit = transaction { Edit.findById(editId) } ?: return

    // Publish to the repository server
    val publishUrl = URLBuilder(config.repository.url)
        .appendPathSegments("api", "v1", "apps", edit.appId.toString(), "metadata")
        .buildString()
    runBlocking {
        httpClient.submitFormWithBinaryData(publishUrl, formData {
            if (edit.shortDescription != null) {
                append("short_description", edit.shortDescription!!)
            }
        }) {
            method = HttpMethod.Patch
            header("Authorization", "token ${config.repository.apiKey}")
            expectSuccess = true
        }
    }

    // Account for publication
    transaction {
        App.findById(edit.appId)?.run {
            if (edit.shortDescription != null) {
                shortDescription = edit.shortDescription!!
            }

            updating = false
        }

        edit.published = true
    }
}
