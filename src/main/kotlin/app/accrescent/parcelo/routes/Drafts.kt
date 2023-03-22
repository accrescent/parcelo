package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.Draft as DraftDao
import app.accrescent.parcelo.data.net.Draft
import app.accrescent.parcelo.validation.ApkSetMetadata
import app.accrescent.parcelo.validation.InvalidApkSetException
import app.accrescent.parcelo.validation.parseApkSet
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.h2.api.ErrorCode
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Resource("/drafts")
class Drafts {
    @Resource("{id}")
    class Id(val parent: Drafts = Drafts(), val id: String)
}

fun Route.draftRoutes() {
    createDraftRoute()
    deleteDraftRoute()
    getDraftsRoute()
    getDraftRoute()
}

fun Route.createDraftRoute() {
    post("/drafts") {
        var apkSetMetadata: ApkSetMetadata? = null
        var label: String? = null

        val multipart = call.receiveMultipart().readAllParts()
        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                apkSetMetadata = try {
                    part.streamProvider().use { parseApkSet(it) }
                } catch (e: InvalidApkSetException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } finally {
                    part.dispose()
                }
            } else if (part is PartData.FormItem && part.name == "label") {
                label = part.value
            }
        }

        if (apkSetMetadata != null && label != null) {
            try {
                val draft = transaction {
                    val draftEntry = DraftDao.new {
                        this.label = label
                        appId = apkSetMetadata.appId
                        versionCode = apkSetMetadata.versionCode
                        versionName = apkSetMetadata.versionName
                    }

                    Draft(
                        id = draftEntry.id.value.toString(),
                        appId = draftEntry.appId,
                        label = draftEntry.label,
                        versionCode = draftEntry.versionCode,
                        versionName = draftEntry.versionName,
                    )
                }
                call.respond(draft)
            } catch (e: ExposedSQLException) {
                if (e.errorCode == ErrorCode.DUPLICATE_KEY_1) {
                    call.respond(HttpStatusCode.Conflict)
                } else {
                    throw e
                }
            }
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}

fun Route.deleteDraftRoute() {
    delete<Drafts.Id> {
        val draftId = try {
            UUID.fromString(it.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }

        val draft = transaction { DraftDao.findById(draftId) }
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            transaction { draft.delete() }
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun Route.getDraftsRoute() {
    get<Drafts> {
        val drafts = transaction {
            DraftDao.all().map {
                Draft(it.id.value.toString(), it.appId, it.label, it.versionCode, it.versionName)
            }
        }

        call.respond(drafts)
    }
}

fun Route.getDraftRoute() {
    get<Drafts.Id> {
        val draftId = try {
            UUID.fromString(it.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val draftEntry = transaction { DraftDao.findById(draftId) }
        if (draftEntry == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val draft = Draft(
                draftEntry.id.value.toString(),
                draftEntry.appId,
                draftEntry.label,
                draftEntry.versionCode,
                draftEntry.versionName,
            )
            call.respond(draft)
        }
    }
}
