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
import java.util.UUID

@Entity
@Table(name = "organization_acls")
class OrganizationAcl(
    @Column(name = "organization_id", nullable = false)
    val organizationId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "can_create_app_drafts", nullable = false)
    val canCreateAppDrafts: Boolean,

    @Column(name = "can_view_organization", nullable = false)
    val canViewOrganization: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    private lateinit var organization: Organization

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    private lateinit var user: User

    companion object : PanacheCompanion<OrganizationAcl> {
        fun findByOrganizationIdAndUserId(organizationId: UUID, userId: UUID): OrganizationAcl? {
            return find("WHERE organizationId = ?1 AND userId = ?2", organizationId, userId)
                .firstResult()
        }
    }
}
