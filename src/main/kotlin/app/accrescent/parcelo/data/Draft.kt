package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

// This is a UUID table because the ID is exposed to unprivileged API consumers. We don't want to
// leak e.g. the total number of drafts.
object Drafts : UUIDTable() {
    val appId = text("app_id")
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
}

class Draft(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Draft>(Drafts)

    var appId by Drafts.appId
    var label by Drafts.label
    var versionCode by Drafts.versionCode
    var versionName by Drafts.versionName
}
