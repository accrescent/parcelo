package app.accrescent.parcelo.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun configureDatabase() {
    Database.connect(
        "jdbc:h2:mem:parcelo;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "org.h2.Driver",
    )

    transaction {
        SchemaUtils.create(Apps, Drafts)
    }
}
