// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
    name = "app_drafts",
    // Forbid the draft from being submitted if no app package is attached to it
    check = [CheckConstraint(constraint = "app_package_id IS NOT NULL OR submitted = false")],
)
class AppDraft(
    @Id
    val id: UUID,

    @Column(name = "organization_id", nullable = false)
    val organizationId: UUID,

    @Column(name = "app_package_id")
    var appPackageId: UUID?,

    @Column(name = "submitted", nullable = false)
    var submitted: Boolean,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var organization: Organization

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    val appPackage: AppPackage? = null

    companion object : PanacheCompanionBase<AppDraft, UUID> {
        fun countInOrganization(organizationId: UUID): Long {
            return count("WHERE organizationId = ?1", organizationId)
        }

        fun findByProcessingJobBucketIdAndObjectId(bucketId: String, objectId: String): AppDraft? {
            return find(
                "FROM AppDraft app_drafts " +
                        "JOIN AppDraftUploadProcessingJob package_upload_processing_jobs " +
                        "ON package_upload_processing_jobs.appDraftId = app_drafts.id " +
                        "WHERE package_upload_processing_jobs.bucketId = ?1 " +
                        "AND package_upload_processing_jobs.objectId = ?2",
                bucketId,
                objectId,
            )
                .firstResult()
        }
    }
}
