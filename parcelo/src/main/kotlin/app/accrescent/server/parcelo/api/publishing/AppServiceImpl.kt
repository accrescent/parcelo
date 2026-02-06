// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.AppService
import app.accrescent.appstore.publish.v1alpha1.GetAppRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppResponse
import app.accrescent.appstore.publish.v1alpha1.ListAppsRequest
import app.accrescent.appstore.publish.v1alpha1.ListAppsResponse
import app.accrescent.appstore.publish.v1alpha1.app
import app.accrescent.appstore.publish.v1alpha1.getAppResponse
import app.accrescent.appstore.publish.v1alpha1.listAppsResponse
import app.accrescent.parcelo.impl.v1.ListAppsPageToken
import app.accrescent.parcelo.impl.v1.listAppsPageToken
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
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

        val canView = permissionService.hasPermission(
            ObjectReference(ObjectType.APP, request.appId),
            Permission.VIEW,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canView) {
            val exists = App.existsById(request.appId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP, request.appId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appNotFoundException(request.appId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to view app")
                    .asRuntimeException()
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

    private companion object {
        private val invalidPageTokenError = Status
            .INVALID_ARGUMENT
            .withDescription("provided page token is invalid")
            .asRuntimeException()

        private fun appNotFoundException(appId: String) = Status
            .NOT_FOUND
            .withDescription("app with ID \"$appId\" not found")
            .asRuntimeException()
    }
}
