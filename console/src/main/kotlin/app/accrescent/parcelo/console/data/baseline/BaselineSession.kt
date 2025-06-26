// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object BaselineSessions : IdTable<String>("sessions") {
    override val id = text("id").entityId()
    val userId = reference("user_id", BaselineUsers, onDelete = ReferenceOption.CASCADE)
    val expiryTime = long("expiry_time")
    override val primaryKey = PrimaryKey(id)
}
