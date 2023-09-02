// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

import app.accrescent.parcelo.console.data.configureDatabase
import app.accrescent.parcelo.console.jobs.configureJobRunr
import app.accrescent.parcelo.console.routes.auth.configureAuthentication
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.storage.LocalFileStorageService
import io.ktor.client.HttpClient
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.io.path.Path

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.module() {
    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            single {
                Config(
                    System.getenv("BASE_URL"),
                    System.getenv("CONSOLE_DATABASE_PATH"),
                    System.getenv("REPOSITORY_URL"),
                    System.getenv("REPOSITORY_API_KEY"),
                )
            }
            single<FileStorageService> { LocalFileStorageService(Path(System.getenv("FILE_STORAGE_BASE_DIR"))) }
            single { HttpClient() }
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
        githubClientId = System.getenv("GITHUB_OAUTH2_CLIENT_ID"),
        githubClientSecret = System.getenv("GITHUB_OAUTH2_CLIENT_SECRET"),
        githubRedirectUrl = System.getenv("GITHUB_OAUTH2_REDIRECT_URL"),
        httpClient = httpClient,
    )
    configureRouting()
}
