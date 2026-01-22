// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.web

import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrganizationAcl
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import io.quarkus.oidc.IdToken
import io.quarkus.security.Authenticated
import jakarta.transaction.Transactional
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
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
    fun register() {
        if (!User.existsByGithubUserId(idToken.subject)) {
            val org = Organization(id = Identifier.generateNew(IdType.ORGANIZATION))
                .also { it.persist() }
            val user = User(
                id = Identifier.generateNew(IdType.USER),
                scopedUserId = idToken.subject,
            )
                .also { it.persist() }
            OrganizationAcl(
                organizationId = org.id,
                userId = user.id,
                canCreateAppDrafts = true,
                canEditApps = true,
                canViewApps = true,
                canViewOrganization = true,
            )
                .persist()
        }
    }
}
