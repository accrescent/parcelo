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
    name = "app_draft_relationship_sets",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_draft_id", "user_id"])],
)
class AppDraftRelationshipSet(
    @Column(columnDefinition = "text", name = "app_draft_id", nullable = false)
    var appDraftId: String,

    @Column(columnDefinition = "text", name = "user_id", nullable = false)
    var userId: String,

    @Column(nullable = false)
    var reviewer: Boolean,

    @Column(nullable = false)
    var publisher: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var appDraft: AppDraft

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var user: User

    companion object : PanacheCompanion<AppDraftRelationshipSet> {
        fun findByAppDraftIdAndUserId(appDraftId: String, userId: String): AppDraftRelationshipSet? {
            return find("WHERE appDraftId = ?1 AND userId = ?2", appDraftId, userId).firstResult()
        }

        fun findByAppDraftListingIdAndUserId(
            appDraftListingId: String,
            userId: String,
        ): AppDraftRelationshipSet? {
            return find(
                "FROM AppDraftRelationshipSet app_draft_relationship_sets " +
                        "JOIN AppDraft app_drafts " +
                        "ON app_drafts.id = app_draft_relationship_sets.appDraftId " +
                        "JOIN AppDraftListing app_draft_listings " +
                        "ON app_draft_listings.appDraftId = app_drafts.id " +
                        "WHERE app_draft_listings.id = ?1 " +
                        "AND app_draft_relationship_sets.userId = ?2",
                appDraftListingId,
                userId,
            )
                .firstResult()
        }
    }
}
