// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.Drafts as DbDrafts
import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
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
import app.accrescent.parcelo.console.data.net.ApiError
import app.accrescent.parcelo.console.data.net.toApiError
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.validation.MIN_BUNDLETOOL_VERSION
import app.accrescent.parcelo.console.validation.MIN_TARGET_SDK
import app.accrescent.parcelo.console.validation.REVIEW_ISSUE_BLACKLIST
import app.accrescent.parcelo.console.validation.ReviewRequest
import app.accrescent.parcelo.console.validation.ReviewResult
import io.ktor.http.ContentDisposition
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
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.SortOrder
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
    @Resource("assigned")
    class Assigned(val parent: Drafts)

    @Resource("{id}")
    class Id(val parent: Drafts = Drafts(), val id: String) {
        @Resource("apkset")
        class ApkSet(val parent: Id)

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

        getAssignedDraftsRoute()
        getDraftApkSetRoute()

        createDraftReviewRoute()
    }
}

fun Route.createDraftRoute() {
    val config: Config by inject()
    val storageService: FileStorageService by inject()

    post<Drafts> {
        val creatorId = call.principal<Session>()!!.userId

        var apkSet: ApkSet? = null
        var label: String? = null
        var apkSetData: ByteArray? = null
        var iconHash: String? = null
        var iconData: ByteArray? = null

        val multipart = call.receiveMultipart().readAllParts()
        for (part in multipart) {
            when {
                part is PartData.FileItem && part.name == "apk_set" -> {
                    val parseResult = run {
                        apkSetData = part.streamProvider().use { it.readBytes() }
                        apkSetData!!.inputStream().use { ApkSet.parse(it) }
                    }
                    part.dispose()
                    apkSet = when (parseResult) {
                        is ParseApkSetResult.Ok -> parseResult.apkSet
                        is ParseApkSetResult.Error -> run {
                            call.respond(HttpStatusCode.BadRequest, toApiError(parseResult))
                            return@post
                        }
                    }
                }

                part is PartData.FileItem && part.name == "icon" -> {
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
                        call.respond(HttpStatusCode.BadRequest, ApiError.iconImageFormat())
                        return@post
                    }
                    if (image.width != 512 || image.height != 512) {
                        call.respond(HttpStatusCode.BadRequest, ApiError.imageResolution())
                        return@post
                    }

                    iconHash = MessageDigest
                        .getInstance("SHA-256")
                        .digest(iconData)
                        .joinToString("") { "%02x".format(it) }
                }

                part is PartData.FormItem && part.name == "label" -> {
                    // Label must be between 3 and 30 characters in length inclusive
                    if (part.value.length < 3 || part.value.length > 30) {
                        call.respond(HttpStatusCode.BadRequest, ApiError.labelLength())
                        return@post
                    } else {
                        label = part.value
                    }
                }

                else -> {
                    call.respond(HttpStatusCode.BadRequest, ApiError.unknownPartName(part.name))
                    return@post
                }
            }
        }

        if (
            apkSet != null &&
            label != null &&
            iconHash != null &&
            iconData != null &&
            apkSetData != null
        ) {
            // Check that there isn't already a published app with this ID
            if (transaction { App.findById(apkSet.appId.value) } != null) {
                call.respond(HttpStatusCode.Conflict, ApiError.appAlreadyExists())
                return@post
            }

            if (apkSet.targetSdk < MIN_TARGET_SDK) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError.minTargetSdk(MIN_TARGET_SDK, apkSet.targetSdk)
                )
                return@post
            }
            if (apkSet.bundletoolVersion < MIN_BUNDLETOOL_VERSION) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError.minBundletoolVersion(
                        MIN_BUNDLETOOL_VERSION.toString(),
                        apkSet.bundletoolVersion.toString()
                    )
                )
                return@post
            }

            val reviewIssues = REVIEW_ISSUE_BLACKLIST intersect apkSet.reviewIssues.toSet()
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
                val appFileId = apkSetData!!.inputStream().use { storageService.saveFile(it) }
                val icon = Icon.new {
                    hash = iconHash
                    fileId = iconFileId
                }
                Draft.new {
                    this.label = label
                    appId = apkSet.appId.value
                    versionCode = apkSet.versionCode
                    versionName = apkSet.versionName
                    this.creatorId = creatorId
                    fileId = appFileId
                    iconId = icon.id
                    reviewIssueGroupId = issueGroupId
                }.serializable()
            }

            call.response.header(
                HttpHeaders.Location,
                "${config.application.baseUrl}/api/v1/drafts/${draft.id}"
            )
            call.respond(HttpStatusCode.Created, draft)
        } else {
            call.respond(HttpStatusCode.BadRequest, ApiError.missingPartName())
        }
    }
}

fun Route.deleteDraftRoute() {
    val storageService: FileStorageService by inject()

    delete<Drafts.Id> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.id))
            return@delete
        }

        val draft = transaction {
            Draft.find { DbDrafts.id eq draftId and (DbDrafts.creatorId eq userId) }
                .singleOrNull()
        }
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
        } else {
            storageService.deleteFile(draft.fileId)
            storageService.deleteFile(draft.iconId)
            transaction { draft.delete() }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.getDraftsRoute() {
    get<Drafts> {
        val userId = call.principal<Session>()!!.userId

        val drafts = transaction {
            Draft
                .find { DbDrafts.creatorId eq userId }
                .orderBy(DbDrafts.creationTime to SortOrder.ASC)
                .map { it.serializable() }
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
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.id))
            return@patch
        }

        // Submit the draft
        val draft = transaction {
            Draft.find { DbDrafts.id eq draftId and (DbDrafts.creatorId eq userId) }.singleOrNull()
        }
        if (draft == null) {
            call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
        } else if (draft.reviewerId != null) {
            // A reviewer is already assigned
            call.respond(HttpStatusCode.Conflict, ApiError.reviewerAlreadyAssigned())
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

/**
 * Returns the list of unreviewed drafts assigned to the current user for review. If the user is not
 * a reviewer, this route returns a 403.
 */
fun Route.getAssignedDraftsRoute() {
    get<Drafts.Assigned> {
        val userId = call.principal<Session>()!!.userId

        val reviewer =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() } ?: run {
                call.respond(HttpStatusCode.Forbidden, ApiError.readForbidden())
                return@get
            }

        val assignedDrafts = transaction {
            Draft
                .find { DbDrafts.reviewerId eq reviewer.id and (DbDrafts.reviewId eq null) }
                .map { it.serializable() }
        }

        call.respond(HttpStatusCode.OK, assignedDrafts)
    }
}

/**
 * Returns the APK set for a given draft. This route is only accessible by the draft's reviewer.
 */
fun Route.getDraftApkSetRoute() {
    val storageService: FileStorageService by inject()

    get<Drafts.Id.ApkSet> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.parent.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.parent.id))
            return@get
        }

        // Normally we would include access control (in this case, the user's reviewer ID) in the
        // query to prevent accidentally leaking data to unauthorized users, but in this case we
        // instead choose to find the draft first, so we can return more specific status codes than
        // Not Found as appropriate if the user has sufficient access.
        val draft = transaction { Draft.findById(draftId) } ?: run {
            call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
            return@get
        }
        val userIsDraftReviewer =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
                ?.let { it.id == draft.reviewerId }
                ?: false

        if (userIsDraftReviewer) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "draft-${draft.appId}-${draft.versionCode}.apks",
                ).toString(),
            )
            call.respondBytes { storageService.loadFile(draft.fileId).use { it.readBytes() } }
        } else {
            // Check whether the user has read access to this draft. If they do, tell them they're
            // not allowed to download the APK set. Otherwise, don't reveal that the draft exists.
            val userCanRead = userId == draft.creatorId
            if (userCanRead) {
                call.respond(HttpStatusCode.Forbidden, ApiError.downloadForbidden())
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
            }
        }
    }
}

fun Route.createDraftReviewRoute() {
    post<Drafts.Id.Review> { route ->
        val userId = call.principal<Session>()!!.userId

        val draftId = try {
            UUID.fromString(route.parent.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.parent.id))
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
            call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
            return@post
        }

        val userCanReview =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
                ?.let { it.id == draft.reviewerId }
                ?: false
        if (userCanReview) {
            // Check whether this draft has already been reviewed
            if (draft.reviewId != null) {
                call.respond(HttpStatusCode.Conflict, ApiError.alreadyReviewed())
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
                call.respond(HttpStatusCode.Forbidden, ApiError.reviewForbidden())
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.draftNotFound(draftId))
            }
        }
    }
}
