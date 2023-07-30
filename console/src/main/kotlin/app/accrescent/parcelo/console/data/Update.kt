package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.Update as SerializableUpdate
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import java.util.UUID

object Updates : UUIDTable("updates") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val creatorId = reference("creator_id", Users, ReferenceOption.CASCADE)
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val reviewerId = reference("reviewer_id", Reviewers).nullable()
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
    val submitted = bool("submitted").default(false)
    val reviewId = reference("review_id", Reviews, ReferenceOption.NO_ACTION).nullable()

    init {
        check {
            // A reviewer may not be assigned if there isn't a set of review issues
            not(reviewerId.isNotNull() and reviewIssueGroupId.isNull())
        }
    }
}

class Update(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableUpdate> {
    companion object : UUIDEntityClass<Update>(Updates)

    var appId by Updates.appId
    var versionCode by Updates.versionCode
    var versionName by Updates.versionName
    var creatorId by Updates.creatorId
    val creationTime by Updates.creationTime
    var fileId by Updates.fileId
    var reviewerId by Updates.reviewerId
    var reviewIssueGroupId by Updates.reviewIssueGroupId
    var submitted by Updates.submitted
    var reviewId by Updates.reviewId

    override fun serializable(): SerializableUpdate {
        return SerializableUpdate(
            id.value.toString(),
            appId.value,
            versionCode,
            versionName,
            creationTime,
            reviewIssueGroupId != null,
        )
    }
}
