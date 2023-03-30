package app.accrescent.parcelo.routes.auth

import app.accrescent.parcelo.data.Session as SessionDao
import app.accrescent.parcelo.data.User
import app.accrescent.parcelo.data.Users
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.oauth
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.generateSessionId
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.transactions.transaction
import org.kohsuke.github.GitHubBuilder

fun AuthenticationConfig.github(
    clientId: String,
    clientSecret: String,
    redirectUrl: String,
    httpClient: HttpClient = HttpClient(CIO),
) {
    oauth("oauth2-github") {
        urlProvider = { redirectUrl }
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "github",
                authorizeUrl = "https://github.com/login/oauth/authorize",
                accessTokenUrl = "https://github.com/login/oauth/access_token",
                requestMethod = HttpMethod.Post,
                clientId = clientId,
                clientSecret = clientSecret,
                defaultScopes = listOf("user:email"),
            )
        }
        client = httpClient
    }
}

fun Route.githubRoutes() {
    authenticate("oauth2-github") {
        route("/github") {
            get("/login") {}

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2 = call.principal() ?: return@get
                val githubUser = GitHubBuilder().withOAuthToken(principal.accessToken).build()

                val githubUserId = githubUser.myself.id
                val email =
                    githubUser.myself.emails2.find { it.isPrimary && it.isVerified }?.email ?: run {
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }

                // Register if not already registered
                val sessionId = transaction {
                    val user =
                        User.find { Users.githubUserId eq githubUserId }.firstOrNull() ?: User.new {
                            this.githubUserId = githubUserId
                            this.email = email
                        }

                    SessionDao.new(generateSessionId()) {
                        userId = user.id
                        expiryTime =
                            System.currentTimeMillis() + SESSION_LIFETIME.inWholeMilliseconds
                    }.id.value
                }

                call.sessions.set(Session(sessionId))

                call.respondRedirect("/drafts")
            }
        }
    }
}
