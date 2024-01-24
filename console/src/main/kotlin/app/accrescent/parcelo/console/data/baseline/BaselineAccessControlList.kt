// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineAccessControlLists : IntIdTable("access_control_lists") {
    val userId = reference("user_id", BaselineUsers, ReferenceOption.CASCADE)
    val appId = reference("app_id", BaselineApps, ReferenceOption.CASCADE)
    val update = bool("update").default(false)

    init {
        uniqueIndex(userId, appId)
    }
}
