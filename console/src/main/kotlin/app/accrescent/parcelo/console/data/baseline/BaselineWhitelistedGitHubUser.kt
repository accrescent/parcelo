// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IdTable

object BaselineWhitelistedGitHubUsers : IdTable<Long>("whitelisted_github_users") {
    override val id = long("gh_id").entityId()
    override val primaryKey = PrimaryKey(id)
}
