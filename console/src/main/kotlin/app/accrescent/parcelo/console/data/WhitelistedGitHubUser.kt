// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

object WhitelistedGitHubUsers : IdTable<Long>("whitelisted_github_users") {
    override val id = long("gh_id").entityId()
    override val primaryKey = PrimaryKey(id)
}

class WhitelistedGitHubUser(id: EntityID<Long>) : Entity<Long>(id) {
    companion object : EntityClass<Long, WhitelistedGitHubUser>(WhitelistedGitHubUsers)
}
