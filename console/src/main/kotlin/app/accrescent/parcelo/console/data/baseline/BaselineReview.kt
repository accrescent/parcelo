// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable

object BaselineReviews : IntIdTable("reviews") {
    val approved = bool("approved")
    val additionalNotes = text("additional_notes").nullable()
}
