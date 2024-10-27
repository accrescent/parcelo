// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

import app.accrescent.parcelo.console.data.configureDatabase
import app.accrescent.parcelo.console.jobs.configureJobRunr
import app.accrescent.parcelo.console.publish.PublishService
import app.accrescent.parcelo.console.publish.S3PublishService
import app.accrescent.parcelo.console.routes.auth.configureAuthentication
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.storage.S3FileStorageService
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

private const val POSTGRESQL_DEFAULT_SERVER_NAME = "localhost"
private const val POSTGRESQL_DEFAULT_DATABASE_NAME = "postgres"
private const val POSTGRESQL_DEFAULT_PORT = 5432
private const val POSTGRESQL_DEFAULT_USER = "postgres"
private const val POSTGRESQL_DEFAULT_SSL_MODE = "verify-full"

fun main(args: Array<String>) = EngineMain.main(args)

@OptIn(ExperimentalSerializationApi::class)
fun Application.module() {
    log.info("Starting Parcelo console 0.10.0")

    val config = Config(
        application = Config.Application(
            baseUrl = System.getenv("BASE_URL"),
        ),
        cors = Config.Cors(
            allowedHost = System.getenv("CORS_ALLOWED_HOST"),
            allowedScheme = System.getenv("CORS_ALLOWED_SCHEME"),
        ),
        postgresql = Config.Postgresql(
            serverName = System.getenv("POSTGRESQL_SERVER_NAME")
                ?: POSTGRESQL_DEFAULT_SERVER_NAME,
            databaseName = System.getenv("POSTGRESQL_DATABASE_NAME")
                ?: POSTGRESQL_DEFAULT_DATABASE_NAME,
            portNumber = System.getenv("POSTGRESQL_PORT_NUMBER")?.toInt()
                ?: POSTGRESQL_DEFAULT_PORT,
            user = System.getenv("POSTGRESQL_USER") ?: POSTGRESQL_DEFAULT_USER,
            password = System.getenv("POSTGRESQL_PASSWORD"),
            sslMode = System.getenv("POSTGRESQL_SSL_MODE") ?: POSTGRESQL_DEFAULT_SSL_MODE,
        ),
        privateStorage = System.getenv("PRIVATE_STORAGE_BACKEND")?.let {
            when (it) {
                "S3" -> Config.ObjectStorage.S3(
                    endpointUrl = System.getenv("PRIVATE_STORAGE_ENDPOINT_URL"),
                    region = System.getenv("PRIVATE_STORAGE_REGION"),
                    bucket = System.getenv("PRIVATE_STORAGE_BUCKET"),
                    accessKeyId = System.getenv("PRIVATE_STORAGE_ACCESS_KEY_ID"),
                    secretAccessKey = System.getenv("PRIVATE_STORAGE_SECRET_ACCESS_KEY"),
                )

                else ->
                    throw Exception("invalid private storage backend $it; must be one of [S3]")
            }
        } ?: throw Exception("PRIVATE_STORAGE_BACKEND is not specified in the environment"),
        s3 = Config.S3(
            endpointUrl = System.getenv("S3_ENDPOINT_URL"),
            region = System.getenv("S3_REGION"),
            bucket = System.getenv("S3_BUCKET"),
            accessKeyId = System.getenv("S3_ACCESS_KEY_ID"),
            secretAccessKey = System.getenv("S3_SECRET_ACCESS_KEY"),
        ),
        github = Config.GitHub(
            clientId = System.getenv("GITHUB_OAUTH2_CLIENT_ID")
                ?: throw Exception("GITHUB_OAUTH2_CLIENT_ID not specified in environment"),
            clientSecret = System.getenv("GITHUB_OAUTH2_CLIENT_SECRET")
                ?: throw Exception("GITHUB_OAUTH2_CLIENT_SECRET not specified in environment"),
            redirectUrl = System.getenv("GITHUB_OAUTH2_REDIRECT_URL"),
        ),
    )

    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            single { config }
            single<FileStorageService> {
                when (config.privateStorage) {
                    is Config.ObjectStorage.S3 -> S3FileStorageService(
                        Url.parse(config.privateStorage.endpointUrl),
                        config.privateStorage.region,
                        config.privateStorage.bucket,
                        config.privateStorage.accessKeyId,
                        config.privateStorage.secretAccessKey,
                    )
                }
            }
            single { HttpClient { install(HttpTimeout) } }
            single<PublishService> {
                S3PublishService(
                    Url.parse(config.s3.endpointUrl),
                    config.s3.region,
                    config.s3.bucket,
                    config.s3.accessKeyId,
                    config.s3.secretAccessKey,
                )
            }
        }

        modules(mainModule)
    }
    val httpClient: HttpClient by inject()

    install(ContentNegotiation) {
        json(Json {
            explicitNulls = false
        })
    }
    install(CORS) {
        allowCredentials = true

        allowHost(config.cors.allowedHost, schemes = listOf(config.cors.allowedScheme))
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
    }
    configureJobRunr(configureDatabase())
    configureAuthentication(
        githubClientId = config.github.clientId,
        githubClientSecret = config.github.clientSecret,
        githubRedirectUrl = config.github.redirectUrl,
        httpClient = httpClient,
    )
    configureRouting()
}
