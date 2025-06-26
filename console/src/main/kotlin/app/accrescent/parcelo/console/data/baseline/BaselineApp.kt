// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineApps : IdTable<String>("apps") {
    override val id = text("id").entityId()
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val fileId = reference("file_id", BaselineFiles, ReferenceOption.NO_ACTION)
    val reviewIssueGroupId =
        reference(
            "review_issue_group_id",
            BaselineReviewIssueGroups,
            ReferenceOption.NO_ACTION
        ).nullable()
    val updating = bool("updating").default(false)
    val repositoryMetadata = blob("repository_metadata")
    override val primaryKey = PrimaryKey(id)
}
