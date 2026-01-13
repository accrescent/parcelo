// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.AppEditService
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppEditResponse
import app.accrescent.appstore.publish.v1alpha1.createAppEditResponse
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppEditListing
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
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
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppEditServiceImpl : AppEditService {
    @Transactional
    override fun createAppEdit(request: CreateAppEditRequest): Uni<CreateAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreateAppEdit = PermissionService.userCanCreateAppEditForApp(userId, request.appId)
        if (!canCreateAppEdit) {
            val appExists = App.existsById(request.appId)
            val canViewAppExistence = PermissionService.userCanViewAppExistence(userId, request.appId)

            throw if (!canViewAppExistence || !appExists) {
                appNotFoundException(request.appId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription(
                        "insufficient permission to create edits for app \"${request.appId}\""
                    )
                    .asRuntimeException()
            }
        }
        val app = App.findById(request.appId) ?: throw appNotFoundException(request.appId)

        val appActiveEditLimit = app.activeEditLimit
        val appActiveEditCount = AppEdit.countActiveForApp(app.id)
        if (appActiveEditCount >= appActiveEditLimit) {
            throw Status
                .RESOURCE_EXHAUSTED
                .withDescription("app limit of $appActiveEditLimit active edits already reached")
                .asRuntimeException()
        }

        val appEdit = AppEdit(
            id = UUID.randomUUID(),
            appId = request.appId,
            defaultListingLanguage = app.defaultListingLanguage,
            appPackageId = app.appPackageId,
            reviewId = null,
            published = false,
        )
            .also { it.persist() }
        for (listing in app.listings) {
            AppEditListing(
                id = UUID.randomUUID(),
                appEditId = appEdit.id,
                language = listing.language,
                name = listing.name,
                shortDescription = listing.shortDescription,
                iconImageId = listing.iconImageId,
            )
                .persist()
        }

        val response = createAppEditResponse { appEditId = appEdit.id.toString() }

        return Uni.createFrom().item { response }
    }

    private companion object {
        private fun appNotFoundException(appId: String) = Status
            .NOT_FOUND
            .withDescription("app with ID \"$appId\" not found")
            .asRuntimeException()
    }
}
