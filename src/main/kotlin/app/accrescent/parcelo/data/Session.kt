package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

object Sessions : IdTable<String>("sessions") {
    override val id = text("id").entityId()
    val userId = reference("user_id", Users)
    override val primaryKey = PrimaryKey(id)
}

class Session(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, Session>(Sessions)

    var userId by Sessions.userId
}
