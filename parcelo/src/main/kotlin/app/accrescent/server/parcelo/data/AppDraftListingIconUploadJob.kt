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
    name = "app_draft_listing_icon_upload_jobs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class AppDraftListingIconUploadJob(
    @Column(name = "app_draft_listing_id", nullable = false)
    var appDraftListingId: UUID,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    var bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    var objectId: String,

    @Column(columnDefinition = "text", name = "background_operation_id", nullable = false)
    var backgroundOperationId: String,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_listing_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appDraftListing: AppDraftListing

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "background_operation_id", insertable = false, updatable = false)
    lateinit var backgroundOperation: BackgroundOperation

    companion object : PanacheCompanion<AppDraftListingIconUploadJob> {
        fun findByBucketIdAndObjectId(
            bucketId: String,
            objectId: String,
        ): AppDraftListingIconUploadJob? {
            return find("WHERE bucketId = ?1 AND objectId = ?2", bucketId, objectId).firstResult()
        }
    }
}
