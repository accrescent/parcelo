package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.Draft as SerializableDraft
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import java.util.UUID

// This is a UUID table because the ID is exposed to unprivileged API consumers. We don't want to
// leak e.g. the total number of drafts.
object Drafts : UUIDTable("drafts") {
    val appId = text("app_id")
    val label = text("label")
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val submitterId = reference("submitter_id", Users, ReferenceOption.CASCADE)
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val iconId = reference("icon_id", Icons, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", Reviewers).nullable()
    val approved = bool("approved").default(false)
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()

    init {
        // Drafts can't be approved without being submitted (which is equivalent to having a
        // reviewer assigned) first
        check { not(approved eq true and reviewerId.isNull()) }
    }
}

class Draft(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableDraft> {
    companion object : UUIDEntityClass<Draft>(Drafts)

    var appId by Drafts.appId
    var label by Drafts.label
    var versionCode by Drafts.versionCode
    var versionName by Drafts.versionName
    var submitterId by Drafts.submitterId
    var fileId by Drafts.fileId
    var iconId by Drafts.iconId
    var reviewerId by Drafts.reviewerId
    var approved by Drafts.approved
    var reviewIssueGroupId by Drafts.reviewIssueGroupId

    override fun serializable(): SerializableDraft {
        return SerializableDraft(
            id.value.toString(),
            appId,
            label,
            versionCode,
            versionName,
            reviewerId != null,
            approved,
        )
    }
}
