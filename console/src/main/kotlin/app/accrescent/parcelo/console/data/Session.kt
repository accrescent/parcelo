// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import io.ktor.server.auth.Principal
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Sessions : IdTable<String>("sessions") {
    override val id = text("id").entityId()
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val expiryTime = long("expiry_time")
    override val primaryKey = PrimaryKey(id)
}

class Session(id: EntityID<String>) : Entity<String>(id), Principal {
    companion object : EntityClass<String, Session>(Sessions)

    var userId by Sessions.userId
    var expiryTime by Sessions.expiryTime
}
