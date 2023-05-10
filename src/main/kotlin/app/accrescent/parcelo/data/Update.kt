package app.accrescent.parcelo.data

import app.accrescent.parcelo.data.net.Update as SerializableUpdate
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.util.UUID

object Updates : UUIDTable("updates") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val creatorId = reference("creator_id", Users, ReferenceOption.CASCADE)
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", Reviewers).nullable()
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
    val submitted = bool("submitted").default(false)

    init {
        check {
            // This is an XNOR over the nullness of reviewerId and reviewIssueGroupId. In other
            // words, either both reviewerId and reviewIssueGroupId must be null or both must be
            // non-null.
            reviewerId.isNull().and(reviewIssueGroupId.isNull())
                .or(reviewerId.isNotNull() and reviewIssueGroupId.isNotNull())
        }
    }
}

class Update(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableUpdate> {
    companion object : UUIDEntityClass<Update>(Updates)

    var appId by Updates.appId
    var versionCode by Updates.versionCode
    var versionName by Updates.versionName
    var creatorId by Updates.creatorId
    var fileId by Updates.fileId
    var reviewerId by Updates.reviewerId
    var reviewIssueGroupId by Updates.reviewIssueGroupId
    var submitted by Updates.submitted

    override fun serializable(): SerializableUpdate {
        return SerializableUpdate(
            id.value.toString(),
            appId.value,
            versionCode,
            versionName,
            reviewerId != null,
        )
    }
}
