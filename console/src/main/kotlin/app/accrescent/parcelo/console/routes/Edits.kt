// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.AccessControlLists
import app.accrescent.parcelo.console.data.Edit
import app.accrescent.parcelo.console.data.Edits
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.net.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

@Resource("/edits")
class Edits

fun Route.editRoutes() {
    authenticate("cookie") {
        createEditRoute()
        getEditsForAppRoute()
    }
}

/**
 * Creates an edit for the given app. The user must have the "editMetadata" permission to do this.
 */
fun Route.createEditRoute() {
    post<Apps.Id.Edits> { route ->
        val userId = call.principal<Session>()!!.userId
        val appId = route.parent.id

        val acl = transaction {
            AccessControlList
                .find { AccessControlLists.appId eq appId and (AccessControlLists.userId eq userId) }
                .singleOrNull()
        }
        if (acl == null) {
            call.respond(HttpStatusCode.NotFound, ApiError.appNotFound(appId))
            return@post
        } else if (!acl.editMetadata) {
            call.respond(HttpStatusCode.Forbidden, ApiError.editCreationForbidden())
            return@post
        }

        var shortDescription: String? = null
        for (part in call.receiveMultipart().readAllParts()) {
            if (part is PartData.FormItem && part.name == "short_description") {
                shortDescription = part.value
            } else {
                call.respond(HttpStatusCode.BadRequest, ApiError.unknownPartName(part.name))
                return@post
            }
        }
        if (shortDescription == null) {
            call.respond(HttpStatusCode.BadRequest, ApiError.missingPartName())
            return@post
        }

        val edit = transaction {
            Edit.new {
                this.appId = acl.appId
                this.shortDescription = shortDescription
            }
        }.serializable()

        call.respond(HttpStatusCode.Created, edit)
    }
}

/**
 * Returns the edits for a given app. The user must have the "editMetadata" permission to view
 * these.
 */
fun Route.getEditsForAppRoute() {
    get<Apps.Id.Edits> { route ->
        val userId = call.principal<Session>()!!.userId
        val appId = route.parent.id

        val acl = transaction {
            AccessControlList
                .find { AccessControlLists.appId eq appId and (AccessControlLists.userId eq userId) }
                .singleOrNull()
        }
        if (acl == null) {
            call.respond(HttpStatusCode.NotFound, ApiError.appNotFound(appId))
            return@get
        } else if (!acl.editMetadata) {
            call.respond(HttpStatusCode.Forbidden, ApiError.readForbidden())
            return@get
        }

        val edits = transaction {
            Edit
                .find { Edits.appId eq acl.appId }
                .orderBy(Edits.creationTime to SortOrder.ASC)
                .map { it.serializable() }
        }

        call.respond(HttpStatusCode.OK, edits)
    }
}
