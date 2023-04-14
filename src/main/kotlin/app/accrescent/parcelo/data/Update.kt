package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.Update as SerializableUpdate
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

object Updates : UUIDTable("updates") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val versionCode = integer("version_code")
    val submitterId = reference("submitter_id", Users, ReferenceOption.CASCADE)
    val reviewerId = reference("reviewer_id", Reviewers).nullable()
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
}

class Update(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableUpdate> {
    companion object : UUIDEntityClass<Update>(Updates)

    var appId by Updates.appId
    var versionCode by Updates.versionCode
    var submitterId by Updates.submitterId
    var reviewerId by Updates.reviewerId
    var reviewIssueGroupId by Updates.reviewIssueGroupId

    override fun serializable(): SerializableUpdate {
        return SerializableUpdate(id.value.toString(), appId.value, versionCode, reviewerId != null)
    }
}
