package app.accrescent.parcelo.routes.auth

import app.accrescent.parcelo.data.Session as SessionDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.maxAge
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.days

fun Application.configureAuthentication(
    githubClientId: String,
    githubClientSecret: String,
    githubRedirectUrl: String,
    httpClient: HttpClient = HttpClient(CIO),
) {
    val developmentMode = environment.developmentMode

    install(Sessions) {
        cookie<Session>(if (!developmentMode) "__Host-session" else "session") {
            cookie.maxAge = 1.days
            cookie.path = "/"
            cookie.secure = !developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Strict"
        }
    }

    install(Authentication) {
        session<Session>("cookie") {
            validate { session ->
                transaction { SessionDao.findById(session.id) }?.let { Session(it.id.value) }
            }

            challenge("/auth/login")
        }

        github(githubClientId, githubClientSecret, githubRedirectUrl, httpClient)
    }
}

fun Route.authRoutes() {
    authenticate("cookie") {
        get("/login") {
            call.respondRedirect("/drafts")
        }
    }

    route("/auth") {
        get("/login") {
            call.respondRedirect("/auth/github/login")
        }

        githubRoutes()
    }
}
