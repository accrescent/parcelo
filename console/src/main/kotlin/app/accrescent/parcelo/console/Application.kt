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
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.nio.file.Path

private const val DEFAULT_CONFIG_PATH = "/etc/pconsole/config.toml"
private const val POSTGRESQL_DEFAULT_SERVER_NAME = "localhost"
private const val POSTGRESQL_DEFAULT_DATABASE_NAME = "postgres"
private const val POSTGRESQL_DEFAULT_PORT = 5432
private const val POSTGRESQL_DEFAULT_USER = "postgres"
private const val POSTGRESQL_DEFAULT_SSL = true

fun main(args: Array<String>) = EngineMain.main(args)

@OptIn(ExperimentalSerializationApi::class)
fun Application.module() {
    log.info("Starting Parcelo console 0.9.0")

    val config = if (environment.developmentMode) {
        Config(
            application = Config.Application(
                baseUrl = System.getenv("BASE_URL"),
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
                ssl = System.getenv("POSTGRESQL_SSL")?.toBooleanStrict() ?: POSTGRESQL_DEFAULT_SSL,
            ),
            privateStorage = Config.S3(
                endpointUrl = System.getenv("PRIVATE_STORAGE_ENDPOINT_URL"),
                region = System.getenv("PRIVATE_STORAGE_REGION"),
                bucket = System.getenv("PRIVATE_STORAGE_BUCKET"),
                accessKeyId = System.getenv("PRIVATE_STORAGE_ACCESS_KEY_ID"),
                secretAccessKey = System.getenv("PRIVATE_STORAGE_SECRET_ACCESS_KEY"),
            ),
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
    } else {
        val configPath = System.getenv("CONFIG_PATH") ?: DEFAULT_CONFIG_PATH
        tomlMapper { }.decode(Path.of(configPath))
    }

    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            single { config }
            single<FileStorageService> {
                S3FileStorageService(
                    Url.parse(config.privateStorage.endpointUrl),
                    config.privateStorage.region,
                    config.privateStorage.bucket,
                    config.privateStorage.accessKeyId,
                    config.privateStorage.secretAccessKey,
                )
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
    configureJobRunr(configureDatabase())
    configureAuthentication(
        githubClientId = config.github.clientId,
        githubClientSecret = config.github.clientSecret,
        githubRedirectUrl = config.github.redirectUrl,
        httpClient = httpClient,
    )
    configureRouting()
}
