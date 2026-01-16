// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftReviewRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftReviewResponse
import app.accrescent.appstore.publish.v1alpha1.ReviewService
import app.accrescent.appstore.publish.v1alpha1.createAppDraftReviewResponse
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.Publisher
import app.accrescent.server.parcelo.data.RejectionReason
import app.accrescent.server.parcelo.data.Review
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.quarkus.mailer.MailTemplate
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import java.util.UUID

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class ReviewServiceImpl : ReviewService {
    @JvmRecord
    data class AppDraftAssignedToYouForPublishingEmail(
        val appDraftId: String,
    ) : MailTemplate.MailTemplateInstance

    @Transactional
    override fun createAppDraftReview(
        request: CreateAppDraftReviewRequest,
    ): Uni<CreateAppDraftReviewResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val appDraft = AppDraft.findById(request.appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId, request.appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"${request.appDraftId}\" not found")
                .asRuntimeException()
        }
        val canReview = PermissionService.userCanReviewAppDraft(userId, request.appDraftId)
        if (!canReview) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to review app draft")
                .asRuntimeException()
        }
        if (appDraft.reviewId != null) {
            throw Status
                .ALREADY_EXISTS
                .withDescription("app draft has already been reviewed")
                .asRuntimeException()
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
        val publisher = Publisher.findRandom() ?: throw Status
            .FAILED_PRECONDITION
            .withDescription("no publishers available to assign")
            .asRuntimeException()
        val existingAcl = AppDraftAcl.findByAppDraftIdAndUserId(request.appDraftId, publisher.userId)
        if (existingAcl == null) {
            AppDraftAcl(
                appDraftId = request.appDraftId,
                userId = publisher.userId,
                canDelete = false,
                canEditListings = false,
                canPublish = true,
                canReplacePackage = false,
                canReview = false,
                canSubmit = false,
                canView = false,
                canViewExistence = true,
            )
                .persist()
        } else {
            existingAcl.canPublish = true
            existingAcl.canViewExistence = true
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

        return Uni.createFrom().item { createAppDraftReviewResponse {} }
    }
}
