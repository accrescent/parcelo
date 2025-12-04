// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

@Entity
@Table(
    name = "orphaned_blobs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class OrphanedBlob(
    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    val bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    val objectId: String,

    @Column(name = "orphaned_on", nullable = false)
    val orphanedOn: OffsetDateTime,
) : PanacheEntity() {
    companion object : PanacheCompanion<OrphanedBlob> {
        fun deleteByBucketIdAndObjectId(bucketId: String, objectId: String) {
            delete("WHERE bucketId = ?1 AND objectId = ?2", bucketId, objectId)
        }
    }
}
