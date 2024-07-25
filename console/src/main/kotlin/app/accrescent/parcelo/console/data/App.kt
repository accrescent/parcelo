// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.App as SerializableApp
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and

object Apps : IdTable<String>("apps") {
    override val id = text("id").entityId()
    val versionCode = integer("version_code")
    val versionName = text("version_name")
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
    val reviewIssueGroupId =
        reference("review_issue_group_id", ReviewIssueGroups, ReferenceOption.NO_ACTION).nullable()
    val updating = bool("updating").default(false)
    val repositoryMetadata = blob("repository_metadata")
    override val primaryKey = PrimaryKey(id)
}

class App(id: EntityID<String>) : Entity<String>(id), ToSerializable<SerializableApp> {
    companion object : EntityClass<String, App>(Apps)

    var versionCode by Apps.versionCode
    var versionName by Apps.versionName
    var fileId by Apps.fileId
    var reviewIssueGroupId by Apps.reviewIssueGroupId
    var updating by Apps.updating
    var repositoryMetadata by Apps.repositoryMetadata

    override fun serializable(): SerializableApp {
        // Use en-US locale by default
        val listing =
            Listing.find { Listings.appId eq id and (Listings.locale eq "en-US") }.single()

        return SerializableApp(
            id.value,
            listing.label,
            versionCode,
            versionName,
            listing.shortDescription,
        )
    }
}
