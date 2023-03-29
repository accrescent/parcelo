package app.accrescent.parcelo

import app.accrescent.parcelo.data.configureDatabase
import app.accrescent.parcelo.plugins.configureRouting
import app.accrescent.parcelo.routes.auth.configureAuthentication
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(httpClient: HttpClient = HttpClient(CIO)) {
    install(ContentNegotiation) {
        json()
    }
    configureDatabase()
    configureAuthentication(
        githubClientId = System.getenv("GITHUB_OAUTH2_CLIENT_ID"),
        githubClientSecret = System.getenv("GITHUB_OAUTH2_CLIENT_SECRET"),
        githubRedirectUrl = System.getenv("GITHUB_OAUTH2_REDIRECT_URL"),
        httpClient = httpClient,
    )
    configureRouting()
}
