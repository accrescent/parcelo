package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.App as AppDao
import app.accrescent.parcelo.data.Draft
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.h2.api.ErrorCode
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Resource("/apps")
class Apps

fun Route.appRoutes() {
    createAppRoute()
    getAppsRoute()
}

@Serializable
data class CreateAppRequest(@SerialName("draft_id") val draftId: String)

fun Route.createAppRoute() {
    post("/apps") {
        val request = call.receive<CreateAppRequest>()
        val draftId = try {
            UUID.fromString(request.draftId)
        } catch (e: IllegalArgumentException) {
            // The draft ID isn't a valid UUID
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val draft = transaction { Draft.findById(draftId) }

        if (draft != null) {
            // A draft with this ID exists, so transform it into a published app
            val app = try {
                transaction {
                    draft.delete()
                    AppDao.new(draft.appId) {
                        label = draft.label
                        versionCode = draft.versionCode
                        versionName = draft.versionName
                    }
                }
            } catch (e: ExposedSQLException) {
                if (e.errorCode == ErrorCode.DUPLICATE_KEY_1) {
                    call.respond(HttpStatusCode.Conflict)
                    return@post
                } else {
                    throw e
                }
            }.serializable()

            call.respond(app)
        } else {
            // No draft with this ID exists
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

fun Route.getAppsRoute() {
    get<Apps> {
        val apps = transaction { AppDao.all().map { it.serializable() } }

        call.respond(apps)
    }
}
