// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.web

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrganizationRelationshipSet
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import app.accrescent.server.parcelo.security.UserRegistrationService
import io.quarkus.oidc.IdToken
import io.quarkus.oidc.OidcSession
import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import java.net.URI
import java.time.OffsetDateTime

@Authenticated
@Path("/web/account")
class AccountResource(
    private val config: ParceloConfig,
    @IdToken val idToken: JsonWebToken,
    private val oidcSession: OidcSession,
    private val userRegistrationService: UserRegistrationService,
) {
    @GET
    @Path("/login")
    fun login(): Response = Response.seeOther(URI(config.authRedirectUrl())).build()

    @POST
    @Path("/logout")
    fun logout(): Uni<Response> = oidcSession.logout().map { Response.ok().build() }

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
            if (!userRegistrationService.registrationsAvailable()) {
                return Response.status(Response.Status.FORBIDDEN).build()
            }

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
                registeredAt = OffsetDateTime.now(),
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
