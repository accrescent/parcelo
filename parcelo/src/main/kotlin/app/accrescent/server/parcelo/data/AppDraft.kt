// SPDX-FileCopyrightText: © 2025 Logan Magee
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
    name = "app_drafts",
    check = [
        // Forbid the draft from being submitted if no app package is attached to it
        CheckConstraint(constraint = "app_package_id IS NOT NULL OR submitted_at IS NULL"),
        // Forbid the draft from being submitted if no default listing language is set
        CheckConstraint(constraint = "default_listing_language IS NOT NULL or submitted_at IS NULL"),
        // Forbid the draft from being reviewed if it is not submitted
        CheckConstraint(constraint = "submitted_at IS NOT NULL OR review_id IS NULL"),
        // Forbid the draft from being in a publishing state if it is not reviewed
        CheckConstraint(constraint = "review_id IS NOT NULL OR publishing = false"),
        // Forbid the draft from being published if it is not reviewed
        CheckConstraint(constraint = "review_id IS NOT NULL OR published_at IS NULL"),
        // Forbid the draft from being publishing and published simultaneously
        CheckConstraint(constraint = "publishing = false OR published_at IS NULL"),
    ],
)
class AppDraft(
    @Id
    @Column(columnDefinition = "text")
    var id: String,

    @Column(columnDefinition = "text", name = "organization_id", nullable = false)
    var organizationId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime,

    @Column(name = "app_package_id")
    var appPackageId: UUID?,

    @Column(columnDefinition = "text", name = "default_listing_language")
    var defaultListingLanguage: String?,

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
    lateinit var organization: Organization

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    var appPackage: AppPackage? = null

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    var review: Review? = null

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "appDraft")
    lateinit var listings: List<AppDraftListing>

    fun hasListingForLanguage(language: String): Boolean {
        return count(
            "FROM AppDraftListing app_draft_listings " +
                    "WHERE app_draft_listings.appDraftId = ?1 " +
                    "AND app_draft_listings.language = ?2",
            id,
            language,
        ) > 0
    }

    fun allListingsHaveIcon(): Boolean {
        return count(
            "FROM AppDraftListing app_draft_listings " +
                    "WHERE app_draft_listings.appDraftId = ?1 " +
                    "AND app_draft_listings.iconImageId IS NULL",
            id,
        ) == 0L
    }

    companion object : PanacheCompanionBase<AppDraft, String> {
        fun countActiveInOrganization(organizationId: String): Long {
            // An app draft is considered active if it is not in a terminal state. The terminal
            // states are:
            //
            // - Published
            // - Reviewed with a rejection
            return count(
                "LEFT JOIN Review reviews " +
                        "ON reviews.id = reviewId " +
                        "WHERE organizationId = ?1 " +
                        "AND publishedAt IS NULL " +
                        "AND (reviews.approved IS NULL OR reviews.approved = true)",
                organizationId,
            )
        }

        fun findForOrganizationAndUserByQuery(
            organizationId: String,
            userId: String,
            pageSize: UInt,
            afterAppDraftId: String?,
        ): List<AppDraft> {
            return if (afterAppDraftId == null) {
                find(
                    "FROM AppDraft app_drafts " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = app_drafts.organizationId " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
                            "AND organization_relationship_sets.organizationId = ?2 " +
                            "ORDER BY app_drafts.id ASC LIMIT ?3",
                    userId,
                    organizationId,
                    pageSize.toLong(),
                )
            } else {
                find(
                    "FROM AppDraft app_drafts " +
                            "JOIN OrganizationRelationshipSet organization_relationship_sets " +
                            "ON organization_relationship_sets.organizationId = app_drafts.organizationId " +
                            "WHERE organization_relationship_sets.userId = ?1 " +
                            "AND organization_relationship_sets.owner = true " +
                            "AND organization_relationship_sets.organizationId = ?2 " +
                            "AND app_drafts.id > ?3 " +
                            "ORDER BY app_drafts.id ASC LIMIT ?4",
                    userId,
                    organizationId,
                    afterAppDraftId,
                    pageSize.toLong(),
                )
            }
                .list()
        }

        fun submittedDraftExistsWithAppId(appId: String): Boolean {
            return count(
                "FROM AppDraft app_drafts " +
                        "JOIN AppPackage app_packages " +
                        "ON app_packages.id = app_drafts.appPackageId " +
                        "WHERE app_drafts.submittedAt IS NOT NULL " +
                        "AND app_packages.appId = ?1",
                appId,
            ) > 0
        }
    }
}
