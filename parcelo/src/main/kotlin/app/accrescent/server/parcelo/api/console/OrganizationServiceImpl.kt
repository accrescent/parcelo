// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1.ErrorReason
import app.accrescent.console.v1.GetOrganizationRequest
import app.accrescent.console.v1.GetOrganizationResponse
import app.accrescent.console.v1.ListOrganizationsRequest
import app.accrescent.console.v1.ListOrganizationsResponse
import app.accrescent.console.v1.OrganizationService
import app.accrescent.console.v1.getOrganizationResponse
import app.accrescent.console.v1.listOrganizationsResponse
import app.accrescent.console.v1.organization
import app.accrescent.parcelo.impl.v1.ListOrganizationsPageToken
import app.accrescent.parcelo.impl.v1.listOrganizationsPageToken
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.GrpcRequestValidationInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.PermissionService
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
class OrganizationServiceImpl @Inject constructor(
    private val permissionService: PermissionService,
) : OrganizationService {
    @Transactional
    override fun getOrganization(request: GetOrganizationRequest): Uni<GetOrganizationResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService
            .hasPermission(HasPermissionRequest.ViewOrganization(request.organizationId, userId))
        if (!canView) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                "insufficient permission to view organization",
            )
                .toStatusRuntimeException()
        }

        val organization = Organization
            .findById(request.organizationId)
            ?: throw organizationNotFoundException(request.organizationId)
        val response = getOrganizationResponse {
            this.organization = organization {
                id = organization.id
                publishedAppLimit = organization.publishedAppLimit
                publishedAppCount = App.countInOrganization(organization.id).toInt()
                activeAppDraftLimit = organization.activeAppDraftLimit
                activeAppDraftCount = AppDraft.countActiveInOrganization(organization.id).toInt()
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun listOrganizations(
        request: ListOrganizationsRequest,
    ): Uni<ListOrganizationsResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val lastOrganizationId = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val token = ListOrganizationsPageToken.parseFrom(tokenBytes)
                if (!token.hasLastOrganizationId()) {
                    throw invalidPageTokenError
                }

                token.lastOrganizationId
            } catch (_: IllegalArgumentException) {
                throw invalidPageTokenError
            } catch (_: InvalidProtocolBufferException) {
                throw invalidPageTokenError
            }
        } else {
            null
        }

        val organizations = Organization
            .findForUserByQuery(userId, pageSize, lastOrganizationId)
            .map { organization ->
                organization {
                    id = organization.id
                    publishedAppLimit = organization.publishedAppLimit
                    publishedAppCount = App.countInOrganization(organization.id).toInt()
                    activeAppDraftLimit = organization.activeAppDraftLimit
                    activeAppDraftCount = AppDraft.countActiveInOrganization(organization.id).toInt()
                }
            }

        val response = if (organizations.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listOrganizationsPageToken {
                this.lastOrganizationId = organizations.last().id
            }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listOrganizationsResponse {
                this.organizations.addAll(organizations)
                nextPageToken = encodedPageToken
            }
        } else {
            listOrganizationsResponse {}
        }

        return Uni.createFrom().item { response }
    }

    private companion object {
        private val invalidPageTokenError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INVALID_REQUEST,
            "provided page token is invalid",
        )
            .toStatusRuntimeException()

        private fun organizationNotFoundException(organizationId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "organization \"$organizationId\" not found",
        )
            .toStatusRuntimeException()
    }
}
