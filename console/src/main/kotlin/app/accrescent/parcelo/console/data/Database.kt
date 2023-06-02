package app.accrescent.parcelo.console.data

import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    Database.connect(
        "jdbc:h2:mem:parcelo;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "org.h2.Driver",
    )

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
