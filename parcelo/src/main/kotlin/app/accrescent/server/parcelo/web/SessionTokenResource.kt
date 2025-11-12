// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.web

import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.ApiKey
import app.accrescent.server.parcelo.security.ApiKeyType
import io.quarkus.oidc.IdToken
import io.quarkus.security.Authenticated
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.eclipse.microprofile.jwt.JsonWebToken
import app.accrescent.server.parcelo.data.ApiKey as DbApiKey

@Authenticated
@Path("/web/session/tokens")
class SessionTokenResource {
    @IdToken
    @Inject
    lateinit var idToken: JsonWebToken

    @POST
    @Transactional
    fun generateToken(): TokenResponse {
        val apiKey = ApiKey.generateNew(ApiKeyType.USER_SESSION)

        // Get the current user's ID if they're registered or else register them
        val userId = User
            .findIdByGithubUserId(idToken.subject)
            ?.id
            ?: run { User(scopedUserId = idToken.subject).also { it.persist() }.id!! }

        // Create a new session token (which is just an API key) for the user
        val session = DbApiKey(
            userId = userId,
            apiKeyHash = apiKey.sha256Hash(),
        )
        session.persist()

        return TokenResponse(token = apiKey.rawValue())
    }
}
