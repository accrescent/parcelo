// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ReviewIssues : IntIdTable("review_issues") {
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.CASCADE)
    val rawValue = text("raw_value")

    init {
        uniqueIndex(reviewIssueGroupId, rawValue)
    }
}

class ReviewIssue(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ReviewIssue>(ReviewIssues)

    var reviewIssueGroupId by ReviewIssues.reviewIssueGroupId
    var rawValue by ReviewIssues.rawValue
}
