package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.Draft as SerializableDraft
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
    val iconHash = text("icon_hash")
}

class Draft(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableDraft> {
    companion object : UUIDEntityClass<Draft>(Drafts)

    var appId by Drafts.appId
    var label by Drafts.label
    var versionCode by Drafts.versionCode
    var versionName by Drafts.versionName
    var iconHash by Drafts.iconHash

    override fun serializable(): SerializableDraft {
        return SerializableDraft(
            id.value.toString(),
            appId,
            label,
            versionCode,
            versionName,
            iconHash,
        )
    }
}
