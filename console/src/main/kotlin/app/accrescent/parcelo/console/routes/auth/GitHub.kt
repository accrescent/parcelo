package app.accrescent.parcelo.console.routes.auth

import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.Users
import app.accrescent.parcelo.console.data.WhitelistedGitHubUser
import app.accrescent.parcelo.console.data.WhitelistedGitHubUsers
import io.ktor.client.HttpClient
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationEnvironment
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

const val COOKIE_OAUTH_STATE_PROD = "__Host-oauth-state"
const val COOKIE_OAUTH_STATE_DEVEL = "oauth-state"
const val COOKIE_OAUTH_STATE_LIFETIME = 60 * 60 // 1 hour

val ApplicationEnvironment.oauthStateCookieName
    get() =
        if (!developmentMode) COOKIE_OAUTH_STATE_PROD else COOKIE_OAUTH_STATE_DEVEL

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
                onStateCreated = { call, state ->
                    // Cross-site request forgery (CSRF) protection.
                    // See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-30#section-10.12
                    val developmentMode = call.application.environment.developmentMode
                    call.response.cookies.append(
                        Cookie(
                            name = call.application.environment.oauthStateCookieName,
                            value = state,
                            maxAge = COOKIE_OAUTH_STATE_LIFETIME,
                            path = "/",
                            secure = !developmentMode,
                            httpOnly = true,
                            extensions = mapOf("SameSite" to "Lax")
                        )
                    )
                }
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
                // Cross-site request forgery (CSRF) protection.
                // See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-30#section-10.12
                val oauthCookie = call.request.cookies[call.application.environment.oauthStateCookieName]
                if (oauthCookie == null) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

                val principal: OAuthAccessTokenResponse.OAuth2 = call.principal() ?: return@get
                if (principal.state != oauthCookie) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

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
                    WhitelistedGitHubUser
                        .find { WhitelistedGitHubUsers.id eq user.githubUserId }
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
