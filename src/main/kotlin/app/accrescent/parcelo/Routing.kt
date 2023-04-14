package app.accrescent.parcelo

import app.accrescent.parcelo.routes.appRoutes
import app.accrescent.parcelo.routes.auth.authRoutes
import app.accrescent.parcelo.routes.draftRoutes
import app.accrescent.parcelo.routes.sessionRoutes
import app.accrescent.parcelo.routes.updateRoutes
import app.accrescent.parcelo.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.angular
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.resources.Resources
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(Resources)

    routing {
        authRoutes()

        route("/api") {
            sessionRoutes()

            appRoutes()
            draftRoutes()
            userRoutes()
            updateRoutes()
        }
    }

    if (environment.developmentMode) {
        routing {
            singlePageApplication {
                angular("frontend/dist/frontend")
            }
        }
    }
}
