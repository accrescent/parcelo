package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.User as SerializableUser
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

object Users : UUIDTable() {
    val githubUserId = long("gh_id").uniqueIndex()
    val email = text("email")
}

class User(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableUser> {
    companion object : UUIDEntityClass<User>(Users)

    var githubUserId by Users.githubUserId
    var email by Users.email

    override fun serializable() = SerializableUser(id.value.toString(), githubUserId, email)
}
