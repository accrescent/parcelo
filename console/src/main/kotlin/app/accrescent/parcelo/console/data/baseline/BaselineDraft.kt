// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not

// This is a UUID table because the ID is exposed to unprivileged API consumers. We don't want to
// leak e.g. the total number of drafts.
object BaselineDrafts : UUIDTable("drafts") {
    val appId = text("app_id")
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val creatorId = reference("creator_id", BaselineUsers, ReferenceOption.CASCADE)
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val fileId = reference("file_id", BaselineFiles, ReferenceOption.NO_ACTION)
    val iconId = reference("icon_id", BaselineIcons, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", BaselineReviewers).nullable()
    val reviewIssueGroupId =
        reference(
            "review_issue_group_id",
            BaselineReviewIssueGroups,
            ReferenceOption.NO_ACTION
        ).nullable()
    val reviewId = reference("review_id", BaselineReviews, ReferenceOption.NO_ACTION).nullable()
    val publishing = bool("publishing").default(false)

    init {
        // Drafts can't be reviewed without being submitted (which is equivalent to having a
        // reviewer assigned) first
        check { not(reviewId.isNotNull() and reviewerId.isNull()) }
    }
}
