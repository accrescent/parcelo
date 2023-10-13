// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.Apps as DbApps
import app.accrescent.parcelo.console.data.AccessControlLists
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Review
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.net.ApiError
import app.accrescent.parcelo.console.jobs.registerPublishAppJob
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import java.util.UUID

@Resource("/apps")
class Apps {
    @Resource("{id}")
    class Id(val parent: Apps = Apps(), val id: String) {
        @Resource("updates")
        class Updates(val parent: Id)
    }
}

fun Route.appRoutes() {
    authenticate("cookie") {
        createAppRoute()
        getAppRoute()
        getAppsRoute()
    }
}

@Serializable
data class CreateAppRequest(@SerialName("draft_id") val draftId: String)

fun Route.createAppRoute() {
    post<Apps> {
        val userId = call.principal<Session>()!!.userId

        // Only allow publishers to publish apps
        val isPublisher = transaction { User.findById(userId)?.publisher }
        if (isPublisher != true) {
            call.respond(HttpStatusCode.Forbidden, ApiError.publishForbidden())
            return@post
        }

        val request = call.receive<CreateAppRequest>()
        val draftId = try {
            UUID.fromString(request.draftId)
        } catch (e: IllegalArgumentException) {
            // The draft ID isn't a valid UUID
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(request.draftId))
            return@post
        }

        // Only allow publishing of approved apps which are not already being published
        val draft = transaction {
            Draft
                .findById(draftId)
                .takeIf { draft -> draft?.reviewId?.let { Review.findById(it)?.approved } ?: false }
                ?.takeIf { draft -> !draft.publishing }
                // Update publishing status if found
                ?.apply { publishing = true }
        }

        if (draft != null) {
            // A draft with this ID exists, so register a job to publish it as an app
            BackgroundJob.enqueue { registerPublishAppJob(draft.id.value) }
            call.respond(HttpStatusCode.Accepted)
        } else {
            // No draft with this ID exists
            call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
        }
    }
}

fun Route.getAppRoute() {
    get<Apps.Id> { route ->
        val userId = call.principal<Session>()!!.userId
        val appId = route.id

        val app = transaction {
            DbApps
                .innerJoin(AccessControlLists)
                .select {
                    AccessControlLists.userId.eq(userId)
                        .and(DbApps.id eq AccessControlLists.appId)
                        .and(DbApps.id eq appId)
                }
                .singleOrNull()
                ?.let { App.wrapRow(it) }
                ?.serializable()
        }

        if (app == null) {
            call.respond(HttpStatusCode.NotFound, ApiError.appNotFound(appId))
        } else {
            call.respond(HttpStatusCode.OK, app)
        }
    }
}

fun Route.getAppsRoute() {
    get<Apps> {
        val userId = call.principal<Session>()!!.userId

        val apps = transaction {
            DbApps
                .innerJoin(AccessControlLists)
                .select {
                    AccessControlLists.userId eq userId and (DbApps.id eq AccessControlLists.appId)
                }
                .map { App.wrapRow(it).serializable() }
        }

        call.respond(apps)
    }
}
