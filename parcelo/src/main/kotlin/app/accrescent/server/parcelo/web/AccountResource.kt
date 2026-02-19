// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.web

import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrganizationRelationshipSet
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import io.quarkus.oidc.IdToken
import io.quarkus.security.Authenticated
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken

@Authenticated
@Path("/web/account")
class AccountResource(@IdToken val idToken: JsonWebToken) {
    @GET
    @Path("/login")
    fun login() = Unit

    @PUT
    @Path("/register")
    @Transactional
    fun register(): Response {
        // Require the identity provider to provide a verified email
        val email: String? = idToken.getClaim(Claims.email)
        val emailVerified: Boolean? = idToken.getClaim(Claims.email_verified)
        if (email == null || emailVerified != true) {
            return Response.status(Response.Status.FORBIDDEN).build()
        }

        if (!User.existsByOidcId(idToken.issuer, idToken.subject)) {
            val org = Organization(id = Identifier.generateNew(IdType.ORGANIZATION))
                .also { it.persist() }
            val user = User(
                id = Identifier.generateNew(IdType.USER),
                oidcProvider = OidcProvider.fromIssuer(idToken.issuer),
                oidcIssuer = idToken.issuer,
                oidcSubject = idToken.subject,
                email = email,
                reviewer = false,
                publisher = false,
            )
                .also { it.persist() }
            OrganizationRelationshipSet(
                organizationId = org.id,
                userId = user.id,
                owner = true,
            )
                .persist()
        }

        return Response.ok().build()
    }
}
