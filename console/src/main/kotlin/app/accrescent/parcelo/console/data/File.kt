// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Files : IntIdTable("files") {
    val deleted = bool("deleted").default(false)
    val s3ObjectKey = text("s3_object_key").nullable()
}

class File(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<File>(Files)

    var deleted by Files.deleted
    var s3ObjectKey by Files.s3ObjectKey
}
