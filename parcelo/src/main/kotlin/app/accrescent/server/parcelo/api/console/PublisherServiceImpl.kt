// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.CreatePublisherRequest
import app.accrescent.console.v1alpha1.CreatePublisherResponse
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.PublisherService
import app.accrescent.console.v1alpha1.createPublisherResponse
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.Publisher
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class PublisherServiceImpl @Inject constructor(
    val config: ParceloConfig,
    private val permissionService: PermissionService,
) : PublisherService {
    @Transactional
    override fun createPublisher(request: CreatePublisherRequest): Uni<CreatePublisherResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreatePublisher = permissionService.hasPermission(
            // PLATFORM is a singleton, so its resource ID is unused
            ObjectReference(ObjectType.PLATFORM, ""),
            Permission.CREATE_PUBLISHER,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canCreatePublisher) {
            val userExists = User.existsById(request.userId)
            val canViewUserExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.USER, request.userId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!userExists || !canViewUserExistence) {
                userNotFoundException(request.userId)
            } else {
                throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to create publisher",
                )
                    .toStatusRuntimeException()
            }
        }

        when {
            !User.existsById(request.userId) -> throw userNotFoundException(request.userId)
            Publisher.existsByUserId(request.userId) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_EXISTS,
                "specified user is already a publisher",
            )
                .toStatusRuntimeException()
        }

        // Because this API is restricted to administrators, we don't need to verify ownership of
        // the email address
        Publisher(userId = request.userId, email = request.email).persist()

        return Uni.createFrom().item { createPublisherResponse {} }
    }

    private companion object {
        private fun userNotFoundException(userId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "user with ID \"$userId\" not found",
        )
            .toStatusRuntimeException()
    }
}
