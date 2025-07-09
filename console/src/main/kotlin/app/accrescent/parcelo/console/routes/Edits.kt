// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.AccessControlLists
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Edit
import app.accrescent.parcelo.console.data.Edits as DbEdits
import app.accrescent.parcelo.console.data.RejectionReason
import app.accrescent.parcelo.console.data.Review
import app.accrescent.parcelo.console.data.Reviewer
import app.accrescent.parcelo.console.data.Reviewers
import app.accrescent.parcelo.console.data.Reviews
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.net.ApiError
import app.accrescent.parcelo.console.jobs.publishEdit
import app.accrescent.parcelo.console.validation.ReviewRequest
import app.accrescent.parcelo.console.validation.ReviewResult
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import java.util.UUID

@Resource("/edits")
class Edits {
    @Resource("assigned")
    class Assigned(val parent: Edits)

    @Resource("{id}")
    class Id(val parent: Edits, val id: String) {
        @Resource("review")
        class Review(val parent: Id)
    }
}

fun Route.editRoutes() {
    authenticate("cookie") {
        createEditRoute()
        getEditsForAppRoute()
        updateEditRoute()
        deleteEditRoute()

        getAssignedEditsRoute()

        createEditReviewRoute()
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

        @Suppress("DEPRECATION_ERROR")
        val multipart = call.receiveMultipart().readAllParts()

        try {
            for (part in multipart) {
                if (part is PartData.FormItem && part.name == "short_description") {
                    // Short description must be between 3 and 80 characters in length inclusive
                    if (part.value.length < 3 || part.value.length > 80) {
                        call.respond(HttpStatusCode.BadRequest, ApiError.shortDescriptionLength())
                        return@post
                    } else {
                        shortDescription = part.value
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiError.unknownPartName(part.name))
                    return@post
                }
            }
        } finally {
            multipart.forEach { it.dispose() }
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
                .find { DbEdits.appId eq acl.appId }
                .orderBy(DbEdits.creationTime to SortOrder.ASC)
                .map { it.serializable() }
        }

        call.respond(HttpStatusCode.OK, edits)
    }
}

/**
 * Submits the given edit for review. The user must have the "editMetadata" permission for the
 * corresponding app to do this.
 *
 * For a submission to succeed, there must also be no other edits associated with the same app which
 * have been submitted and are neither published nor rejected.
 */
fun Route.updateEditRoute() {
    patch<Edits.Id> { route ->
        val userId = call.principal<Session>()!!.userId
        val editId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.id))
            return@patch
        }

        val edit = transaction { Edit.findById(editId) } ?: run {
            call.respond(HttpStatusCode.NotFound, ApiError.editNotFound(editId))
            return@patch
        }

        // Ensure the user has the editMetadata permission for this app
        val acl = transaction {
            AccessControlList
                .find { AccessControlLists.appId eq edit.appId and (AccessControlLists.userId eq userId) }
                .singleOrNull()
        }
        if (acl == null || !acl.editMetadata) {
            call.respond(HttpStatusCode.NotFound, ApiError.editNotFound(editId))
            return@patch
        } else if (edit.reviewerId != null) {
            call.respond(HttpStatusCode.Conflict, ApiError.reviewerAlreadyAssigned())
            return@patch
        }

        // Submit the edit, assigning a random reviewer. If another edit associated with the same
        // app has already been submitted and is neither published nor rejected, report the
        // conflict.
        val (httpStatusCode, httpBody) = transaction {
            val submissionAlreadyExists = !Reviews
                .innerJoin(DbEdits)
                .selectAll()
                .where {
                    DbEdits.reviewerId.isNotNull()
                        .and(not(DbEdits.published))
                        .and(Reviews.approved)
                }
                .empty()

            if (submissionAlreadyExists) {
                Pair(HttpStatusCode.Conflict, ApiError.submissionConflict())
            } else {
                edit.reviewerId = Reviewers
                    .select(Reviewers.id)
                    .orderBy(Random())
                    .limit(1)
                    .single()[Reviewers.id]
                Pair(HttpStatusCode.NoContent, null)
            }
        }

        if (httpBody != null) {
            call.respond(httpStatusCode, httpBody)
        } else {
            call.respond(httpStatusCode)
        }
    }
}

/**
 * Deletes the edit with the given ID. The user deleting the update must have the "editMetadata"
 * permission for the associated app.
 */
fun Route.deleteEditRoute() {
    delete<Edits.Id> { route ->
        val userId = call.principal<Session>()!!.userId
        val editId = try {
            UUID.fromString(route.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ApiError.invalidUuid(route.id))
            return@delete
        }

        val edit = transaction {
            Edit
                .findById(editId)
                ?.takeIf { edit ->
                    AccessControlList.find {
                        AccessControlLists.appId.eq(edit.appId)
                            .and(AccessControlLists.userId eq userId)
                            .and(AccessControlLists.editMetadata)
                    }.singleOrNull() != null
                }
        }
        if (edit == null) {
            // Either the update doesn't exist or the user doesn't have the "editMetadata"
            // permission for the associated app.
            call.respond(HttpStatusCode.NotFound, ApiError.editNotFound(editId))
        } else {
            // If the edit is not yet reviewed, then delete it. Otherwise, inform the user that they
            // don't have sufficient permissions to delete the edit.
            //
            // Note that all edits pass through review (unlike updates), so there is no need to
            // check whether the edit is publishing or published since the edit must be reviewed
            // before it can reach those states.
            if (edit.reviewId == null) {
                transaction { edit.delete() }
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.Forbidden, ApiError.deleteForbidden())
            }
        }
    }
}

/**
 * Returns the list of unreviewed edits assigned to the current user for review. If the user is not
 * a reviewer, this route returns a 403.
 *
 * See also [getAssignedDraftsRoute], [getAssignedUpdatesRoute]
 */
fun Route.getAssignedEditsRoute() {
    get<Edits.Assigned> {
        val userId = call.principal<Session>()!!.userId

        val reviewer =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() } ?: run {
                call.respond(HttpStatusCode.Forbidden, ApiError.readForbidden())
                return@get
            }

        val assignedEdits = transaction {
            Edit
                .find { DbEdits.reviewerId eq reviewer.id and (DbEdits.reviewId eq null) }
                .map { it.serializable() }
        }

        call.respond(HttpStatusCode.OK, assignedEdits)
    }
}

/**
 * Creates a review for the given edit.
 */
fun Route.createEditReviewRoute() {
    post<Edits.Id.Review> { route ->
        val userId = call.principal<Session>()!!.userId

        val editId = try {
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

        val edit = transaction { Edit.findById(editId) } ?: run {
            call.respond(HttpStatusCode.NotFound, ApiError.editNotFound(editId))
            return@post
        }

        val userCanReview =
            transaction { Reviewer.find { Reviewers.userId eq userId }.singleOrNull() }
                ?.let { it.id == edit.reviewerId } == true
        if (userCanReview) {
            // Check whether this update has already been reviewed
            if (edit.reviewId != null) {
                call.respond(HttpStatusCode.Conflict, ApiError.alreadyReviewed())
                return@post
            }

            // Check whether the app is currently updating to prevent conflicts
            val publishedApp = transaction { App.findById(edit.appId) } ?: run {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }
            if (request.result == ReviewResult.APPROVED && publishedApp.updating) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiError.alreadyUpdating(publishedApp.id.value),
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
                edit.reviewId = review.id
                review
            }

            // If approved, submit a publishing job for the edit
            if (review.approved) {
                transaction { publishedApp.updating = true }
                BackgroundJob.enqueue { publishEdit(edit.id.value) }
            }

            call.respond(HttpStatusCode.Created, request)
        } else {
            // Check whether the user has read access to this edit. If they do, tell them they're
            // not allowed to review the edit. Otherwise, don't reveal the edit exists.
            val userCanRead = transaction {
                AccessControlList
                    .find {
                        AccessControlLists.appId.eq(edit.appId)
                            .and(AccessControlLists.userId eq userId)
                    }
                    .singleOrNull()
                    ?.editMetadata == true
            }
            if (userCanRead) {
                call.respond(HttpStatusCode.Forbidden, ApiError.reviewForbidden())
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.editNotFound(editId))
            }
        }
    }
}
