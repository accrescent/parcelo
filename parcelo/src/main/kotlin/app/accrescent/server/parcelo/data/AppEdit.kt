// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.CascadeType
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "app_edits",
    check = [
        // Forbid the edit from being reviewed if it is not submitted
        CheckConstraint(constraint = "submitted_at IS NOT NULL OR review_id IS NULL"),
        // Forbid the edit from being in a publishing state if it is not submitted
        CheckConstraint(constraint = "submitted_at IS NOT NULL OR publishing = false"),
        // Forbid the edit from being published if it is not submitted
        CheckConstraint(constraint = "submitted_at IS NOT NULL OR published_at IS NULL"),
        // Forbid the edit from being publishing and published simultaneously
        CheckConstraint(constraint = "publishing = false OR published_at IS NULL"),
    ]
)
class AppEdit(
    @Id
    @Column(columnDefinition = "text")
    var id: String,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    var appId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime,

    @Column(name = "expected_app_entity_tag", nullable = false)
    var expectedAppEntityTag: Int,

    @Column(columnDefinition = "text", name = "default_listing_language", nullable = false)
    var defaultListingLanguage: String,

    @Column(name = "app_package_id", nullable = false)
    var appPackageId: UUID,

    @Column(name = "submitted_at")
    var submittedAt: OffsetDateTime?,

    @Column(name = "review_id")
    var reviewId: UUID?,

    @Column(nullable = false)
    var publishing: Boolean,

    @Column(name = "published_at")
    var publishedAt: OffsetDateTime?,
) : PanacheEntityBase {
    val submitted: Boolean
        get() = submittedAt != null

    val published: Boolean
        get() = publishedAt != null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var app: App

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    lateinit var appPackage: AppPackage

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    var review: Review? = null

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "appEdit")
    lateinit var listings: List<AppEditListing>

    fun hasListingForLanguage(language: String): Boolean {
        return count(
            "FROM AppEditListing app_edit_listings " +
                    "WHERE app_edit_listings.appEditId = ?1 " +
                    "AND app_edit_listings.language = ?2",
            id,
            language,
        ) > 0
    }

    fun allListingsHaveIcon(): Boolean {
        return count(
            "FROM AppEditListing app_edit_listings " +
                    "WHERE app_edit_listings.appEditId = ?1 " +
                    "AND app_edit_listings.iconImageId IS NULL",
            id,
        ) == 0L
    }

    companion object : PanacheCompanionBase<AppEdit, String> {
        fun activeAndSubmittedEditExistsForAppId(appId: String): Boolean {
            return count(
                "LEFT JOIN Review reviews " +
                        "ON reviews.id = reviewId " +
                        "WHERE appId = ?1 " +
                        "AND submittedAt IS NOT NULL " +
                        "AND publishedAt IS NULL " +
                        "AND (reviews.approved IS NULL OR reviews.approved = true)",
                appId,
            ) > 0
        }

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
                        "AND (reviews.approved IS NULL OR reviews.approved = true)",
                appId,
            )
        }

        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun findForAppAndUserByQuery(
            appId: String,
            userId: String,
            pageSize: UInt,
            afterAppEditId: String?,
        ): List<AppEdit> {
            return if (afterAppEditId == null) {
                find(
                    "FROM AppEdit app_edits " +
                            "JOIN App apps " +
                            "ON apps.id = app_edits.appId " +
                            "JOIN OrganizationAcl organization_acls " +
                            "ON organization_acls.organizationId = apps.organizationId " +
                            "WHERE app_edits.appId = ?1 " +
                            "AND organization_acls.userId = ?2 " +
                            "AND organization_acls.canViewApps = true " +
                            "ORDER by app_edits.id ASC limit ?3",
                    appId,
                    userId,
                    pageSize.toLong(),
                )
            } else {
                find(
                    "FROM AppEdit app_edits " +
                            "JOIN App apps " +
                            "ON apps.id = app_edits.appId " +
                            "JOIN OrganizationAcl organization_acls " +
                            "ON organization_acls.organizationId = apps.organizationId " +
                            "WHERE app_edits.appId = ?1 " +
                            "AND organization_acls.userId = ?2 " +
                            "AND organization_acls.canViewApps = true " +
                            "AND app_edits.id > ?3 " +
                            "ORDER by app_edits.id ASC limit ?4",
                    appId,
                    userId,
                    afterAppEditId,
                    pageSize.toLong(),
                )
            }
                .list()
        }
    }
}
