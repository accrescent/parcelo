// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.GetSelfRequest
import app.accrescent.console.v1alpha1.GetSelfResponse
import app.accrescent.console.v1alpha1.UpdateUserRequest
import app.accrescent.console.v1alpha1.UpdateUserResponse
import app.accrescent.console.v1alpha1.UserRole
import app.accrescent.console.v1alpha1.UserService
import app.accrescent.console.v1alpha1.getSelfResponse
import app.accrescent.console.v1alpha1.updateUserResponse
import app.accrescent.console.v1alpha1.user
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.GrpcRequestValidationInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.PermissionService
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class UserServiceImpl @Inject constructor(
    private val permissionService: PermissionService,
) : UserService {
    @Transactional
    override fun getSelf(request: GetSelfRequest): Uni<GetSelfResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        // This should never throw a not found exception in practice
        val self = User.findById(userId) ?: throw userNotFoundException(userId)

        val response = getSelfResponse {
            user = user {
                id = self.id
                email = self.email
                if (self.reviewer) {
                    roles.add(UserRole.USER_ROLE_REVIEWER)
                }
                if (self.publisher) {
                    roles.add(UserRole.USER_ROLE_PUBLISHER)
                }
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateUser(request: UpdateUserRequest): Uni<UpdateUserResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canUpdateUser = permissionService
            .hasPermission(HasPermissionRequest.UpdateUser(request.userId, userId))
        if (!canUpdateUser) {
            val exists = User.existsById(request.userId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewUserExistence(request.userId, userId))

            throw if (!exists || !canViewExistence) {
                userNotFoundException(request.userId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to update user",
                )
                    .toStatusRuntimeException()
            }
        }

        val user = User.findById(request.userId) ?: throw userNotFoundException(request.userId)
        if (request.updateMask.pathsList.contains("roles")) {
            val canUpdateRoles = permissionService
                .hasPermission(HasPermissionRequest.UpdateUserRoles(request.userId, userId))
            if (!canUpdateRoles) {
                throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to update roles for user ${request.userId}",
                )
                    .toStatusRuntimeException()
            }

            user.reviewer = request.rolesList.contains(UserRole.USER_ROLE_REVIEWER)
            user.publisher = request.rolesList.contains(UserRole.USER_ROLE_PUBLISHER)
        }

        return Uni.createFrom().item { updateUserResponse {} }
    }

    private companion object {
        private fun userNotFoundException(userId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "user with ID $userId not found"
        )
            .toStatusRuntimeException()
    }
}
