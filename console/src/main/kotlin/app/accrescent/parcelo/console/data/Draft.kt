// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.Draft as SerializableDraft
import app.accrescent.parcelo.console.data.net.DraftStatus
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// This is a UUID table because the ID is exposed to unprivileged API consumers. We don't want to
// leak e.g. the total number of drafts.
object Drafts : UUIDTable("drafts") {
    val appId = text("app_id")
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val shortDescription = text("short_description").default("")
    val creatorId = reference("creator_id", Users, ReferenceOption.CASCADE)
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val iconId = reference("icon_id", Icons, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", Reviewers).nullable()
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
    val reviewId = reference("review_id", Reviews, ReferenceOption.NO_ACTION).nullable()
    val publishing = bool("publishing").default(false)

    init {
        // Drafts can't be reviewed without being submitted (which is equivalent to having a
        // reviewer assigned) first
        check { not(reviewId.isNotNull() and reviewerId.isNull()) }
    }
}

class Draft(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableDraft> {
    companion object : UUIDEntityClass<Draft>(Drafts)

    var appId by Drafts.appId
    var label by Drafts.label
    var versionCode by Drafts.versionCode
    var versionName by Drafts.versionName
    var shortDescription by Drafts.shortDescription
    var creatorId by Drafts.creatorId
    val creationTime by Drafts.creationTime
    var fileId by Drafts.fileId
    var iconId by Drafts.iconId
    var reviewerId by Drafts.reviewerId
    var reviewIssueGroupId by Drafts.reviewIssueGroupId
    var reviewId by Drafts.reviewId
    var publishing by Drafts.publishing

    override fun serializable(): SerializableDraft {
        val status = if (reviewerId == null) {
            DraftStatus.UNSUBMITTED
        } else if (reviewId == null) {
            DraftStatus.SUBMITTED
        } else {
            if (publishing) {
                DraftStatus.PUBLISHING
            } else {
                val review = transaction { Review.findById(reviewId!!)!! }
                if (review.approved) {
                    DraftStatus.APPROVED
                } else {
                    DraftStatus.REJECTED
                }
            }
        }

        return SerializableDraft(
            id.value.toString(),
            appId,
            label,
            versionCode,
            versionName,
            shortDescription,
            creationTime,
            status,
        )
    }
}
