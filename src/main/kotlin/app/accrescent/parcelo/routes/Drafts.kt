package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.Draft as DraftDao
import app.accrescent.parcelo.data.Drafts as DbDrafts
import app.accrescent.parcelo.data.ReviewIssue
import app.accrescent.parcelo.data.ReviewIssueGroup
import app.accrescent.parcelo.data.Reviewer
import app.accrescent.parcelo.data.Reviewers
import app.accrescent.parcelo.data.Session
import app.accrescent.parcelo.validation.ApkSetMetadata
import app.accrescent.parcelo.validation.InvalidApkSetException
import app.accrescent.parcelo.validation.PERMISSION_REVIEW_BLACKLIST
import app.accrescent.parcelo.validation.parseApkSet
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.h2.api.ErrorCode
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.ImageIO

@Resource("/drafts")
class Drafts {
    @Resource("{id}")
    class Id(val parent: Drafts = Drafts(), val id: String)
}

@Serializable
data class UpdateDraftRequest(val submitted: Boolean = false, val approved: Boolean = false)

fun Route.draftRoutes() {
    authenticate("cookie") {
        createDraftRoute()
        deleteDraftRoute()
        getDraftsRoute()
        getDraftRoute()
        updateDraftRoute()
    }
}

fun Route.createDraftRoute() {
    post("/drafts") {
        val submitterId = call.principal<Session>()!!.userId

        var apkSetMetadata: ApkSetMetadata? = null
        var label: String? = null
        var iconHash: String? = null

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
            } else if (part is PartData.FileItem && part.name == "icon") {
                val iconData = part.streamProvider().use { it.readAllBytes() }

                // Icon must be a 512 x 512 PNG
                val image = iconData.inputStream().use { ImageIO.read(it) }
                if (image.width != 512 || image.height != 512) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                iconHash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(iconData)
                    .joinToString("") { "%02x".format(it) }
            } else if (part is PartData.FormItem && part.name == "label") {
                // Label must be between 3 and 30 characters in length inclusive
                if (part.value.length < 3 || part.value.length > 30) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } else {
                    label = part.value
                }
            }
        }

        if (apkSetMetadata != null && label != null && iconHash != null) {
            try {
                val draft = transaction {
                    // Associate review issues with draft as necessary
                    val issueGroupId = if (apkSetMetadata.permissions.isNotEmpty()) {
                        val issueGroupId = ReviewIssueGroup.new {}.id
                        apkSetMetadata.permissions
                            .filter { PERMISSION_REVIEW_BLACKLIST.contains(it) }
                            .forEach {
                                ReviewIssue.new {
                                    reviewIssueGroupId = issueGroupId
                                    rawValue = it
                                }
                            }
                        issueGroupId
                    } else {
                        null
                    }

                    DraftDao.new {
                        this.label = label
                        appId = apkSetMetadata.appId
                        versionCode = apkSetMetadata.versionCode
                        versionName = apkSetMetadata.versionName
                        this.iconHash = iconHash
                        this.submitterId = submitterId
                        reviewIssueGroupId = issueGroupId
                    }.serializable()
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
    delete<Drafts.Id> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }

        val draft = transaction {
            DraftDao.find { DbDrafts.id eq draftId and (DbDrafts.submitterId eq userId) }
                .singleOrNull()
        }
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
        val userId = call.principal<Session>()!!.userId

        val drafts = transaction {
            DraftDao.find { DbDrafts.submitterId eq userId }.map { it.serializable() }
        }.toList()

        call.respond(drafts)
    }
}

fun Route.getDraftRoute() {
    get<Drafts.Id> {
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(it.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val draft = transaction {
            DraftDao.find { DbDrafts.id eq draftId and (DbDrafts.submitterId eq userId) }
                .singleOrNull()
        }?.serializable()
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(draft)
        }
    }
}

fun Route.updateDraftRoute() {
    patch<Drafts.Id> { route ->
        val userId = call.principal<Session>()!!.userId
        val request = call.receive<UpdateDraftRequest>()

        // We can only submit or approve the app, not both at once
        if (!(request.submitted xor request.approved)) {
            call.respond(HttpStatusCode.BadRequest)
            return@patch
        }

        val draftId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@patch
        }

        // Submit the draft
        if (request.submitted) {
            val draft = transaction {
                DraftDao.find { DbDrafts.id eq draftId and (DbDrafts.submitterId eq userId) }
                    .singleOrNull()
            }
            if (draft == null) {
                call.respond(HttpStatusCode.NotFound)
            } else if (draft.reviewerId != null) {
                // A reviewer is already assigned
                call.respond(HttpStatusCode.Forbidden)
            } else {
                // Submit the draft by assigning a random reviewer
                transaction {
                    draft.reviewerId = Reviewers
                        .slice(Reviewers.id)
                        .selectAll()
                        .orderBy(Random())
                        .limit(1)
                        .single()[Reviewers.id]
                }
                call.respond(draft.serializable())
            }
        } else if (request.approved) {
            val reviewer =
                transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
            if (reviewer == null) {
                // The user isn't the assigned reviewer
                call.respond(HttpStatusCode.Forbidden)
            } else {
                // Approve the draft
                val draft = transaction {
                    DraftDao.find { DbDrafts.id eq draftId and (DbDrafts.reviewerId eq reviewer.id) }
                        .singleOrNull()
                }
                if (draft == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    transaction { draft.approved = true }
                    call.respond(draft.serializable())
                }
            }
        }
    }
}
