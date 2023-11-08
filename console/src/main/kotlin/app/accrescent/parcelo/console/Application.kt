// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

import app.accrescent.parcelo.console.data.configureDatabase
import app.accrescent.parcelo.console.jobs.configureJobRunr
import app.accrescent.parcelo.console.routes.auth.configureAuthentication
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.storage.LocalFileStorageService
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import io.ktor.client.HttpClient
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.nio.file.Path
import kotlin.io.path.Path

private const val DEFAULT_CONFIG_PATH = "/etc/pconsole/config.toml"

fun main(args: Array<String>) = EngineMain.main(args)

@OptIn(ExperimentalSerializationApi::class)
fun Application.module() {
    val config = if (environment.developmentMode) {
        Config(
            application = Config.Application(
                baseUrl = System.getenv("BASE_URL"),
                databasePath = System.getenv("CONSOLE_DATABASE_PATH"),
                fileStorageDir = System.getenv("FILE_STORAGE_BASE_DIR"),
            ),
            repository = Config.Repository(
                url = System.getenv("REPOSITORY_URL"),
                apiKey = System.getenv("REPOSITORY_API_KEY"),
            ),
            github = Config.GitHub(
                clientId = System.getenv("GITHUB_OAUTH2_CLIENT_ID"),
                clientSecret = System.getenv("GITHUB_OAUTH2_CLIENT_SECRET"),
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
            single<FileStorageService> { LocalFileStorageService(Path(config.application.fileStorageDir)) }
            single { HttpClient() }
        }

        modules(mainModule)

        // Temporary workaround for https://github.com/InsertKoinIO/koin/issues/1674
        GlobalContext.startKoin(this)
    }
    val httpClient: HttpClient by inject()

    install(ContentNegotiation) {
        json(Json {
            explicitNulls = false
        })
    }
    install(XForwardedHeaders)
    configureJobRunr(configureDatabase())
    configureAuthentication(
        githubClientId = config.github.clientId,
        githubClientSecret = config.github.clientSecret,
        githubRedirectUrl = config.github.redirectUrl,
        httpClient = httpClient,
    )
    configureRouting()
}
