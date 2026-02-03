// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class BackgroundOperationType {
    PUBLISH_APP_DRAFT,
    PUBLISH_APP_EDIT,
}

@Entity
@Table(
    name = "background_operations",
    check = [
        // Forbid the operation from being marked as succeeded if it doesn't have a result
        CheckConstraint(constraint = "result IS NOT NULL OR succeeded = false"),
    ],
)
class BackgroundOperation(
    @Column(columnDefinition = "text", nullable = false)
    @Enumerated(EnumType.STRING)
    val type: BackgroundOperationType,

    @Column(columnDefinition = "text", name = "parent_id", nullable = false)
    val parentId: String,

    @Column(columnDefinition = "text", name = "job_name", nullable = false, unique = true)
    val jobName: String,

    @Column(nullable = false)
    var createdAt: OffsetDateTime,

    var result: ByteArray?,

    @Column(nullable = false)
    var succeeded: Boolean,
) : PanacheEntity() {
    companion object : PanacheCompanion<BackgroundOperation> {
        fun findByJobName(name: String): BackgroundOperation? {
            return find("WHERE jobName = ?1", name).firstResult()
        }
    }
}
