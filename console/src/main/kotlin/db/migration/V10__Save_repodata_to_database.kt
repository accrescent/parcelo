// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package db.migration

import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.App
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.inject
import java.io.FileNotFoundException

/**
 * A versioned migration which saves published repository to the database.
 */
class V10__Save_repodata_to_database : BaseJavaMigration() {
    private val config: Config by inject(Config::class.java)

    /**
     * Downloads all published repository metadata, saving it to the database alongside its
     * associated app.
     */
    override fun migrate(context: Context) {
        val oldRepoDataReqs = transaction { App.all().map { it.id.value } }
            .map { appId ->
                val req = GetObjectRequest {
                    bucket = config.s3.bucket
                    key = "apps/$appId/repodata.json"
                }
                Pair(appId, req)
            }
        S3Client {
            endpointUrl = Url.parse(config.s3.endpointUrl)
            region = config.s3.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = config.s3.accessKeyId
                secretAccessKey = config.s3.secretAccessKey
            }
        }.use { s3Client ->
            oldRepoDataReqs.forEach { (appId, req) ->
                runBlocking {
                    s3Client.getObject(req) { resp ->
                        val data = resp.body
                            ?.toInputStream()
                            ?.use { it.readBytes() }
                            ?: throw FileNotFoundException()

                        transaction {
                            App.findById(appId)?.repositoryMetadata = ExposedBlob(data)
                        }
                    }
                }
            }
        }
    }
}
