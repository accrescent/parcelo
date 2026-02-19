// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "organization_relationship_sets",
    uniqueConstraints = [UniqueConstraint(columnNames = ["organization_id", "user_id"])],
)
class OrganizationRelationshipSet(
    @Column(columnDefinition = "text", name = "organization_id", nullable = false)
    var organizationId: String,

    @Column(columnDefinition = "text", name = "user_id", nullable = false)
    var userId: String,

    @Column(nullable = false)
    var owner: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    private lateinit var organization: Organization

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    private lateinit var user: User

    companion object : PanacheCompanion<OrganizationRelationshipSet> {
        fun findByAppIdAndUserId(appId: String, userId: String): OrganizationRelationshipSet? {
            return find(
                "FROM OrganizationRelationshipSet organization_relationship_sets " +
                        "JOIN App apps " +
                        "ON apps.organizationId = organization_relationship_sets.organizationId " +
                        "WHERE apps.id = ?1 " +
                        "AND organization_relationship_sets.userId = ?2",
                appId,
                userId,
            )
                .firstResult()
        }

        fun findByAppDraftIdAndUserId(
            appDraftId: String,
            userId: String,
        ): OrganizationRelationshipSet? {
            return find(
                "FROM OrganizationRelationshipSet organization_relationship_sets " +
                        "JOIN AppDraft app_drafts " +
                        "ON app_drafts.organizationId = organization_relationship_sets.organizationId " +
                        "WHERE app_drafts.id = ?1 " +
                        "AND organization_relationship_sets.userId = ?2",
                appDraftId,
                userId,
            )
                .firstResult()
        }

        fun findByAppEditIdAndUserId(appEditId: String, userId: String): OrganizationRelationshipSet? {
            return find(
                "FROM OrganizationRelationshipSet organization_relationship_sets " +
                        "JOIN App apps " +
                        "ON apps.organizationId = organization_relationship_sets.organizationId " +
                        "JOIN AppEdit app_edits " +
                        "ON app_edits.appId = apps.id " +
                        "WHERE app_edits.id = ?1 " +
                        "AND organization_relationship_sets.userId = ?2",
                appEditId,
                userId,
            )
                .firstResult()
        }

        fun findByOrganizationIdAndUserId(
            organizationId: String,
            userId: String,
        ): OrganizationRelationshipSet? {
            return find("WHERE organizationId = ?1 AND userId = ?2", organizationId, userId)
                .firstResult()
        }
    }
}
