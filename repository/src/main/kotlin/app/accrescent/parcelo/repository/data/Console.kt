// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.data

import io.ktor.server.auth.Principal
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Consoles : IntIdTable("consoles") {
    val label = text("label").uniqueIndex()
    val apiKey = varchar("api_key", 32).uniqueIndex()
}

class Console(id: EntityID<Int>) : IntEntity(id), Principal {
    companion object : IntEntityClass<Console>(Consoles)

    var label by Consoles.label
    var apiKey by Consoles.apiKey
}
