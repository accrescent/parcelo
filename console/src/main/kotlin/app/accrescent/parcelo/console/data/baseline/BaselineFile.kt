// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only
//
// DO NOT MODIFY - DATABASE BASELINE

package app.accrescent.parcelo.console.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable

object BaselineFiles : IntIdTable("files") {
    val deleted = bool("deleted").default(false)
    val s3ObjectKey = text("s3_object_key").nullable()
}
