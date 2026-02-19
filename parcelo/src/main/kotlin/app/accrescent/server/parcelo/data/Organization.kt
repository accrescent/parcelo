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

private const val DEFAULT_ACTIVE_APP_DRAFT_LIMIT = 3
private const val DEFAULT_PUBLISHED_APP_LIMIT = 1

@Entity
@Table(name = "organizations")
class Organization(
    @Id
    @Column(columnDefinition = "text")
    var id: String,
) : PanacheEntityBase {
    @Column(name = "active_app_draft_limit", nullable = false)
    var activeAppDraftLimit = DEFAULT_ACTIVE_APP_DRAFT_LIMIT

    @Column(name = "published_app_limit", nullable = false)
    var publishedAppLimit = DEFAULT_PUBLISHED_APP_LIMIT

    companion object : PanacheCompanionBase<Organization, String> {
        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun findForUserByQuery(
            userId: String,
            pageSize: UInt,
            lastOrganizationId: String?,
        ): List<Organization> {
            return if (lastOrganizationId == null) {
                find(
                    "FROM Organization organizations " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = organizations.id " +
                            "AND organization_relationship_sets.userId = ?1 " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
                            "ORDER BY organizations.id ASC LIMIT ?2",
                    userId,
                    pageSize.toLong(),
                )
            } else {
                find(
                    "FROM Organization organizations " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = organizations.id " +
                            "AND organization_relationship_sets.userId = ?1 " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
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
