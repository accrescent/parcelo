// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

import app.accrescent.parcelo.console.routes.appRoutes
import app.accrescent.parcelo.console.routes.auth.authRoutes
import app.accrescent.parcelo.console.routes.draftRoutes
import app.accrescent.parcelo.console.routes.editRoutes
import app.accrescent.parcelo.console.routes.healthRoutes
import app.accrescent.parcelo.console.routes.sessionRoutes
import app.accrescent.parcelo.console.routes.updateRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(Resources)

    routing {
        authRoutes()
        healthRoutes()

        route("/api/v1") {
            sessionRoutes()

            appRoutes()
            draftRoutes()
            editRoutes()
            updateRoutes()
        }
    }
}
