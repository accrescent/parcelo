package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Drafts
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.jobs.registerPublishAppJob
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import java.util.UUID

@Resource("/apps")
class Apps {
    @Resource("{id}")
    class Id(val parent: Apps = Apps(), val id: String)
}

fun Route.appRoutes() {
    authenticate("cookie") {
        createAppRoute()
    }
    getAppRoute()
    getAppsRoute()
}

@Serializable
data class CreateAppRequest(@SerialName("draft_id") val draftId: String)

fun Route.createAppRoute() {
    post("/apps") {
        val userId = call.principal<Session>()!!.userId

        // Only allow publishers to publish apps
        val isPublisher = transaction { User.findById(userId)?.publisher }
        if (isPublisher != true) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        val request = call.receive<CreateAppRequest>()
        val draftId = try {
            UUID.fromString(request.draftId)
        } catch (e: IllegalArgumentException) {
            // The draft ID isn't a valid UUID
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        // Only allow publishing of approved apps
        val draft =
            transaction { Draft.find { Drafts.id eq draftId and Drafts.approved }.singleOrNull() }

        if (draft != null) {
            // A draft with this ID exists, so register a job to publish it as an app
            BackgroundJob.enqueue { registerPublishAppJob(draft.id.value) }
            call.respond(HttpStatusCode.Accepted)
        } else {
            // No draft with this ID exists
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

fun Route.getAppRoute() {
    get<Apps.Id> {
        val app = transaction { App.findById(it.id) }?.serializable()
        if (app == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(app)
        }
    }
}

fun Route.getAppsRoute() {
    get<Apps> {
        val apps = transaction { App.all().map { it.serializable() } }

        call.respond(apps)
    }
}
