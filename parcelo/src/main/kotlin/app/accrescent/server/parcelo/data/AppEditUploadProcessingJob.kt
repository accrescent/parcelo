// SPDX-FileCopyrightText: © 2026 Logan Magee
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
    name = "app_edit_upload_processing_jobs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class AppEditUploadProcessingJob(
    @Column(columnDefinition = "text", name = "app_edit_id", nullable = false)
    val appEditId: String,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    val bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    val objectId: String,

    @Column(columnDefinition = "text", name = "background_operation_id", nullable = false)
    val backgroundOperationId: String,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_edit_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appEdit: AppEdit

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "background_operation_id", insertable = false, updatable = false)
    lateinit var backgroundOperation: BackgroundOperation

    companion object : PanacheCompanion<AppEditUploadProcessingJob> {
        fun findByBucketIdAndObjectId(
            bucketId: String,
            objectId: String,
        ): AppEditUploadProcessingJob? {
            return find("WHERE bucketId = ?1 AND objectId = ?2", bucketId, objectId).firstResult()
        }
    }
}
