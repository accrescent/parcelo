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

object BaselineUpdates : UUIDTable("updates") {
    val appId = reference("app_id", BaselineApps, ReferenceOption.CASCADE)
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val creatorId = reference("creator_id", BaselineUsers, ReferenceOption.CASCADE)
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val fileId = reference("file_id", BaselineFiles, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", BaselineReviewers).nullable()
    val reviewIssueGroupId =
        reference(
            "review_issue_group_id",
            BaselineReviewIssueGroups,
            ReferenceOption.NO_ACTION
        ).nullable()
    val submitted = bool("submitted").default(false)
    val reviewId = reference("review_id", BaselineReviews, ReferenceOption.NO_ACTION).nullable()
    val published = bool("published").default(false)

    init {
        check {
            // A reviewer may not be assigned if there isn't a set of review issues
            not(reviewerId.isNotNull() and reviewIssueGroupId.isNull())
        }
    }
}
