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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
@Table(name = "app_draft_acls")
class AppDraftAcl(
    @Column(name = "app_draft_id", nullable = false)
    val appDraftId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "can_create_listings", nullable = false)
    val canCreateListings: Boolean,

    @Column(name = "can_delete", nullable = false)
    val canDelete: Boolean,

    @Column(name = "can_replace_package", nullable = false)
    val canReplacePackage: Boolean,

    @Column(name = "can_view", nullable = false)
    val canView: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var appDraft: AppDraft

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var user: User

    companion object : PanacheCompanion<AppDraftAcl> {
        fun findByAppDraftIdAndUserId(appDraftId: UUID, userId: UUID): AppDraftAcl? {
            return find("WHERE appDraftId = ?1 AND userId = ?2", appDraftId, userId).firstResult()
        }
    }
}
