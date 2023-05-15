package app.accrescent.parcelo.console.routes.auth

import app.accrescent.parcelo.console.data.Session as SessionDao
import app.accrescent.parcelo.console.data.Sessions as DbSessions
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

val SESSION_LIFETIME = 1.days

fun Application.configureAuthentication(
    githubClientId: String,
    githubClientSecret: String,
    githubRedirectUrl: String,
    httpClient: HttpClient = HttpClient(CIO),
) {
    val developmentMode = environment.developmentMode

    install(Sessions) {
        cookie<Session>(if (!developmentMode) "__Host-session" else "session") {
            cookie.maxAge = if (!developmentMode) SESSION_LIFETIME else Duration.INFINITE
            cookie.path = "/"
            cookie.secure = !developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Strict"
        }
    }

    install(Authentication) {
        session<Session>("cookie") {
            validate { session ->
                val currentTime = System.currentTimeMillis()

                transaction {
                    // We should delete _all_ expired sessions somewhere to prevent accumulating
                    // dead sessions, so we might as well do it here, eliminating the need to
                    // directly check whether the expiryTime has passed.
                    //
                    // If we ever reach a point where this causes performance issues, we can
                    // instead delete all expired sessions whenever a new session is created,
                    // which happens much less frequently than session validation.
                    DbSessions.deleteWhere { expiryTime less currentTime }
                    SessionDao.findById(session.id)
                }
            }

            challenge("/auth/login")
        }

        github(githubClientId, githubClientSecret, githubRedirectUrl, httpClient)
    }
}

fun Route.authRoutes() {
    authenticate("cookie") {
        get("/login") {
            call.respondRedirect("/dashboard")
        }
    }

    route("/auth") {
        get("/login") {
            call.respondRedirect("/auth/github/login")
        }

        githubRoutes()
    }
}
