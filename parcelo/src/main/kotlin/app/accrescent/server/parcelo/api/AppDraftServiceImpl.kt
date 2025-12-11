// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftService
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.CreateAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.DeleteAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftDownloadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftDownloadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.SubmitAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.SubmitAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.UpdateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.UpdateAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.createAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.createAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.deleteAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.deleteAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftDownloadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.submitAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.updateAppDraftResponse
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.AppDraftUploadProcessingJob
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.data.Reviewer
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
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

// 1 GiB
private const val MAX_APK_SET_SIZE_BYTES = 1073741824
private const val UPLOAD_URL_EXPIRATION_SECONDS = 30L
private const val DOWNLOAD_URL_EXPIRATION_SECONDS = 30L

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
class AppDraftServiceImpl @Inject constructor(
    private val config: ParceloConfig,
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

        val appDraft = AppDraft(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            appPackageId = null,
            defaultListingLanguage = null,
            submitted = false,
            reviewId = null,
        )
            .also { it.persist() }
        AppDraftAcl(
            appDraftId = appDraft.id,
            userId = userId,
            canDelete = true,
            canEditListings = true,
            canReplacePackage = true,
            canReview = false,
            canSubmit = true,
            canViewExistence = true,
        )
            .persist()

        val response = createAppDraftResponse {
            appDraftId = appDraft.id.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraftUploadInfo(
        request: GetAppDraftUploadInfoRequest,
    ): Uni<GetAppDraftUploadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
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
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }

        val blobInfo = BlobInfo
            .newBuilder(config.appUploadBucket(), UUID.randomUUID().toString()).build()
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
        AppDraftUploadProcessingJob(
            appDraftId = appDraftId,
            bucketId = blobInfo.bucket,
            objectId = blobInfo.name,
            completed = false,
            succeeded = false,
        )
            .persist()

        val response = getAppDraftUploadInfoResponse {
            apkSetUploadUrl = uploadUrl.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraftDownloadInfo(
        request: GetAppDraftDownloadInfoRequest,
    ): Uni<GetAppDraftDownloadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canReview = PermissionService.userCanReviewAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canReview) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to review app draft")
                .asRuntimeException()
        }
        val appPackage = appDraft.appPackage ?: throw Status
            .NOT_FOUND
            .withDescription("app draft \"$appDraftId\" has no package")
            .asRuntimeException()

        val apkSetBlob = BlobInfo.newBuilder(appPackage.bucketId, appPackage.objectId).build()
        val downloadUrl = storage.signUrl(
            apkSetBlob,
            DOWNLOAD_URL_EXPIRATION_SECONDS,
            TimeUnit.SECONDS,
            Storage.SignUrlOption.withV4Signature(),
        )

        val response = getAppDraftDownloadInfoResponse {
            apkSetUrl = downloadUrl.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateAppDraft(request: UpdateAppDraftRequest): Uni<UpdateAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canUpdate = PermissionService
            .userCanEditAppDraftListings(userId = userId, appDraftId = appDraftId)
        if (!canUpdate) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to update default listing language")
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted app drafts cannot be modified")
                .asRuntimeException()
        }

        // Update the app draft based on the update mask
        if (request.updateMask.pathsList.contains("default_listing_language")) {
            appDraft.defaultListingLanguage = request.defaultListingLanguage
        }

        return Uni.createFrom().item { updateAppDraftResponse {} }
    }

    @Transactional
    override fun submitAppDraft(request: SubmitAppDraftRequest): Uni<SubmitAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canSubmitDraft = PermissionService
            .userCanSubmitAppDraft(userId = userId, appDraftId = appDraftId)
        if (!canSubmitDraft) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to submit app draft")
                .asRuntimeException()
        }
        val defaultListingLanguage = appDraft.defaultListingLanguage
        when {
            appDraft.appPackageId == null -> throw Status
                .FAILED_PRECONDITION
                .withDescription("draft must have a package uploaded before it can be submitted")
                .asRuntimeException()

            defaultListingLanguage == null -> throw Status
                .FAILED_PRECONDITION
                .withDescription(
                    "draft must have a default listing language set before it can be submitted"
                )
                .asRuntimeException()

            !appDraft.hasListingForLanguage(defaultListingLanguage) -> throw Status
                .FAILED_PRECONDITION
                .withDescription("draft must have a listing for the default listing language")
                .asRuntimeException()

            appDraft.submitted -> throw Status
                .FAILED_PRECONDITION
                .withDescription("draft already submitted")
                .asRuntimeException()
        }

        // Assign a reviewer
        val reviewer = Reviewer.findRandom() ?: throw Status
            .FAILED_PRECONDITION
            .withDescription("no reviewers available to assign")
            .asRuntimeException()
        val existingAcl = AppDraftAcl.findByAppDraftIdAndUserId(appDraftId, reviewer.userId)
        if (existingAcl == null) {
            AppDraftAcl(
                appDraftId = appDraftId,
                userId = reviewer.userId,
                canDelete = false,
                canEditListings = false,
                canReplacePackage = false,
                canReview = true,
                canSubmit = false,
                canViewExistence = true,
            )
                .persist()
        } else {
            existingAcl.canReview = true
            existingAcl.canViewExistence = true
        }
        appDraft.submitted = true

        return Uni.createFrom().item { submitAppDraftResponse {} }
    }

    @Transactional
    override fun deleteAppDraft(request: DeleteAppDraftRequest): Uni<DeleteAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
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
                .withDescription(
                    "insufficient permission to delete app draft \"$appDraftId\""
                )
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be deleted")
                .asRuntimeException()
        }

        val appPackage = appDraft.appPackage
        appDraft.delete()

        // Delete the associated package (if one exists) and mark its associated blob for
        // deletion
        if (appPackage != null) {
            OrphanedBlob(
                bucketId = appPackage.bucketId,
                objectId = appPackage.objectId,
                orphanedOn = OffsetDateTime.now(),
            )
                .persist()
            appPackage.delete()
        }

        return Uni.createFrom().item { deleteAppDraftResponse {} }
    }

    @Transactional
    override fun createAppDraftListing(
        request: CreateAppDraftListingRequest,
    ): Uni<CreateAppDraftListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canCreateListings = PermissionService
            .userCanEditAppDraftListings(userId = userId, appDraftId = appDraftId)
        if (!canCreateListings) {
            throw Status
                .PERMISSION_DENIED
                .withDescription(
                    "insufficient permission to create app listings for app draft " +
                            "\"$appDraftId\""
                )
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }

        AppListing(
            appDraftId = appDraftId,
            language = request.language,
            name = request.name,
            shortDescription = request.shortDescription,
        )
            .persist()

        return Uni.createFrom().item { createAppDraftListingResponse {} }
    }

    @Transactional
    override fun deleteAppDraftListing(
        request: DeleteAppDraftListingRequest,
    ): Uni<DeleteAppDraftListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService
            .userCanViewAppDraftExistence(userId = userId, appDraftId = appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canDeleteAppDraftListings = PermissionService
            .userCanEditAppDraftListings(userId = userId, appDraftId = appDraftId)
        if (!canDeleteAppDraftListings) {
            throw Status
                .PERMISSION_DENIED
                .withDescription(
                    "insufficient permission to delete listings for app draft "
                            + "\"$appDraftId\""
                )
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }

        val deleted = AppListing.deleteByAppDraftAndLanguage(appDraftId, request.language)
        if (deleted) {
            return Uni.createFrom().item { deleteAppDraftListingResponse {} }
        } else {
            throw Status
                .NOT_FOUND
                .withDescription(
                    "listing with language \"${request.language}\" not found for app draft "
                            + "\"$appDraftId\""
                )
                .asRuntimeException()
        }
    }
}
