package app.accrescent.parcelo.repository.data

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
        SchemaUtils.create(Consoles)

        if (environment.developmentMode) {
            // Create a default console
            Console.new {
                label = "debug-console"
                apiKey = System.getenv("REPOSITORY_API_KEY")
            }
        }
    }
}
