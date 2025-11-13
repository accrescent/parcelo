// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftService
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftPackageUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftPackageUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.createAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.deleteAppDraftResponse
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
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
class AppDraftServiceImpl : AppDraftService {
    @Transactional
    override fun createAppDraft(request: CreateAppDraftRequest): Uni<CreateAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val organizationId = UUID.fromString(request.organizationId)

        val canViewOrg = PermissionService
            .userCanViewOrganization(userId = userId, organizationId = organizationId)
        if (!canViewOrg) {
            throw Status
                .NOT_FOUND
                .withDescription("organization \"${request.organizationId}\" not found")
                .asRuntimeException()
        }

        val canCreateAppDrafts = PermissionService
            .userCanCreateAppDraftsInOrganization(userId = userId, organizationId = organizationId)
        if (!canCreateAppDrafts) {
            throw Status
                .PERMISSION_DENIED
                .withDescription(
                    "insufficient permission to create app drafts in organization " +
                            "\"${request.organizationId}\""
                )
                .asRuntimeException()
        }

        val appDraft = AppDraft(id = UUID.randomUUID(), organizationId = organizationId)
            .also { it.persist() }
        AppDraftAcl(
            appDraftId = appDraft.id,
            userId = userId,
            canDelete = true,
            canView = true,
        )
            .persist()

        val response = createAppDraftResponse {
            id = appDraft.id.toString()
        }

        return Uni.createFrom().item { response }
    }

    override fun getAppDraftPackageUploadInfo(
        request: GetAppDraftPackageUploadInfoRequest,
    ): Uni<GetAppDraftPackageUploadInfoResponse> {
        throw Status.UNIMPLEMENTED.asRuntimeException()
    }

    @Transactional
    override fun deleteAppDraft(request: DeleteAppDraftRequest): Uni<DeleteAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.id)

        val canViewDraft = PermissionService
            .userCanViewAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canViewDraft) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }

        val canDeleteAppDraft = PermissionService
            .userCanDeleteAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canDeleteAppDraft) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to delete app draft \"$appDraftId\"")
                .asRuntimeException()
        }

        AppDraft.deleteById(appDraftId)

        return Uni.createFrom().item { deleteAppDraftResponse {} }
    }
}
