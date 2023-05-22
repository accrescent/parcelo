package app.accrescent.parcelo.repository

import app.accrescent.parcelo.repository.routes.appRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(Resources)

    routing {
        route("/api/v1") {
            appRoutes()
        }
    }
}
