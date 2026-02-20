// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.AppService
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.GetAppRequest
import app.accrescent.console.v1alpha1.GetAppResponse
import app.accrescent.console.v1alpha1.ListAppsRequest
import app.accrescent.console.v1alpha1.ListAppsResponse
import app.accrescent.console.v1alpha1.UpdateAppRequest
import app.accrescent.console.v1alpha1.UpdateAppResponse
import app.accrescent.console.v1alpha1.app
import app.accrescent.console.v1alpha1.getAppResponse
import app.accrescent.console.v1alpha1.listAppsResponse
import app.accrescent.console.v1alpha1.updateAppResponse
import app.accrescent.parcelo.impl.v1.ListAppsPageToken
import app.accrescent.parcelo.impl.v1.listAppsPageToken
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.protobuf.InvalidProtocolBufferException
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlin.io.encoding.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 50u

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppServiceImpl @Inject constructor(
    private val permissionService: PermissionService,
) : AppService {
    @Transactional
    override fun getApp(request: GetAppRequest): Uni<GetAppResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService
            .hasPermission(HasPermissionRequest.ViewApp(request.appId, userId))
        if (!canView) {
            val exists = App.existsById(request.appId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppExistence(request.appId, userId))

            throw if (!exists || !canViewExistence) {
                appNotFoundException(request.appId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to view app",
                )
                    .toStatusRuntimeException()
            }
        }

        val app = App.findById(request.appId) ?: throw appNotFoundException(request.appId)
        val response = getAppResponse {
            this.app = app {
                id = app.id
                defaultListingLanguage = app.defaultListingLanguage
                publiclyListed = true
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun listApps(request: ListAppsRequest): Uni<ListAppsResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val lastAppId = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val pageToken = ListAppsPageToken.parseFrom(tokenBytes)
                if (!pageToken.hasLastAppId()) {
                    throw invalidPageTokenError
                }

                pageToken.lastAppId
            } catch (_: IllegalArgumentException) {
                throw invalidPageTokenError
            } catch (_: InvalidProtocolBufferException) {
                throw invalidPageTokenError
            }
        } else {
            null
        }

        val apps = App.findForUserByQuery(userId, pageSize, lastAppId).map { app ->
            app {
                id = app.id
                defaultListingLanguage = app.defaultListingLanguage
                publiclyListed = true
            }
        }

        val response = if (apps.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listAppsPageToken { this.lastAppId = apps.last().id }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listAppsResponse {
                this.apps.addAll(apps)
                nextPageToken = encodedPageToken
            }
        } else {
            listAppsResponse {}
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateApp(request: UpdateAppRequest): Uni<UpdateAppResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canUpdate = permissionService
            .hasPermission(HasPermissionRequest.UpdateApp(request.appId, userId))
        if (!canUpdate) {
            val exists = App.existsById(request.appId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppExistence(request.appId, userId))

            throw if (!exists || !canViewExistence) {
                appNotFoundException(request.appId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to modify app",
                )
                    .toStatusRuntimeException()
            }
        }

        // Update the app based on the update mask
        val app = App.findById(request.appId) ?: throw appNotFoundException(request.appId)
        if (request.updateMask.pathsList.contains("publicly_listed")) {
            app.publiclyListed = request.publiclyListed
        }

        return Uni.createFrom().item { updateAppResponse {} }
    }

    private companion object {
        private val invalidPageTokenError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INVALID_REQUEST,
            "provided page token is invalid",
        )
            .toStatusRuntimeException()

        private fun appNotFoundException(appId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app with ID \"$appId\" not found",
        )
            .toStatusRuntimeException()
    }
}
