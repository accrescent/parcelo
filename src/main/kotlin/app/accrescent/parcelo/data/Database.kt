package app.accrescent.parcelo.data

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
        SchemaUtils.create(Apps, Drafts, Sessions, ReviewIssues, Reviewers, Users)

        // If this is a development run, create a default superuser
        if (environment.developmentMode) {
            val user = User.new {
                githubUserId = System.getenv("DEBUG_USER_GITHUB_ID").toLong()
                email = System.getenv("DEBUG_USER_EMAIL")
                publisher = true
            }
            Reviewer.new {
                userId = user.id
                email = System.getenv("DEBUG_USER_REVIEWER_EMAIL")
            }
        }
    }
}
