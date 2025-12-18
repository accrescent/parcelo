// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
@Table(
    name = "app_draft_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_draft_id", "language"])],
)
class AppDraftListing(
    @Id
    val id: UUID,

    @Column(name = "app_draft_id", nullable = false)
    val appDraftId: UUID,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    val shortDescription: String,

    @Column(name = "icon_image_id")
    var iconImageId: UUID?,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appDraft: AppDraft

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_image_id", insertable = false, updatable = false)
    val icon: Image? = null

    companion object : PanacheCompanionBase<AppDraftListing, UUID> {
        fun findByAppDraftIdAndLanguage(appDraftId: UUID, language: String): AppDraftListing? {
            return find("WHERE appDraftId = ?1 AND language = ?2", appDraftId, language)
                .firstResult()
        }

        fun exists(appDraftId: UUID, language: String): Boolean {
            return count("WHERE appDraftId = ?1 AND language = ?2", appDraftId, language) > 0
        }
    }
}
