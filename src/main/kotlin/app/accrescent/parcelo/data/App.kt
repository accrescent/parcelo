package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.App as SerializableApp
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Apps : IdTable<String>("apps") {
    override val id = text("id").entityId()
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val iconId = reference("icon_id", Icons, ReferenceOption.NO_ACTION)
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
    override val primaryKey = PrimaryKey(id)
}

class App(id: EntityID<String>) : Entity<String>(id), ToSerializable<SerializableApp> {
    companion object : EntityClass<String, App>(Apps)

    var label by Apps.label
    var versionCode by Apps.versionCode
    var versionName by Apps.versionName
    var fileId by Apps.fileId
    var iconId by Apps.iconId
    var reviewIssueGroupId by Apps.reviewIssueGroupId

    override fun serializable(): SerializableApp {
        return SerializableApp(id.value, label, versionCode, versionName)
    }
}
