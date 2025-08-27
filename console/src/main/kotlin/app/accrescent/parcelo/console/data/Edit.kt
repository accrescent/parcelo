// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.EditStatus
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import app.accrescent.parcelo.console.data.net.Edit as SerializableEdit

object Edits : UUIDTable("edits") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val shortDescription = text("short_description").nullable()
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val reviewerId = reference("reviewer_id", Reviewers, ReferenceOption.NO_ACTION).nullable()
    val reviewId = reference("review_id", Reviews, ReferenceOption.NO_ACTION).nullable()
    val published = bool("published").default(false)

    init {
        check {
            // At least one metadata field must be non-null
            shortDescription.isNotNull()
        }
    }
}

class Edit(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableEdit> {
    companion object : UUIDEntityClass<Edit>(Edits)

    var appId by Edits.appId
    var shortDescription by Edits.shortDescription
    val creationTime by Edits.creationTime
    var reviewerId by Edits.reviewerId
    var reviewId by Edits.reviewId
    var published by Edits.published

    override fun serializable(): SerializableEdit {
        val status = when {
            reviewerId == null -> EditStatus.UNSUBMITTED
            reviewId == null -> EditStatus.SUBMITTED
            else -> {
                val review = transaction { Review.findById(reviewId!!)!! }
                if (review.approved) {
                    if (published) {
                        EditStatus.PUBLISHED
                    } else {
                        EditStatus.PUBLISHING
                    }
                } else {
                    EditStatus.REJECTED
                }
            }
        }

        return SerializableEdit(
            id.value.toString(),
            appId.value,
            shortDescription,
            creationTime,
            status,
        )
    }
}
