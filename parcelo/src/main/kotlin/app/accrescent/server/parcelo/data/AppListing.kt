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
import java.util.UUID

@Entity
@Table(
    name = "app_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_draft_id", "language"])],
)
class AppListing(
    @Column(name = "app_draft_id", nullable = false)
    val appDraftId: UUID,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    val shortDescription: String,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appDraft: AppDraft

    companion object : PanacheCompanion<AppListing> {
        fun deleteByAppDraftAndLanguage(appDraftId: UUID, language: String): Boolean {
            return delete("WHERE appDraftId = ?1 AND language = ?2", appDraftId, language) > 0
        }
    }
}
