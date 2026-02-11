// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.CreatePublisherRequest
import app.accrescent.console.v1alpha1.CreatePublisherResponse
import app.accrescent.console.v1alpha1.PublisherService
import app.accrescent.console.v1alpha1.createPublisherResponse
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
                throw Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to create reviewer")
                    .asRuntimeException()
            }
        }

        if (!User.existsById(request.userId)) {
            throw Status
                .NOT_FOUND
                .withDescription("user with ID \"${request.userId}\" not found")
                .asRuntimeException()
        }
        if (Publisher.existsByUserId(request.userId)) {
            throw Status
                .ALREADY_EXISTS
                .withDescription("specified user is already a publisher")
                .asRuntimeException()
        }

        // Because this API is restricted to administrators, we don't need to verify ownership of
        // the email address
        Publisher(userId = request.userId, email = request.email).persist()

        return Uni.createFrom().item { createPublisherResponse {} }
    }

    private companion object {
        private fun userNotFoundException(userId: String) = Status
            .NOT_FOUND
            .withDescription("user with ID \"$userId\" not found")
            .asRuntimeException()
    }
}
