package app.accrescent.parcelo.console.routes.auth

import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.Users
import app.accrescent.parcelo.console.data.WhitelistedGitHubUsers
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
import org.jetbrains.exposed.sql.select
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

                // Register if not already registered
                val user = transaction {
                    User.find { Users.githubUserId eq githubUserId }.firstOrNull()
                } ?: run {
                    val email = githubUser.myself.emails2.find { it.isPrimary && it.isVerified }
                        ?.email
                        ?: run {
                            call.respond(HttpStatusCode.Forbidden)
                            return@get
                        }

                    transaction {
                        User.new {
                            this.githubUserId = githubUserId
                            this.email = email
                        }
                    }
                }
                val userNotWhitelisted = transaction {
                    WhitelistedGitHubUsers
                        .select { WhitelistedGitHubUsers.id eq user.githubUserId }
                        .empty()
                }
                if (userNotWhitelisted) {
                    call.respondRedirect("/register/unauthorized")
                    return@get
                }

                val sessionId = transaction {
                    Session.new(generateSessionId()) {
                        userId = user.id
                        expiryTime =
                            System.currentTimeMillis() + SESSION_LIFETIME.inWholeMilliseconds
                    }.id.value
                }

                call.sessions.set(Session(sessionId))

                call.respondRedirect("/dashboard")
            }
        }
    }
}
