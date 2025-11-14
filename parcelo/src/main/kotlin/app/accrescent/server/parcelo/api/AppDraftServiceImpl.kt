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
import app.accrescent.appstore.publish.v1alpha1.getAppDraftPackageUploadInfoResponse
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.UUID
import java.util.concurrent.TimeUnit

// 1 GiB
private const val MAX_APK_SET_SIZE_BYTES = 1073741824
private const val UPLOAD_URL_EXPIRATION_SECONDS = 30L

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
class AppDraftServiceImpl @Inject constructor(
    @ConfigProperty(name = "parcelo.bucket.appupload.name")
    private val appUploadBucketName: String,

    private val storage: Storage,
) : AppDraftService {
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

        val orgAppDraftLimit = Organization
            .findById(organizationId, LockModeType.PESSIMISTIC_WRITE)
            ?.appDraftLimit
            ?: run {
                throw Status
                    .NOT_FOUND
                    .withDescription("organization \"${request.organizationId}\" not found")
                    .asRuntimeException()
            }
        val orgAppDraftCount = AppDraft.countInOrganization(organizationId)
        if (orgAppDraftCount >= orgAppDraftLimit) {
            throw Status
                .RESOURCE_EXHAUSTED
                .withDescription(
                    "organization limit of $orgAppDraftLimit app drafts already reached"
                )
                .asRuntimeException()
        }

        val appDraft = AppDraft(id = UUID.randomUUID(), organizationId = organizationId)
            .also { it.persist() }
        AppDraftAcl(
            appDraftId = appDraft.id,
            userId = userId,
            canDelete = true,
            canReplacePackage = true,
            canView = true,
        )
            .persist()

        val response = createAppDraftResponse {
            id = appDraft.id.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraftPackageUploadInfo(
        request: GetAppDraftPackageUploadInfoRequest,
    ): Uni<GetAppDraftPackageUploadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val canViewDraft = PermissionService
            .userCanDeleteAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canViewDraft) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canReplacePackage = PermissionService
            .userCanReplaceAppDraftPackage(userId = userId, appDraftId = appDraftId)
        if (!canReplacePackage) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to replace package")
                .asRuntimeException()
        }

        val blobInfo = BlobInfo.newBuilder(appUploadBucketName, UUID.randomUUID().toString()).build()
        val uploadUrl = storage.signUrl(
            blobInfo,
            UPLOAD_URL_EXPIRATION_SECONDS,
            TimeUnit.SECONDS,
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withExtHeaders(
                mapOf("X-Goog-Content-Length-Range" to "0,$MAX_APK_SET_SIZE_BYTES")
            ),
        )

        val response = getAppDraftPackageUploadInfoResponse {
            apkSetUploadUrl = uploadUrl.toString()
        }

        return Uni.createFrom().item { response }
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
