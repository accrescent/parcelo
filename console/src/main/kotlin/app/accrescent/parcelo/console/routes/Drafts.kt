package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.Drafts as DbDrafts
import app.accrescent.parcelo.apksparser.ApkSetMetadata
import app.accrescent.parcelo.apksparser.InvalidApkSetException
import app.accrescent.parcelo.apksparser.parseApkSet
import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.RejectionReason
import app.accrescent.parcelo.console.data.Review
import app.accrescent.parcelo.console.data.ReviewIssue
import app.accrescent.parcelo.console.data.ReviewIssueGroup
import app.accrescent.parcelo.console.data.Reviewer
import app.accrescent.parcelo.console.data.Reviewers
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.validation.MIN_TARGET_SDK_NEW_APP
import app.accrescent.parcelo.console.validation.PERMISSION_REVIEW_BLACKLIST
import app.accrescent.parcelo.console.validation.SERVICE_INTENT_FILTER_REVIEW_BLACKLIST
import io.ktor.http.HttpHeaders
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
import io.ktor.server.resources.post
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.IIOException
import javax.imageio.ImageIO

@Resource("/drafts")
class Drafts {
    @Resource("{id}")
    class Id(val parent: Drafts = Drafts(), val id: String) {
        @Resource("review")
        class Review(val parent: Id)
    }
}

fun Route.draftRoutes() {
    authenticate("cookie") {
        createDraftRoute()
        deleteDraftRoute()
        getDraftsRoute()
        updateDraftRoute()

        createDraftReviewRoute()
    }
}

fun Route.createDraftRoute() {
    val config: Config by inject()
    val storageService: FileStorageService by inject()

    post<Drafts> {
        val creatorId = call.principal<Session>()!!.userId

        var apkSetMetadata: ApkSetMetadata? = null
        var label: String? = null
        var apkSetData: ByteArray? = null
        var iconHash: String? = null
        var iconData: ByteArray? = null

        val multipart = call.receiveMultipart().readAllParts()
        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                apkSetMetadata = try {
                    apkSetData = part.streamProvider().use { it.readBytes() }

                    apkSetData.inputStream().use { parseApkSet(it) }
                } catch (e: InvalidApkSetException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } finally {
                    part.dispose()
                }
            } else if (part is PartData.FileItem && part.name == "icon") {
                iconData = part.streamProvider().use { it.readBytes() }

                // Icon must be a 512 x 512 PNG
                val pngReader = ImageIO.getImageReadersByFormatName("PNG").next()
                val image = try {
                    iconData.inputStream().use { ImageIO.createImageInputStream(it) }.use {
                        pngReader.input = it
                        pngReader.read(0)
                    }
                } catch (e: IIOException) {
                    // Assume this is a format error
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
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
            } else {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
        }

        if (
            apkSetMetadata != null &&
            label != null &&
            iconHash != null &&
            iconData != null &&
            apkSetData != null
        ) {
            // Check that there isn't already a published app with this ID
            if (transaction { App.findById(apkSetMetadata.appId.value) } != null) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }

            if (apkSetMetadata.targetSdk < MIN_TARGET_SDK_NEW_APP) {
                call.respond(HttpStatusCode.UnprocessableEntity)
                return@post
            }

            val reviewIssues = PERMISSION_REVIEW_BLACKLIST
                .union(SERVICE_INTENT_FILTER_REVIEW_BLACKLIST)
                .intersect(apkSetMetadata.reviewIssues.toSet())
            val draft = transaction {
                // Associate review issues with draft as necessary
                val issueGroupId = if (reviewIssues.isNotEmpty()) {
                    val issueGroupId = ReviewIssueGroup.new {}.id
                    for (reviewIssue in reviewIssues) {
                        ReviewIssue.new {
                            reviewIssueGroupId = issueGroupId
                            rawValue = reviewIssue
                        }
                    }
                    issueGroupId
                } else {
                    null
                }

                val iconFileId = iconData.inputStream().use { storageService.saveFile(it) }
                val appFileId = apkSetData.inputStream().use { storageService.saveFile(it) }
                val icon = Icon.new {
                    hash = iconHash
                    fileId = iconFileId
                }
                Draft.new {
                    this.label = label
                    appId = apkSetMetadata.appId.value
                    versionCode = apkSetMetadata.versionCode
                    versionName = apkSetMetadata.versionName
                    this.creatorId = creatorId
                    creationTime = System.currentTimeMillis()
                    fileId = appFileId
                    iconId = icon.id
                    reviewIssueGroupId = issueGroupId
                }.serializable()
            }

            call.response.header(
                HttpHeaders.Location,
                "${config.baseUrl}/api/v1/drafts/${draft.id}"
            )
            call.respond(HttpStatusCode.Created, draft)
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
            Draft.find { DbDrafts.id eq draftId and (DbDrafts.creatorId eq userId) }
                .singleOrNull()
        }
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            transaction { draft.delete() }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.getDraftsRoute() {
    get<Drafts> {
        val userId = call.principal<Session>()!!.userId

        val drafts = transaction {
            Draft.find { DbDrafts.creatorId eq userId }.map { it.serializable() }
        }.toList()

        call.respond(drafts)
    }
}

fun Route.updateDraftRoute() {
    patch<Drafts.Id> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@patch
        }

        // Submit the draft
        val draft = transaction {
            Draft.find { DbDrafts.id eq draftId and (DbDrafts.creatorId eq userId) }.singleOrNull()
        }
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound)
        } else if (draft.reviewerId != null) {
            // A reviewer is already assigned
            call.respond(HttpStatusCode.Conflict)
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
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

@Serializable
private enum class ReviewResult {
    @SerialName("approved")
    APPROVED,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
private data class ReviewRequest(
    val result: ReviewResult,
    val reasons: List<String>?,
    @SerialName("additional_notes")
    val additionalNotes: String?,
) {
    // FIXME(#114): Handle this validation automatically via kotlinx.serialization instead
    init {
        if (
            (result == ReviewResult.APPROVED && reasons != null) ||
            (result == ReviewResult.REJECTED && reasons == null)
        ) {
            throw IllegalArgumentException()
        }
    }
}

fun Route.createDraftReviewRoute() {
    post<Drafts.Id.Review> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.parent.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val request = try {
            call.receive<ReviewRequest>()
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        // Normally we would include access control (in this case, the user's reviewer ID) in the
        // query to prevent accidentally leaking data to unauthorized users, but in this case we
        // instead choose to find the draft first, so we can return more specific status codes than
        // Not Found as appropriate if the user has sufficient access.
        val draft = transaction { Draft.findById(draftId) } ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }

        val userCanReview =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
                ?.let { it.id == draft.reviewerId }
                ?: false
        if (userCanReview) {
            // Check whether this draft has already been reviewed
            if (draft.reviewId != null) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }

            // Create the review
            transaction {
                val review = Review.new {
                    approved = when (request.result) {
                        ReviewResult.APPROVED -> true
                        ReviewResult.REJECTED -> false
                    }
                    additionalNotes = request.additionalNotes
                }
                for (rejectionReason in request.reasons.orEmpty()) {
                    RejectionReason.new {
                        reviewId = review.id
                        reason = rejectionReason
                    }
                }
                draft.reviewId = review.id
            }
            call.respond(HttpStatusCode.Created, request)
        } else {
            // Check whether the user has read access to this draft. If they do, tell them they're
            // not allowed to review the draft. Otherwise, don't reveal the draft exists.
            val userCanRead = draft.creatorId == userId
            if (userCanRead) {
                call.respond(HttpStatusCode.Forbidden)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
