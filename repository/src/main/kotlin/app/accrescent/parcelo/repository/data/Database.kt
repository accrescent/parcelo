// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.data

import app.accrescent.parcelo.repository.Config
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.sql.DriverManager

const val DEBUG_CONSOLE_LABEL = "debug-console"

fun Application.configureDatabase() {
    val config: Config by inject()

    if (environment.developmentMode) {
        val url = "jdbc:sqlite:file::memory:?cache=shared"
        // Keep connection alive. See https://github.com/JetBrains/Exposed/issues/726
        DriverManager.getConnection(url)
        Database.connect(url)
    } else {
        Database.connect("jdbc:sqlite:${config.databasePath}")
    }

    transaction {
        SchemaUtils.createMissingTablesAndColumns(Consoles)

        if (environment.developmentMode) {
            // Create a default console
            Consoles.insertIgnore {
                it[label] = DEBUG_CONSOLE_LABEL
                it[apiKey] = config.repositoryApiKey
            }
        }
    }
}
