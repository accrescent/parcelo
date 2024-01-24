// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.User as SerializableUser
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

object Users : UUIDTable("users") {
    val githubUserId = long("gh_id").uniqueIndex()
    val email = text("email")
    val publisher = bool("publisher").default(false)
}

class User(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableUser> {
    companion object : UUIDEntityClass<User>(Users)

    var githubUserId by Users.githubUserId
    var email by Users.email
    var publisher by Users.publisher

    override fun serializable() = SerializableUser(id.value.toString(), githubUserId, email)
}
