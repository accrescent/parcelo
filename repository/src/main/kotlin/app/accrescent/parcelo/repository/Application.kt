// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository

import app.accrescent.parcelo.repository.data.configureDatabase
import app.accrescent.parcelo.repository.routes.auth.configureAuthentication
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

private const val DEFAULT_CONFIG_PATH = "/etc/prepository/config.toml"

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = if (environment.developmentMode) {
        val publishDirectory = System.getenv("REPOSITORY_PUBLISH_DIR")
        Files.createDirectories(Path(publishDirectory))

        Config(
            databasePath = System.getenv("REPOSITORY_DATABASE_PATH"),
            publishDirectory = publishDirectory,
            repositoryApiKey = System.getenv("REPOSITORY_API_KEY"),
        )
    } else {
        val configPath = System.getenv("CONFIG_PATH") ?: DEFAULT_CONFIG_PATH
        tomlMapper { }.decode(Path.of(configPath))
    }

    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            single { config }
        }

        modules(mainModule)
    }
    configureDatabase()
    configureAuthentication()
    configureRouting()
}
