package app.accrescent.parcelo.routes

import app.accrescent.parcelo.routes.auth.Session as CookieSession
import app.accrescent.parcelo.data.Session
import app.accrescent.parcelo.data.Sessions
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.sessionRoutes() {
    authenticate("cookie") {
        deleteSessionRoute()
    }
}

fun Route.deleteSessionRoute() {
    delete("/session") {
        val sessionId = call.principal<Session>()!!.id

        transaction { Sessions.deleteWhere { id eq sessionId } }
        call.sessions.clear<CookieSession>()

        call.respond(HttpStatusCode.OK)
    }
}
