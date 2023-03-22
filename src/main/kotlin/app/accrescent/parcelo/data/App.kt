package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.App as SerializableApp
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

object Apps : IdTable<String>() {
    override val id = text("id").entityId()
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val iconHash = text("icon_hash")
    override val primaryKey = PrimaryKey(id)
}

class App(id: EntityID<String>) : Entity<String>(id), ToSerializable<SerializableApp> {
    companion object : EntityClass<String, App>(Apps)

    var label by Apps.label
    var versionCode by Apps.versionCode
    var versionName by Apps.versionName
    var iconHash by Apps.iconHash

    override fun serializable(): SerializableApp {
        return SerializableApp(id.value, label, versionCode, versionName, iconHash)
    }
}
