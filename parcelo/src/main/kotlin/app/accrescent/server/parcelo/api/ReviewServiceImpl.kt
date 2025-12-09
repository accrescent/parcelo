// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftReviewRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftReviewResponse
import app.accrescent.appstore.publish.v1alpha1.ReviewService
import app.accrescent.appstore.publish.v1alpha1.createAppDraftReviewResponse
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.RejectionReason
import app.accrescent.server.parcelo.data.Review
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import java.util.UUID

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
class ReviewServiceImpl : ReviewService {
    @Transactional
    override fun createAppDraftReview(
        request: CreateAppDraftReviewRequest,
    ): Uni<CreateAppDraftReviewResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        // Review permission implies permission to view certain details of the app draft and the
        // "view" permission implies permission to view as a user, not a reviewer, so we don't check
        // the "view" permission for reviewers here
        val appDraft = AppDraft.findById(appDraftId)
        val canReview = PermissionService.userCanReviewAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canReview || appDraft == null) {
            val canView = PermissionService.userCanViewAppDraft(userId = userId, appDraftId = appDraftId)
            if (canView) {
                throw Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to review app draft")
                    .asRuntimeException()
            } else {
                throw Status
                    .NOT_FOUND
                    .withDescription("app draft \"$appDraftId\" not found")
                    .asRuntimeException()
            }
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

        return Uni.createFrom().item { createAppDraftReviewResponse {} }
    }
}
