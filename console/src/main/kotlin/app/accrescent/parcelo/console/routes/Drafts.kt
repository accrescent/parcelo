// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Drafts as DbDrafts
import app.accrescent.parcelo.console.data.Icon
import app.accrescent.parcelo.console.data.RejectionReason
import app.accrescent.parcelo.console.data.Review
import app.accrescent.parcelo.console.data.ReviewIssue
import app.accrescent.parcelo.console.data.ReviewIssueGroup
import app.accrescent.parcelo.console.data.Reviewer
import app.accrescent.parcelo.console.data.Reviewers
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.net.ApiError
import app.accrescent.parcelo.console.data.net.toApiError
import app.accrescent.parcelo.console.jobs.cleanFile
import app.accrescent.parcelo.console.storage.ObjectStorageService
import app.accrescent.parcelo.console.util.TempFile
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
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.ktor.ext.inject
import java.util.UUID
import javax.imageio.IIOException
import javax.imageio.ImageIO

@Resource("/drafts")
class Drafts {
    @Resource("approved")
    class Approved(val parent: Drafts)

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

        getApprovedDraftsRoute()
        getAssignedDraftsRoute()
        getDraftApkSetRoute()

        createDraftReviewRoute()
    }
}

fun Route.createDraftRoute() {
    val config: Config by inject()
    val storageService: ObjectStorageService by inject()

    post<Drafts> {
        val creatorId = call.principal<Session>()!!.userId

        var apkSet: ApkSet? = null
        var label: String? = null
        var shortDescription: String? = null
        var iconData: ByteArray? = null

        @Suppress("DEPRECATION_ERROR")
        val multipart = call.receiveMultipart().readAllParts()

        try {
            TempFile().use { tempApkSet ->
                for (part in multipart) {
                    when (part) {
                        is PartData.FileItem if part.name == "apk_set" -> {
                            val parseResult = run {
                                tempApkSet.outputStream().use { fileOutputStream ->
                                    part.streamProvider().use { it.copyTo(fileOutputStream) }
                                }
                                ApkSet.parse(tempApkSet.path.toFile())
                            }
                            apkSet = when (parseResult) {
                                is ParseApkSetResult.Ok -> parseResult.apkSet
                                is ParseApkSetResult.Error -> run {
                                    call.respond(HttpStatusCode.BadRequest, toApiError(parseResult))
                                    return@post
                                }
                            }
                        }

                        is PartData.FileItem if part.name == "icon" -> {
                            iconData = part.streamProvider().use { it.readBytes() }

                            // Icon must be a 512 x 512 PNG
                            val pngReader = ImageIO.getImageReadersByFormatName("PNG").next()
                            val image = try {
                                iconData.inputStream().use { ImageIO.createImageInputStream(it) }
                                    .use {
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
                        }

                        is PartData.FormItem if part.name == "label" -> {
                            // Label must be between 3 and 30 characters in length inclusive
                            if (part.value.length < 3 || part.value.length > 30) {
                                call.respond(HttpStatusCode.BadRequest, ApiError.labelLength())
                                return@post
                            } else {
                                label = part.value
                            }
                        }

                        is PartData.FormItem if part.name == "short_description" -> {
                            // Short description must be between 3 and 80 characters in length
                            // inclusive
                            if (part.value.length < 3 || part.value.length > 80) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiError.shortDescriptionLength()
                                )
                                return@post
                            } else {
                                shortDescription = part.value
                            }
                        }

                        else -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiError.unknownPartName(part.name)
                            )
                            return@post
                        }
                    }
                }

                if (
                    apkSet != null &&
                    label != null &&
                    shortDescription != null &&
                    iconData != null
                ) {
                    // Check that there isn't already a published app with this ID
                    if (transaction { App.findById(apkSet.metadata.packageName) } != null) {
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

                    val reviewIssues = REVIEW_ISSUE_BLACKLIST intersect apkSet.reviewIssues
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

                        val iconFileId = runBlocking { storageService.uploadBytes(iconData) }
                        val appFileId = runBlocking { storageService.uploadFile(tempApkSet.path) }
                        val icon = Icon.new { fileId = iconFileId }
                        Draft.new {
                            this.label = label
                            appId = apkSet.metadata.packageName
                            versionCode = apkSet.versionCode
                            versionName = apkSet.versionName
                            this.shortDescription = shortDescription
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
        } finally {
            multipart.forEach { it.dispose() }
        }
    }
}

fun Route.deleteDraftRoute() {
    val storageService: ObjectStorageService by inject()

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
        } else if (draft.reviewId != null) {
            // Forbid deletion if the draft has been reviewed
            call.respond(HttpStatusCode.Forbidden, ApiError.deleteForbidden())
        } else {
            val iconFileId = transaction {
                val icon = Icon.findById(draft.iconId)!!
                draft.delete()
                icon.delete()
                icon.fileId
            }
            storageService.markDeleted(draft.fileId.value)
            storageService.markDeleted(iconFileId.value)
            BackgroundJob.enqueue { cleanFile(draft.fileId.value) }
            BackgroundJob.enqueue { cleanFile(iconFileId.value) }

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
                    .select(Reviewers.id)
                    .orderBy(Random())
                    .limit(1)
                    .single()[Reviewers.id]
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Returns the list of approved new drafts ready for publishing. If the user is not a publisher,
 * this route returns a 403.
 */
fun Route.getApprovedDraftsRoute() {
    get<Drafts.Approved> {
        val userId = call.principal<Session>()!!.userId

        val userIsPublisher = transaction { User.findById(userId)?.publisher } ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        if (!userIsPublisher) {
            call.respond(HttpStatusCode.Forbidden, ApiError.readForbidden())
            return@get
        }

        val approvedDrafts = transaction {
            Draft
                .find { not(DbDrafts.publishing) }
                .filter { draft -> draft.reviewId?.let { Review.findById(it)?.approved } == true }
                .map { it.serializable() }
        }

        call.respond(HttpStatusCode.OK, approvedDrafts)
    }
}

/**
 * Returns the list of unreviewed drafts assigned to the current user for review. If the user is not
 * a reviewer, this route returns a 403.
 *
 * See also [getAssignedEditsRoute], [getAssignedUpdatesRoute]
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
 *
 * See also [getUpdateApkSetRoute].
 */
fun Route.getDraftApkSetRoute() {
    val storageService: ObjectStorageService by inject()

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
                ?.let { it.id == draft.reviewerId } == true

        if (userIsDraftReviewer) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "draft-${draft.appId}-${draft.versionCode}.apks",
                ).toString(),
            )
            call.respondOutputStream {
                storageService.loadObject(draft.fileId) { it.copyTo(this) }
            }
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
                ?.let { it.id == draft.reviewerId } == true
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
