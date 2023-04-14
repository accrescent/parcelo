package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.AccessControlLists
import app.accrescent.parcelo.data.App
import app.accrescent.parcelo.data.ReviewIssue
import app.accrescent.parcelo.data.ReviewIssueGroup
import app.accrescent.parcelo.data.ReviewIssues
import app.accrescent.parcelo.data.Reviewers
import app.accrescent.parcelo.data.Session
import app.accrescent.parcelo.data.Update
import app.accrescent.parcelo.validation.ApkSetMetadata
import app.accrescent.parcelo.validation.InvalidApkSetException
import app.accrescent.parcelo.validation.PERMISSION_REVIEW_BLACKLIST
import app.accrescent.parcelo.validation.parseApkSet
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.updateRoutes() {
    authenticate("cookie") {
        createUpdateRoute()
    }
}

fun Route.createUpdateRoute() {
    post("/apps/{app_id}/updates") {
        val userId = call.principal<Session>()!!.userId
        val appId = call.parameters["app_id"]!!

        val updatePermitted = transaction {
            AccessControlLists
                .slice(AccessControlLists.update)
                .select { AccessControlLists.userId eq userId and (AccessControlLists.appId eq appId) }
                .singleOrNull()
                ?.let { it[AccessControlLists.update] }
                ?: false
        }
        if (!updatePermitted) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        var apkSetMetadata: ApkSetMetadata? = null
        for (part in call.receiveMultipart().readAllParts()) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                apkSetMetadata = try {
                    part.streamProvider().use { parseApkSet(it) }
                } catch (e: InvalidApkSetException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } finally {
                    part.dispose()
                }
            }
        }
        if (apkSetMetadata == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        if (apkSetMetadata.appId != appId) {
            call.respond(HttpStatusCode.UnprocessableEntity)
            return@post
        }

        val app = transaction { App.findById(apkSetMetadata.appId) } ?: run {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        if (apkSetMetadata.versionCode <= app.versionCode) {
            call.respond(HttpStatusCode.UnprocessableEntity)
            return@post
        }

        // There exists:
        //
        // 1. The permission review blacklist
        // 2. The list of permissions the update is requesting
        // 3. The list of permissions the published app has been approved for
        //
        // Only updates requesting permissions not previously approved should require review, and
        // then only for those permissions not previously approved. Therefore, all permissions which
        // exist in both (1) and (2) and do not exist in (3) should be stored with the update for
        // review. If there are none, we don't assign a reviewer.
        val update = transaction {
            PERMISSION_REVIEW_BLACKLIST
                .intersect(apkSetMetadata.permissions.toSet())
                .let { permissions ->
                    if (app.reviewIssueGroupId != null) {
                        permissions.subtract(ReviewIssue.find {
                            ReviewIssues.reviewIssueGroupId eq app.reviewIssueGroupId!!
                        }.map { it.rawValue }.toSet())
                    } else {
                        permissions
                    }
                }
                .let { permissions ->
                    var issueGroupId: EntityID<Int>? = null
                    if (permissions.isNotEmpty()) {
                        issueGroupId = ReviewIssueGroup.new {}.id
                        permissions.forEach {
                            ReviewIssue.new {
                                reviewIssueGroupId = issueGroupId
                                rawValue = it
                            }
                        }
                    }
                    Update.new {
                        this.appId = app.id
                        versionCode = apkSetMetadata.versionCode
                        submitterId = userId
                        if (issueGroupId != null) {
                            reviewerId = Reviewers
                                .slice(Reviewers.id)
                                .selectAll()
                                .orderBy(Random())
                                .limit(1)
                                .single()[Reviewers.id]
                        }
                        reviewIssueGroupId = issueGroupId
                    }
                }
        }.serializable()

        call.respond(update)
    }
}
