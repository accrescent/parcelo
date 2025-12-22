// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

private const val DEFAULT_APP_DRAFT_LIMIT = 3

@Entity
@Table(name = "organizations")
class Organization(
    @Id
    val id: UUID,
) : PanacheEntityBase {
    @Column(name = "app_draft_limit", nullable = false)
    val appDraftLimit = DEFAULT_APP_DRAFT_LIMIT

    companion object : PanacheCompanionBase<Organization, UUID> {
        fun findForUserByQuery(
            userId: UUID,
            pageSize: UInt,
            lastOrganizationId: UUID?,
        ): List<Organization> {
            return if (lastOrganizationId == null) {
                find(
                    "FROM Organization organizations " +
                            "JOIN OrganizationAcl organization_acls " +
                            "ON organization_acls.organizationId = organizations.id " +
                            "AND organization_acls.userId = ?1 " +
                            "WHERE organization_acls.userId = ?1 " +
                            "AND organization_acls.canViewOrganization = true " +
                            "ORDER BY organizations.id ASC LIMIT ?2",
                    userId,
                    pageSize.toLong(),
                )
            } else {
                find(
                    "FROM Organization organizations " +
                            "JOIN OrganizationAcl organization_acls " +
                            "ON organization_acls.organizationId = organizations.id " +
                            "AND organization_acls.userId = ?1 " +
                            "WHERE organization_acls.userId = ?1 " +
                            "AND organization_acls.canViewOrganization = true " +
                            "AND organizations.id > ?2 " +
                            "ORDER BY organizations.id ASC LIMIT ?3",
                    userId,
                    lastOrganizationId,
                    pageSize.toLong(),
                )
            }
                .list()
        }
    }
}
