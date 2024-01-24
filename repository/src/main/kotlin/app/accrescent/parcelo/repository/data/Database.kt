// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.data

import app.accrescent.parcelo.repository.Config
import app.accrescent.parcelo.repository.data.baseline.BaselineConsoles
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import org.sqlite.SQLiteDataSource

const val DEBUG_CONSOLE_LABEL = "debug-console"

fun Application.configureDatabase() {
    val config: Config by inject()

    val dataSource = SQLiteDataSource().apply {
        url = "jdbc:sqlite:${config.databasePath}"

        setEnforceForeignKeys(true)
    }
    Database.connect(dataSource, setupConnection = {
        it.createStatement().executeUpdate("PRAGMA trusted_schema = OFF")
    })

    transaction {
        SchemaUtils.create(BaselineConsoles)

        if (environment.developmentMode) {
            // Create a default console
            Consoles.insertIgnore {
                it[label] = DEBUG_CONSOLE_LABEL
                it[apiKey] = config.repositoryApiKey
            }
        }
    }

    Flyway
        .configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .mixed(true)
        .validateMigrationNaming(true)
        .load()
        .migrate()
}
