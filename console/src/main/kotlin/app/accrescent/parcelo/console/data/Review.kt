// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Reviews : IntIdTable("reviews") {
    val approved = bool("approved")
    val additionalNotes = text("additional_notes").nullable()
}

class Review(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Review>(Reviews)

    var approved by Reviews.approved
    var additionalNotes by Reviews.additionalNotes
}
