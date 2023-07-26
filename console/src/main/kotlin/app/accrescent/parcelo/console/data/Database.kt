package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.Config
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.sql.DriverManager

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
}
