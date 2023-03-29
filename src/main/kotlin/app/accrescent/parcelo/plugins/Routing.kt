package app.accrescent.parcelo.plugins

import app.accrescent.parcelo.routes.appRoutes
import app.accrescent.parcelo.routes.auth.authRoutes
import app.accrescent.parcelo.routes.draftRoutes
import app.accrescent.parcelo.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(Resources)

    routing {
        authRoutes()

        appRoutes()
        draftRoutes()
        userRoutes()
    }
}
