// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository

import app.accrescent.parcelo.repository.data.configureDatabase
import app.accrescent.parcelo.repository.routes.auth.configureAuthentication
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()

        val mainModule = module {
            single {
                Config(
                    System.getenv("REPOSITORY_DATABASE_PATH"),
                    System.getenv("REPOSITORY_PUBLISH_DIR"),
                )
            }
        }

        modules(mainModule)
    }
    configureDatabase()
    configureAuthentication()
    configureRouting()
}
