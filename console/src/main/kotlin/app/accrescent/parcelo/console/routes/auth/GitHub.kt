package app.accrescent.parcelo.console.routes.auth

import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.Users
import app.accrescent.parcelo.console.data.WhitelistedGitHubUser
import app.accrescent.parcelo.console.data.WhitelistedGitHubUsers
import app.accrescent.parcelo.console.data.net.ApiError
import io.ktor.client.HttpClient
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
import io.ktor.server.routing.*
import io.ktor.server.sessions.generateSessionId
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.transactions.transaction
import org.kohsuke.github.GitHubBuilder

fun AuthenticationConfig.github(
    clientId: String,
    clientSecret: String,
    redirectUrl: String,
    httpClient: HttpClient = HttpClient(),
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

            post("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2 = call.principal() ?: return@post
                val githubUser = GitHubBuilder().withOAuthToken(principal.accessToken).build()

                val githubUserId = githubUser.myself.id
                // Register if not already registered
                val user = transaction {
                    User.find { Users.githubUserId eq githubUserId }.firstOrNull()
                } ?: run {
                    val email = githubUser.myself.emails2.find { it.isPrimary && it.isVerified }
                        ?.email
                        ?: run {
                            call.respond(HttpStatusCode.Unauthorized)
                            return@post
                        }

                    transaction {
                        User.new {
                            this.githubUserId = githubUserId
                            this.email = email
                        }
                    }
                }
                val userNotWhitelisted = transaction {
                    WhitelistedGitHubUser
                        .find { WhitelistedGitHubUsers.id eq user.githubUserId }
                        .empty()
                }
                if (userNotWhitelisted) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }

                val sessionId = transaction {
                    Session.new(generateSessionId()) {
                        userId = user.id
                        expiryTime =
                            System.currentTimeMillis() + SESSION_LIFETIME.inWholeMilliseconds
                    }.id.value
                }

                call.sessions.set(Session(sessionId))

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
