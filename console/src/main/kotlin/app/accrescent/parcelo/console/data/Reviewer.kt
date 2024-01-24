// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Reviewers : IntIdTable("reviewers") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val email = text("email")
}

class Reviewer(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Reviewer>(Reviewers)

    var userId by Reviewers.userId
    var email by Reviewers.email
}
