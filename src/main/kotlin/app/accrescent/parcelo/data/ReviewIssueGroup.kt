package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object ReviewIssueGroups : IntIdTable("review_issue_groups")

class ReviewIssueGroup(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ReviewIssueGroup>(ReviewIssueGroups)
}
