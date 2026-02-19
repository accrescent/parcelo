// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID

private const val DEFAULT_ACTIVE_EDIT_LIMIT = 3

@Entity
@Table(name = "apps")
class App(
    @Id
    @Column(columnDefinition = "text")
    var id: String,

    @Column(columnDefinition = "text", name = "default_listing_language", nullable = false)
    var defaultListingLanguage: String,

    @Column(columnDefinition = "text", name = "organization_id", nullable = false)
    var organizationId: String,

    @Column(name = "entity_tag", nullable = false)
    var entityTag: Int,

    @Column(name = "app_package_id", nullable = false)
    var appPackageId: UUID,

    @Column(name = "publicly_listed", nullable = false)
    var publiclyListed: Boolean,
) : PanacheEntityBase {
    @Column(name = "active_edit_limit", nullable = false)
    var activeEditLimit = DEFAULT_ACTIVE_EDIT_LIMIT

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var organization: Organization

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    lateinit var appPackage: AppPackage

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "app")
    lateinit var listings: Set<AppListing>

    companion object : PanacheCompanionBase<App, String> {
        fun countInOrganization(organizationId: String): Long {
            return count("WHERE organizationId = ?1", organizationId)
        }

        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun findDefaultListingLanguagesForPubliclyListedByQuery(
            pageSize: UInt,
            skip: UInt,
            afterAppId: String?,
        ): List<AppDefaultListingLanguage> {
            return if (afterAppId == null) {
                find(
                    "WHERE publiclyListed = true ORDER BY id ASC LIMIT ?1 OFFSET ?2",
                    pageSize.toLong(),
                    skip.toLong(),
                )
            } else {
                find(
                    "WHERE publiclyListed = true AND id > ?1 ORDER BY id ASC LIMIT ?2 OFFSET ?3",
                    afterAppId,
                    pageSize.toLong(),
                    skip.toLong(),
                )
            }
                .project(AppDefaultListingLanguage::class.java)
                .list()
        }

        fun findForUserByQuery(userId: String, pageSize: UInt, afterAppId: String?): List<App> {
            return if (afterAppId == null) {
                find(
                    "FROM App apps " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = apps.organizationId " +
                            "AND organization_relationship_sets.userId = ?1 " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
                            "ORDER BY apps.id ASC LIMIT ?2",
                    userId,
                    pageSize.toLong(),
                )
            } else {
                find(
                    "FROM App apps " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = apps.organizationId " +
                            "AND organization_relationship_sets.userId = ?1 " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
                            "AND apps.id > ?2 " +
                            "ORDER BY apps.id ASC LIMIT ?3",
                    userId,
                    afterAppId,
                    pageSize.toLong(),
                )
            }
                .list()
        }
    }
}

@RegisterForReflection
data class AppDefaultListingLanguage(val id: String, val defaultListingLanguage: String)
