// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.CreateReviewerRequest
import app.accrescent.appstore.publish.v1alpha1.CreateReviewerResponse
import app.accrescent.appstore.publish.v1alpha1.ReviewerService
import app.accrescent.appstore.publish.v1alpha1.createReviewerResponse
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.Reviewer
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class ReviewerServiceImpl @Inject constructor(val config: ParceloConfig) : ReviewerService {
    @Transactional
    override fun createReviewer(request: CreateReviewerRequest): Uni<CreateReviewerResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val authenticatedUser = User
            .findById(userId)
            ?: throw Status.UNAUTHENTICATED.asRuntimeException()
        val canCreateReviewer = config.admin().identityProvider() == authenticatedUser.identityProvider
                && config.admin().scopedUserId() == authenticatedUser.scopedUserId
        if (!canCreateReviewer) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to create reviewers")
                .asRuntimeException()
        }
        if (!User.existsById(request.userId)) {
            throw Status
                .NOT_FOUND
                .withDescription("user with ID \"${request.userId}\" not found")
                .asRuntimeException()
        }
        if (Reviewer.existsByUserId(request.userId)) {
            throw Status
                .ALREADY_EXISTS
                .withDescription("specified user is already a reviewer")
                .asRuntimeException()
        }

        // Because this API is restricted to administrators, we don't need to verify ownership of
        // the email address
        Reviewer(userId = request.userId, email = request.email).persist()

        return Uni.createFrom().item { createReviewerResponse {} }
    }
}
