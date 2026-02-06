// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class BackgroundOperationType {
    PUBLISH_APP_DRAFT,
    PUBLISH_APP_EDIT,
    UPLOAD_APP_DRAFT,
    UPLOAD_APP_DRAFT_LISTING_ICON,
    UPLOAD_APP_EDIT,
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
    @Id
    @Column(columnDefinition = "text")
    val id: String,

    @Column(columnDefinition = "text", nullable = false)
    @Enumerated(EnumType.STRING)
    val type: BackgroundOperationType,

    @Column(columnDefinition = "text", name = "parent_id", nullable = false)
    val parentId: String,

    @Column(nullable = false)
    var createdAt: OffsetDateTime,

    var result: ByteArray?,

    @Column(nullable = false)
    var succeeded: Boolean,
) : PanacheEntityBase {
    companion object : PanacheCompanionBase<BackgroundOperation, String>
}
