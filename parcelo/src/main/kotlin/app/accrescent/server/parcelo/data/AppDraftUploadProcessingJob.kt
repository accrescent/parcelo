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
    name = "app_draft_upload_processing_jobs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class AppDraftUploadProcessingJob(
    @Column(name = "app_draft_id", nullable = false, unique = true)
    val appDraftId: UUID,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    val bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    val objectId: String,

    @Column(nullable = false)
    val completed: Boolean,

    @Column(nullable = false)
    val succeeded: Boolean,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private lateinit var appDraft: AppDraft

    companion object : PanacheCompanion<AppDraftUploadProcessingJob> {
        fun markFailed(bucketId: String, objectId: String) {
            update(
                "SET completed = true, succeeded = false WHERE bucketId = ?1 AND objectId = ?2",
                bucketId,
                objectId,
            )
        }

        fun markSucceeded(bucketId: String, objectId: String) {
            update(
                "SET completed = true, succeeded = true WHERE bucketId = ?1 AND objectId = ?2",
                bucketId,
                objectId,
            )
        }
    }
}
