// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.routes.auth

import app.accrescent.parcelo.repository.data.Console
import app.accrescent.parcelo.repository.data.Consoles.apiKey
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import org.jetbrains.exposed.sql.transactions.transaction

const val API_KEY_AUTH_PROVIDER = "api-key"

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer(API_KEY_AUTH_PROVIDER) {
            authSchemes("token")

            authenticate { transaction { Console.find { apiKey eq it.token }.singleOrNull() } }
        }
    }
}
