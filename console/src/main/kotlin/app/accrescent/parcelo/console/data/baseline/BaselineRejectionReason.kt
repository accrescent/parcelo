// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineRejectionReasons : IntIdTable("rejection_reasons") {
    val reviewId = reference("review_id", BaselineReviews, ReferenceOption.CASCADE)
    val reason = text("reason")
}
