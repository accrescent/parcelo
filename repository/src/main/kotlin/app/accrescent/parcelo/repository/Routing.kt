package app.accrescent.parcelo.repository

import app.accrescent.parcelo.repository.routes.appRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            appRoutes()
        }
    }
}
