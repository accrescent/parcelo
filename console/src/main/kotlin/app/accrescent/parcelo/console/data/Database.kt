package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.Config
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
        SchemaUtils.create(
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
            val user = User.new {
                githubUserId = debugUserGitHubId
                email = System.getenv("DEBUG_USER_EMAIL")
                publisher = true
            }
            Reviewer.new {
                userId = user.id
                email = System.getenv("DEBUG_USER_REVIEWER_EMAIL")
            }
            WhitelistedGitHubUser.new(debugUserGitHubId) {}

            // Create a session for said superuser for testing
            Session.new(System.getenv("DEBUG_USER_SESSION_ID")) {
                userId = user.id
                expiryTime = Long.MAX_VALUE
            }
        }
    }

    return dataSource
}
