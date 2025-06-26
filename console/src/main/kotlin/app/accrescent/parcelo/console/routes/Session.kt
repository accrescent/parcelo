// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.routes.auth.Session as CookieSession
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.Sessions
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
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
    deleteSessionRoute()
}

fun Route.deleteSessionRoute() {
    delete("/session") {
        val sessionId = call.principal<Session>()?.id ?: run {
            call.respond(HttpStatusCode.OK)
            return@delete
        }

        transaction { Sessions.deleteWhere { id eq sessionId } }
        call.sessions.clear<CookieSession>()

        call.respond(HttpStatusCode.OK)
    }
}
