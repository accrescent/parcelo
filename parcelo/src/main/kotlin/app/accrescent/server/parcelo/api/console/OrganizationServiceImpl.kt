// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.ListOrganizationsRequest
import app.accrescent.console.v1alpha1.ListOrganizationsResponse
import app.accrescent.console.v1alpha1.OrganizationService
import app.accrescent.console.v1alpha1.listOrganizationsResponse
import app.accrescent.console.v1alpha1.organization
import app.accrescent.parcelo.impl.v1.ListOrganizationsPageToken
import app.accrescent.parcelo.impl.v1.listOrganizationsPageToken
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import kotlin.io.encoding.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 50u

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class OrganizationServiceImpl : OrganizationService {
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
        private val invalidPageTokenError = Status
            .INVALID_ARGUMENT
            .withDescription("provided page token is invalid")
            .asRuntimeException()
    }
}
