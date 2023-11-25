// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.data.baseline

import org.jetbrains.exposed.dao.id.IntIdTable

object BaselineConsoles : IntIdTable("consoles") {
    val label = text("label").uniqueIndex()
    val apiKey = varchar("api_key", 32).uniqueIndex()
}
