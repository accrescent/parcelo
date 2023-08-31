// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.Apps as DbApps
import app.accrescent.parcelo.console.data.Updates as DbUpdates
import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.console.Config
import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.AccessControlLists
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.RejectionReason
import app.accrescent.parcelo.console.data.Review
import app.accrescent.parcelo.console.data.ReviewIssue
import app.accrescent.parcelo.console.data.ReviewIssueGroup
import app.accrescent.parcelo.console.data.ReviewIssues
import app.accrescent.parcelo.console.data.Reviewer
import app.accrescent.parcelo.console.data.Reviewers
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.Update
import app.accrescent.parcelo.console.data.net.ApiError
import app.accrescent.parcelo.console.data.net.toApiError
import app.accrescent.parcelo.console.jobs.registerPublishUpdateJob
import app.accrescent.parcelo.console.storage.FileStorageService
import app.accrescent.parcelo.console.validation.MIN_BUNDLETOOL_VERSION
import app.accrescent.parcelo.console.validation.MIN_TARGET_SDK
import app.accrescent.parcelo.console.validation.REVIEW_ISSUE_BLACKLIST
import app.accrescent.parcelo.console.validation.ReviewRequest
import app.accrescent.parcelo.console.validation.ReviewResult
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
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.ktor.ext.inject
import java.util.UUID

@Resource("/updates")
class Updates {
    @Resource("{id}")
    class Id(val parent: Updates = Updates(), val id: String) {
        @Resource("review")
        class Review(val parent: Id)
    }
}

fun Route.updateRoutes() {
    authenticate("cookie") {
        createUpdateRoute()
        getUpdatesForAppRoute()
        updateUpdateRoute()

        createUpdateReviewRoute()
    }
}

fun Route.createUpdateRoute() {
    val config: Config by inject()
    val storageService: FileStorageService by inject()

    post<Apps.Id.Updates> { route ->
        val userId = call.principal<Session>()!!.userId
        val appId = route.parent.id

        val updatePermitted = transaction {
            AccessControlLists
                .slice(AccessControlLists.update)
                .select { AccessControlLists.userId eq userId and (AccessControlLists.appId eq appId) }
                .singleOrNull()
                ?.let { it[AccessControlLists.update] }
                ?: false
        }
        if (!updatePermitted) {
            call.respond(HttpStatusCode.Forbidden, ApiError.updateCreationForbidden())
            return@post
        }

        var apkSet: ApkSet? = null
        var apkSetData: ByteArray? = null
        for (part in call.receiveMultipart().readAllParts()) {
            if (part is PartData.FileItem && part.name == "apk_set") {
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
            } else {
                call.respond(HttpStatusCode.BadRequest, ApiError.unknownPartName(part.name))
                return@post
            }
        }
        if (apkSet == null) {
            call.respond(HttpStatusCode.BadRequest, ApiError.missingPartName())
            return@post
        }

        if (apkSet.appId.value != appId) {
            call.respond(HttpStatusCode.UnprocessableEntity)
            return@post
        }

        val app = transaction { App.findById(apkSet.appId.value) } ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        if (apkSet.versionCode <= app.versionCode) {
            call.respond(HttpStatusCode.UnprocessableEntity)
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

        val apkSetFileId = apkSetData!!.inputStream().use { storageService.saveFile(it) }

        // There exists:
        //
        // 1. The review issue blacklist
        // 2. The list of review issues the update contains
        // 3. The list of review issues the published app has been approved for
        //
        // Only updates adding review issues not previously approved should require review, and
        // then only for those review issues not previously approved. Therefore, all review issues
        // which exist in both (1) and (2) and do not exist in (3) should be stored with the update
        // for review. If there are none, we don't assign a reviewer.
        val update = transaction {
            REVIEW_ISSUE_BLACKLIST
                .intersect(apkSet.reviewIssues.toSet())
                .let { reviewIssues ->
                    if (app.reviewIssueGroupId != null) {
                        reviewIssues.subtract(ReviewIssue.find {
                            ReviewIssues.reviewIssueGroupId eq app.reviewIssueGroupId!!
                        }.map { it.rawValue }.toSet())
                    } else {
                        reviewIssues
                    }
                }
                .let { reviewIssues ->
                    var issueGroupId: EntityID<Int>? = null
                    if (reviewIssues.isNotEmpty()) {
                        issueGroupId = ReviewIssueGroup.new {}.id
                        reviewIssues.forEach {
                            ReviewIssue.new {
                                reviewIssueGroupId = issueGroupId
                                rawValue = it
                            }
                        }
                    }
                    Update.new {
                        this.appId = app.id
                        versionCode = apkSet.versionCode
                        versionName = apkSet.versionName
                        creatorId = userId
                        fileId = apkSetFileId
                        reviewIssueGroupId = issueGroupId
                    }
                }
        }.serializable()

        call.apply {
            response.header(
                HttpHeaders.Location,
                "${config.application.baseUrl}/api/v1/updates/${update.id}",
            )
            respond(HttpStatusCode.Created, update)
        }
    }
}

/**
 * Returns the updates for a given app. The user must have the "update" permission to view these.
 */
fun Route.getUpdatesForAppRoute() {
    get<Apps.Id.Updates> { route ->
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
        } else if (!acl.update) {
            call.respond(HttpStatusCode.Forbidden, ApiError.readForbidden())
            return@get
        }

        val updates =
            transaction { Update.find { DbUpdates.appId eq acl.appId }.map { it.serializable() } }

        call.respond(HttpStatusCode.OK, updates)
    }
}

fun Route.updateUpdateRoute() {
    patch<Updates.Id> { route ->
        val userId = call.principal<Session>()!!.userId
        val updateId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.id))
            return@patch
        }

        val appId = transaction { Update.findById(updateId)?.appId } ?: run {
            call.respond(HttpStatusCode.NotFound, ApiError.updateNotFound(updateId))
            return@patch
        }

        // Users can only submit an update they've created and which has a versionCode higher than
        // that of the published app
        val (statusCode, responseBody) = transaction {
            val publishedApp = App
                .find { DbApps.id eq appId }
                .forUpdate() // Lock to prevent race conditions on the version code
                .singleOrNull()
                ?: return@transaction Pair(HttpStatusCode.InternalServerError, null)
            val update = Update
                .find { DbUpdates.id eq updateId and (DbUpdates.creatorId eq userId) }
                .singleOrNull()
                ?: return@transaction Pair(
                    HttpStatusCode.NotFound,
                    ApiError.updateNotFound(updateId),
                )

            val requiresReview = update.reviewIssueGroupId != null
            if (update.versionCode <= publishedApp.versionCode) {
                Pair(
                    HttpStatusCode.Conflict,
                    ApiError.updateVersionTooLow(update.versionCode, publishedApp.versionCode),
                )
            } else if (requiresReview) {
                if (update.reviewerId != null) {
                    Pair(HttpStatusCode.Conflict, ApiError.reviewerAlreadyAssigned())
                } else {
                    update.submitted = true
                    update.reviewerId = Reviewers
                        .slice(Reviewers.id)
                        .selectAll()
                        .orderBy(Random())
                        .limit(1)
                        .single()[Reviewers.id]
                    Pair(HttpStatusCode.OK, update.serializable())
                }
            } else {
                BackgroundJob.enqueue { registerPublishUpdateJob(update.id.value) }
                Pair(HttpStatusCode.Accepted, null)
            }
        }

        if (responseBody != null) {
            call.respond(statusCode, responseBody)
        } else {
            call.respond(statusCode)
        }
    }
}

fun Route.createUpdateReviewRoute() {
    post<Updates.Id.Review> { route ->
        val userId = call.principal<Session>()!!.userId

        val updateId = try {
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
        // instead choose to find the update first, so we can return more specific status codes than
        // Not Found as appropriate if the user has sufficient access.
        val update = transaction { Update.findById(updateId) } ?: run {
            call.respond(HttpStatusCode.NotFound, ApiError.updateNotFound(updateId))
            return@post
        }

        val userCanReview =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
                ?.let { it.id == update.reviewerId }
                ?: false
        if (userCanReview) {
            // Check whether this update has already been reviewed
            if (update.reviewId != null) {
                call.respond(HttpStatusCode.Conflict, ApiError.alreadyReviewed())
                return@post
            }

            // Check whether this update increases the version code
            val publishedApp = transaction { App.findById(update.appId) } ?: run {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }
            if (update.versionCode <= publishedApp.versionCode) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiError.updateVersionTooLow(update.versionCode, publishedApp.versionCode),
                )
                return@post
            }

            // Create the review
            val review = transaction {
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
                update.reviewId = review.id
                review
            }

            // If approved, submit a publishing job for the update
            if (review.approved) {
                BackgroundJob.enqueue { registerPublishUpdateJob(update.id.value) }
            }
            call.respond(HttpStatusCode.Created, request)
        } else {
            // Check whether the user has read access to this update. If they do, tell them they're
            // not allowed to review the update. Otherwise, don't reveal the update exists.
            val userCanRead = update.creatorId == userId
            if (userCanRead) {
                call.respond(HttpStatusCode.Forbidden, ApiError.reviewForbidden())
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.updateNotFound(updateId))
            }
        }
    }
}
