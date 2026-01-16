// SPDX-FileCopyrightText: © 2026 Logan Magee
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
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "app_edits")
class AppEdit(
    @Id
    @Column(columnDefinition = "text")
    val id: String,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    val appId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime,

    @Column(columnDefinition = "text", name = "default_listing_language", nullable = false)
    val defaultListingLanguage: String,

    @Column(name = "app_package_id", nullable = false)
    val appPackageId: UUID,

    @Column(name = "review_id")
    val reviewId: UUID?,

    @Column(name = "published_at")
    var publishedAt: OffsetDateTime?,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var app: App

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    lateinit var appPackage: AppPackage

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    val review: Review? = null

    companion object : PanacheCompanionBase<AppEdit, String> {
        fun countActiveForApp(appId: String): Long {
            // An app edit is considered active if it is not in a terminal state. The terminal
            // states are:
            //
            // - Published
            // - Reviewed with a rejection
            return count(
                "LEFT JOIN Review reviews " +
                        "ON reviews.id = reviewId " +
                        "WHERE appId = ?1 " +
                        "AND publishedAt IS NULL " +
                        "AND (reviews.approved IS NULL OR reviews.approved = false)",
                appId,
            )
        }
    }
}
