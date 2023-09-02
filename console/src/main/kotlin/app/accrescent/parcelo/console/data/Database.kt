// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.Config
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import org.sqlite.SQLiteDataSource
import java.sql.DriverManager
import javax.sql.DataSource

fun Application.configureDatabase(): DataSource {
    val config: Config by inject()

    val dataSource = SQLiteDataSource().apply {
        url = if (environment.developmentMode) {
            "jdbc:sqlite:file::memory:?cache=shared".also {
                // Keep connection alive. See https://github.com/JetBrains/Exposed/issues/726
                DriverManager.getConnection(it)
            }
        } else {
            "jdbc:sqlite:${config.databasePath}"
        }
    }
    Database.connect(dataSource)

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            AccessControlLists,
            Apps,
            Drafts,
            Files,
            Icons,
            RejectionReasons,
            Reviewers,
            ReviewIssues,
            Reviews,
            Sessions,
            Updates,
            Users,
            WhitelistedGitHubUsers,
        )

        if (environment.developmentMode) {
            // Create a default superuser
            val debugUserGitHubId = System.getenv("DEBUG_USER_GITHUB_ID").toLong()
            val userId = Users.insertIgnore {
                it[githubUserId] = debugUserGitHubId
                it[email] = System.getenv("DEBUG_USER_EMAIL")
                it[publisher] = true
            }[Users.id]
            Reviewers.insertIgnore {
                it[this.userId] = userId
                it[email] = System.getenv("DEBUG_USER_REVIEWER_EMAIL")
            }
            WhitelistedGitHubUsers.insertIgnore { it[id] = debugUserGitHubId }

            // Create a session for said superuser for testing
            Sessions.insertIgnore {
                it[id] = System.getenv("DEBUG_USER_SESSION_ID")
                it[this.userId] = userId
                it[expiryTime] = Long.MAX_VALUE
            }
        }
    }

    Flyway.configure().dataSource(dataSource).baselineOnMigrate(true).load().migrate()

    return dataSource
}
