// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.CreateAppDraftReviewRequest
import app.accrescent.console.v1alpha1.CreateAppDraftReviewResponse
import app.accrescent.console.v1alpha1.CreateAppEditReviewRequest
import app.accrescent.console.v1alpha1.CreateAppEditReviewResponse
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.ReviewService
import app.accrescent.console.v1alpha1.createAppDraftReviewResponse
import app.accrescent.console.v1alpha1.createAppEditReviewResponse
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftRelationshipSet
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.data.RejectionReason
import app.accrescent.server.parcelo.data.Review
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.jobs.JobDataKey
import app.accrescent.server.parcelo.jobs.PublishAppEditJob
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.GrpcRequestValidationInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import app.accrescent.server.parcelo.security.PermissionService
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.quarkus.mailer.MailTemplate
import io.smallrye.mutiny.Uni
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import java.time.OffsetDateTime
import java.util.UUID

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class ReviewServiceImpl @Inject constructor(
    private val permissionService: PermissionService,
    private val scheduler: Instance<Scheduler>,
) : ReviewService {
    @JvmRecord
    data class AppDraftAssignedToYouForPublishingEmail(
        val appDraftId: String,
    ) : MailTemplate.MailTemplateInstance

    @JvmRecord
    data class AppDraftReviewedEmail(
        val appDraftId: String,
        val approved: Boolean,
    ) : MailTemplate.MailTemplateInstance

    @JvmRecord
    data class AppEditReviewedEmail(
        val appEditId: String,
        val approved: Boolean,
    ) : MailTemplate.MailTemplateInstance

    @Transactional
    override fun createAppDraftReview(
        request: CreateAppDraftReviewRequest,
    ): Uni<CreateAppDraftReviewResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReview = permissionService
            .hasPermission(HasPermissionRequest.ReviewAppDraft(request.appDraftId, userId))
        if (!canReview) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                "insufficient permission to review app draft",
            )
                .toStatusRuntimeException()
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.reviewId != null) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_EXISTS,
                "app draft has already been reviewed",
            )
                .toStatusRuntimeException()
        }

        // Save the review.
        //
        // We don't need to check if the app draft is submitted first since 1) the server won't give
        // someone review access to the draft if it hasn't been submitted and 2) database
        // constraints ensure a non-submitted draft can't be reviewed even if there's a bug that
        // makes (1) false.
        val review = Review(id = UUID.randomUUID(), approved = request.approved)
            .also { it.persist() }
        for (rejectionReason in request.rejectionReasonsList) {
            RejectionReason(reviewId = review.id, reason = rejectionReason.reason).persist()
        }
        appDraft.reviewId = review.id

        // Assign a publisher
        val publisher = User.findRandomPublisher() ?: throw ConsoleApiError(
            ErrorReason.ERROR_REASON_ASSIGNEE_UNAVAILABLE,
            "no publishers available to assign",
        )
            .toStatusRuntimeException()
        val existingRelationshipSet = AppDraftRelationshipSet
            .findByAppDraftIdAndUserId(request.appDraftId, publisher.id)
        if (existingRelationshipSet == null) {
            AppDraftRelationshipSet(
                appDraftId = request.appDraftId,
                userId = publisher.id,
                reviewer = false,
                publisher = true,
            )
                .persist()
        } else {
            existingRelationshipSet.publisher = true
        }

        // Notify the publisher that they are assigned to this draft before the transaction is
        // committed. This approach means publishers may receive notifications for drafts they
        // aren't actually assigned to if the transaction is rolled back. However, it also means we
        // will always send a notification for drafts they are actually assigned to, which we want
        // to guarantee to ascertain timely publishing.
        AppDraftAssignedToYouForPublishingEmail(appDraft.id)
            .to(publisher.email)
            .subject("A new app draft has been assigned to you")
            .sendAndAwait()

        // Notify the org owners of the review result
        for (owner in User.findOwnersByAppDraftId(request.appDraftId)) {
            AppDraftReviewedEmail(appDraft.id, review.approved)
                .to(owner.email)
                .subject("Your app draft has been ${if (review.approved) "approved" else "rejected"}")
                .sendAndAwait()
        }

        return Uni.createFrom().item { createAppDraftReviewResponse {} }
    }

    @Transactional
    override fun createAppEditReview(
        request: CreateAppEditReviewRequest,
    ): Uni<CreateAppEditReviewResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReview = permissionService
            .hasPermission(HasPermissionRequest.ReviewAppEdit(request.appEditId, userId))
        if (!canReview) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                "insufficient permission to review app edit",
            )
                .toStatusRuntimeException()
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        if (appEdit.reviewId != null) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_EXISTS,
                "app edit has already been reviewed",
            )
                .toStatusRuntimeException()
        }

        // Save the review.
        //
        // We don't need to check if the app edit is submitted first since 1) the server won't give
        // someone review access to the edit if it hasn't been submitted and 2) database constraints
        // ensure a non-submitted edit can't be reviewed even if there's a bug that makes (1) false.
        val review = Review(id = UUID.randomUUID(), approved = request.approved)
            .also { it.persist() }
        for (rejectionReason in request.rejectionReasonsList) {
            RejectionReason(reviewId = review.id, reason = rejectionReason.reason).persist()
        }
        appEdit.reviewId = review.id

        // Notify the org owners of the review result
        for (owner in User.findOwnersByAppEditId(request.appEditId)) {
            AppEditReviewedEmail(appEdit.id, review.approved)
                .to(owner.email)
                .subject("Your app edit has been ${if (review.approved) "approved" else "rejected"}")
                .sendAndAwait()
        }

        // Publish the edit immediately if the review is an approval
        if (request.approved) {
            val job = JobBuilder
                .newJob(PublishAppEditJob::class.java)
                .withIdentity(JobKey.jobKey(Identifier.generateNew(IdType.OPERATION)))
                .withDescription("Publish app edit \"${request.appEditId}\"")
                .usingJobData(JobDataKey.APP_EDIT_ID, request.appEditId)
                .requestRecovery()
                .storeDurably()
                .build()
            val trigger = TriggerBuilder.newTrigger().startNow().build()
            scheduler.get().scheduleJob(job, trigger)

            BackgroundOperation(
                id = job.key.name,
                type = BackgroundOperationType.PUBLISH_APP_EDIT,
                parentId = appEdit.id,
                createdAt = OffsetDateTime.now(),
                result = null,
                succeeded = false,
            )
                .persist()
            appEdit.publishing = true
        }

        return Uni.createFrom().item { createAppEditReviewResponse {} }
    }

    private companion object {
        private fun appDraftNotFoundException(appDraftId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app draft \"$appDraftId\" not found",
        )
            .toStatusRuntimeException()

        private fun appEditNotFoundException(appEditId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app edit with ID \"$appEditId\" not found",
        )
            .toStatusRuntimeException()
    }
}
