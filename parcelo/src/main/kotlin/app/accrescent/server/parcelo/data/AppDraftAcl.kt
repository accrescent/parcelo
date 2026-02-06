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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(
    name = "app_draft_acls",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_draft_id", "user_id"])],
)
class AppDraftAcl(
    @Column(columnDefinition = "text", name = "app_draft_id", nullable = false)
    var appDraftId: String,

    @Column(columnDefinition = "text", name = "user_id", nullable = false)
    var userId: String,

    @Column(name = "can_delete", nullable = false)
    var canDelete: Boolean,

    @Column(name = "can_publish", nullable = false)
    var canPublish: Boolean,

    @Column(name = "can_replace_package", nullable = false)
    var canReplacePackage: Boolean,

    @Column(name = "can_review", nullable = false)
    var canReview: Boolean,

    @Column(name = "can_submit", nullable = false)
    var canSubmit: Boolean,

    @Column(name = "can_update", nullable = false)
    var canUpdate: Boolean,

    @Column(name = "can_view", nullable = false)
    var canView: Boolean,

    @Column(name = "can_view_existence", nullable = false)
    var canViewExistence: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var appDraft: AppDraft

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var user: User

    companion object : PanacheCompanion<AppDraftAcl> {
        fun findByAppDraftIdAndUserId(appDraftId: String, userId: String): AppDraftAcl? {
            return find("WHERE appDraftId = ?1 AND userId = ?2", appDraftId, userId).firstResult()
        }
    }
}
