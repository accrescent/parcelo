// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineEdits : UUIDTable("edits") {
    val appId = reference("app_id", BaselineApps, ReferenceOption.CASCADE)
    val shortDescription = text("short_description").nullable()
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val reviewerId =
        reference("reviewer_id", BaselineReviewers, ReferenceOption.NO_ACTION).nullable()
    val reviewId = reference("review_id", BaselineReviews, ReferenceOption.NO_ACTION).nullable()
    val published = bool("published").default(false)

    init {
        check {
            // At least one metadata field must be non-null
            shortDescription.isNotNull()
        }
    }
}
