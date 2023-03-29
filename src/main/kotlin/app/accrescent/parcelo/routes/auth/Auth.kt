package app.accrescent.parcelo.routes.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Application.configureAuthentication(
    githubClientId: String,
    githubClientSecret: String,
    githubRedirectUrl: String,
    httpClient: HttpClient = HttpClient(CIO),
) {
    install(Authentication) {
        github(githubClientId, githubClientSecret, githubRedirectUrl, httpClient)
    }
}

fun Route.authRoutes() {
    route("/auth") {
        githubRoutes()
    }
}
