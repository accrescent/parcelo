package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

object Apps : IdTable<String>() {
    override val id = text("id").entityId()
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    override val primaryKey = PrimaryKey(id)
}

class App(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, App>(Apps)

    var label by Apps.label
    var versionCode by Apps.versionCode
    var versionName by Apps.versionName
}
