// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.ListMyOrganizationsRequest
import app.accrescent.appstore.publish.v1alpha1.ListMyOrganizationsResponse
import app.accrescent.appstore.publish.v1alpha1.OrganizationService
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsResponse
import app.accrescent.appstore.publish.v1alpha1.organization
import app.accrescent.parcelo.impl.v1.ListMyOrganizationsPageToken
import app.accrescent.parcelo.impl.v1.listMyOrganizationsPageToken
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.transaction.Transactional
import java.util.UUID
import kotlin.io.encoding.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 50u

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
class OrganizationServiceImpl : OrganizationService {
    @Transactional
    override fun listMyOrganizations(
        request: ListMyOrganizationsRequest,
    ): Uni<ListMyOrganizationsResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val lastOrganizationId = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val token = ListMyOrganizationsPageToken.parseFrom(tokenBytes)
                if (!token.hasLastOrganizationId()) {
                    throw invalidPageTokenError
                }

                UUID.fromString(token.lastOrganizationId)
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
                    id = organization.id.toString()
                }
            }

        val response = if (organizations.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listMyOrganizationsPageToken {
                this.lastOrganizationId = organizations.last().id
            }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listMyOrganizationsResponse {
                this.organizations.addAll(organizations)
                nextPageToken = encodedPageToken
            }
        } else {
            listMyOrganizationsResponse {}
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
