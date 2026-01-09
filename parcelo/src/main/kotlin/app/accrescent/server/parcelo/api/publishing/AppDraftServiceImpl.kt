// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

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
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftListingIconUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftListingIconUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.GetAppDraftUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.PublishAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.PublishAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.SubmitAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.SubmitAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.UpdateAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.UpdateAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.appDraft
import app.accrescent.appstore.publish.v1alpha1.createAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.createAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.deleteAppDraftListingResponse
import app.accrescent.appstore.publish.v1alpha1.deleteAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftDownloadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftListingIconUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.getAppDraftUploadInfoResponse
import app.accrescent.appstore.publish.v1alpha1.publishAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.submitAppDraftResponse
import app.accrescent.appstore.publish.v1alpha1.updateAppDraftResponse
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.AppDraftListing
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.AppDraftUploadProcessingJob
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.data.PublishedApk
import app.accrescent.server.parcelo.data.PublishedImage
import app.accrescent.server.parcelo.data.Reviewer
import app.accrescent.server.parcelo.publish.PublishService
import app.accrescent.server.parcelo.publish.PublishedIcon
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.util.TempFile
import app.accrescent.server.parcelo.util.apkPaths
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.android.bundle.Commands
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.quarkus.mailer.MailTemplate
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

// 1 GiB
private const val MAX_APK_SET_SIZE_BYTES = 1073741824
private const val UPLOAD_URL_EXPIRATION_SECONDS = 30L
private const val DOWNLOAD_URL_EXPIRATION_SECONDS = 30L

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppDraftServiceImpl @Inject constructor(
    private val config: ParceloConfig,
    private val publishService: PublishService,
    private val storage: Storage,
) : AppDraftService {
    @JvmRecord
    data class AppDraftAssignedToYouForReviewEmail(
        val appDraftId: UUID,
    ) : MailTemplate.MailTemplateInstance

    @Transactional
    override fun createAppDraft(request: CreateAppDraftRequest): Uni<CreateAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val organizationId = UUID.fromString(request.organizationId)

        val canViewOrg = PermissionService.userCanViewOrganization(userId, organizationId)
        if (!canViewOrg) {
            throw Status
                .NOT_FOUND
                .withDescription("organization \"${request.organizationId}\" not found")
                .asRuntimeException()
        }

        val canCreateAppDrafts = PermissionService
            .userCanCreateAppDraftsInOrganization(userId, organizationId)
        if (!canCreateAppDrafts) {
            throw Status
                .PERMISSION_DENIED
                .withDescription(
                    "insufficient permission to create app drafts in organization " +
                            "\"${request.organizationId}\""
                )
                .asRuntimeException()
        }

        val orgActiveAppDraftLimit = Organization
            .findById(organizationId, LockModeType.PESSIMISTIC_WRITE)
            ?.activeAppDraftLimit
            ?: run {
                throw Status
                    .NOT_FOUND
                    .withDescription("organization \"${request.organizationId}\" not found")
                    .asRuntimeException()
            }
        val orgActiveAppDraftCount = AppDraft.countActiveInOrganization(organizationId)
        if (orgActiveAppDraftCount >= orgActiveAppDraftLimit) {
            throw Status
                .RESOURCE_EXHAUSTED
                .withDescription(
                    "organization limit of $orgActiveAppDraftLimit active app drafts already reached"
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
            published = false,
        )
            .also { it.persist() }
        AppDraftAcl(
            appDraftId = appDraft.id,
            userId = userId,
            canDelete = true,
            canEditListings = true,
            canPublish = false,
            canReplacePackage = true,
            canReview = false,
            canSubmit = true,
            canView = true,
            canViewExistence = true,
        )
            .persist()

        val response = createAppDraftResponse {
            appDraftId = appDraft.id.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraft(request: GetAppDraftRequest): Uni<GetAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canView = PermissionService.userCanViewAppDraft(userId, appDraftId)
        if (!canView) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to view app draft")
                .asRuntimeException()
        }

        val response = getAppDraftResponse {
            draft = appDraft {
                id = appDraft.id.toString()
                submitted = appDraft.submitted
            }
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
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canReplacePackage = PermissionService.userCanReplaceAppDraftPackage(userId, appDraftId)
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
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canReview = PermissionService.userCanReviewAppDraft(userId, appDraftId)
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
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canUpdate = PermissionService.userCanEditAppDraftListings(userId, appDraftId)
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
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canSubmitDraft = PermissionService.userCanSubmitAppDraft(userId, appDraftId)
        if (!canSubmitDraft) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to submit app draft")
                .asRuntimeException()
        }
        val appPackage = appDraft.appPackage ?: throw Status
            .FAILED_PRECONDITION
            .withDescription("draft must have a package uploaded before it can be submitted")
            .asRuntimeException()
        val defaultListingLanguage = appDraft.defaultListingLanguage
        when {
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

            !appDraft.allListingsHaveIcon() -> throw Status
                .FAILED_PRECONDITION
                .withDescription("all draft listings must have an icon")
                .asRuntimeException()

            appDraft.submitted -> throw Status
                .FAILED_PRECONDITION
                .withDescription("draft already submitted")
                .asRuntimeException()

            AppDraft.submittedDraftExistsWithAppId(appPackage.appId) -> throw Status
                .ALREADY_EXISTS
                .withDescription(
                    "a draft has already been submitted for app ID \"${appPackage.appId}\""
                )
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
                canPublish = false,
                canReplacePackage = false,
                canReview = true,
                canSubmit = false,
                canView = false,
                canViewExistence = true,
            )
                .persist()
        } else {
            existingAcl.canReview = true
            existingAcl.canViewExistence = true
        }
        appDraft.submitted = true

        // Notify the reviewer that they are assigned to this draft before the transaction is
        // committed. This approach means reviewers may receive notifications for drafts they aren't
        // actually assigned to if the transaction is rolled back. However, it also means we will
        // always send a notification for drafts they are actually assigned to, which we want to
        // guarantee to ascertain timely reviews.
        AppDraftAssignedToYouForReviewEmail(appDraft.id)
            .to(reviewer.email)
            .subject("A new app draft has been assigned to you")
            .sendAndAwait()

        return Uni.createFrom().item { submitAppDraftResponse {} }
    }

    @Transactional
    override fun deleteAppDraft(request: DeleteAppDraftRequest): Uni<DeleteAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canDeleteAppDraft = PermissionService.userCanDeleteAppDraft(userId, appDraftId)
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
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canCreateListings = PermissionService.userCanEditAppDraftListings(userId, appDraftId)
        if (!canCreateListings) {
            throw Status
                .PERMISSION_DENIED
                .withDescription(
                    "insufficient permission to create app listings for app draft " +
                            "\"$appDraftId\""
                )
                .asRuntimeException()
        }
        if (AppDraftListing.exists(appDraftId, request.language)) {
            throw Status
                .ALREADY_EXISTS
                .withDescription(
                    "an app listing for app draft \"$appDraftId\" with language " +
                            "\"${request.language}\" already exists"
                )
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }

        AppDraftListing(
            id = UUID.randomUUID(),
            appDraftId = appDraftId,
            language = request.language,
            name = request.name,
            shortDescription = request.shortDescription,
            iconImageId = null,
        )
            .persist()

        return Uni.createFrom().item { createAppDraftListingResponse {} }
    }

    @Transactional
    override fun getAppDraftListingIconUploadInfo(
        request: GetAppDraftListingIconUploadInfoRequest,
    ): Uni<GetAppDraftListingIconUploadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canEditListingIcon = PermissionService.userCanEditAppDraftListings(userId, appDraftId)
        if (!canEditListingIcon) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to modify app listing icon")
                .asRuntimeException()
        }
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }
        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(appDraftId, request.language)
            ?: throw Status
                .NOT_FOUND
                .withDescription("listing with language \"${request.language}\" not found")
                .asRuntimeException()

        val uploadJob = AppDraftListingIconUploadJob(
            appDraftListingId = appDraftListing.id,
            uploadKey = UUID.randomUUID(),
            completed = false,
            succeeded = false,
            expiresAt = OffsetDateTime.now().plusSeconds(UPLOAD_URL_EXPIRATION_SECONDS),
        )
            .also { it.persist() }

        val response = getAppDraftListingIconUploadInfoResponse {
            uploadUrl = ImageUploadService
                .createUploadUrl(config.imageUploadServiceBaseUrl(), uploadJob.uploadKey)
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun deleteAppDraftListing(
        request: DeleteAppDraftListingRequest,
    ): Uni<DeleteAppDraftListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canDeleteAppDraftListings = PermissionService
            .userCanEditAppDraftListings(userId, appDraftId)
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
        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(appDraftId, request.language)
            ?: throw Status
                .NOT_FOUND
                .withDescription(
                    "listing with language \"${request.language}\" not found for app draft "
                            + "\"$appDraftId\""
                )
                .asRuntimeException()

        // Delete the associated icon (if it exists) and mark its associated blob for deletion
        appDraftListing.icon?.let { icon ->
            OrphanedBlob(
                bucketId = icon.bucketId,
                objectId = icon.objectId,
                orphanedOn = OffsetDateTime.now()
            )
                .persist()
            icon.delete()
        }

        // Delete the listing
        appDraftListing.delete()

        return Uni.createFrom().item { deleteAppDraftListingResponse {} }
    }

    @Transactional
    override fun publishAppDraft(request: PublishAppDraftRequest): Uni<PublishAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()
        // protovalidate ensures this is a valid UUID, so no need to catch IllegalArgumentException
        val appDraftId = UUID.fromString(request.appDraftId)

        val appDraft = AppDraft.findById(appDraftId)
        val canViewExistence = PermissionService.userCanViewAppDraftExistence(userId, appDraftId)
        if (!canViewExistence || appDraft == null) {
            throw Status
                .NOT_FOUND
                .withDescription("app draft \"$appDraftId\" not found")
                .asRuntimeException()
        }
        val canPublish = PermissionService.userCanPublishAppDraft(userId, appDraftId)
        if (!canPublish) {
            throw Status
                .PERMISSION_DENIED
                .withDescription("insufficient permission to publish app draft")
                .asRuntimeException()
        }
        if (appDraft.published) {
            throw Status
                .ALREADY_EXISTS
                .withDescription("the app draft has already been published")
                .asRuntimeException()
        }

        val appPackage = appDraft.appPackage ?: throw Status
            .INTERNAL
            .withDescription("app draft has no package")
            .asRuntimeException()
        val appId = appPackage.appId
        val defaultListingLanguage = appDraft.defaultListingLanguage ?: throw Status
            .INTERNAL
            .withDescription("app draft has no default listing language")
            .asRuntimeException()
        val listingIcons = appDraft.listings.map {
            val icon = it.icon ?: throw Status
                .DATA_LOSS
                .withDescription("listing for language \"${it.language}\" has no icon")
                .asRuntimeException()
            it.language to icon
        }
        if (App.existsById(appId)) {
            throw Status
                .ALREADY_EXISTS
                .withDescription("an app with ID \"$appId\" has already been published")
                .asRuntimeException()
        }

        // Parse the app package metadata
        val buildApksResult = try {
            Commands.BuildApksResult.parseFrom(appPackage.buildApksResult)
        } catch (_: InvalidProtocolBufferException) {
            throw Status
                .DATA_LOSS
                .withDescription("app package metadata is invalid")
                .asRuntimeException()
        }

        // Publish the APK set's APKs to an S3-compatible server.
        //
        // It is possible for this process to create orphan objects, i.e., objects that are stored
        // remotely but untracked by our database. However, this creation occurs only if this RPC
        // does not eventually complete successfully. That is, if this RPC is called and completes
        // successfully for a given draft, it is guaranteed that no orphan objects exist for it even
        // if previous calls have failed.
        val pathsToApks = TempFile(Path(config.packageProcessingDirectory())).use { tempApkSet ->
            try {
                storage
                    .get(BlobId.of(appPackage.bucketId, appPackage.objectId))
                    .downloadTo(tempApkSet.path)
            } catch (_: StorageException) {
                throw Status
                    .UNAVAILABLE
                    .withDescription("failed to download app package")
                    .asRuntimeException()
            }

            publishService.publishApks(
                appId = buildApksResult.packageName,
                versionCode = appPackage.versionCode,
                apkSetPath = tempApkSet.path,
                apkPaths = buildApksResult.apkPaths(),
            )
        }
        // Publish the app's listing icons to an S3-compatible server.
        //
        // This process has the same orphan object guarantees as publishing an APK set's APKs.
        val publishedIcons = mutableMapOf<String, PublishedIcon>()
        for ((listingLanguage, icon) in listingIcons) {
            TempFile(Path(config.packageProcessingDirectory())).use { tempIcon ->
                try {
                    storage.get(BlobId.of(icon.bucketId, icon.objectId)).downloadTo(tempIcon.path)
                } catch (_: StorageException) {
                    throw Status
                        .UNAVAILABLE
                        .withDescription("failed to download icon for listing \"$listingLanguage\"")
                        .asRuntimeException()
                }

                val publishedIcon = publishService.publishIcon(
                    appId = buildApksResult.packageName,
                    listingLanguage = listingLanguage,
                    iconPath = tempIcon.path,
                )
                publishedIcons.put(listingLanguage, publishedIcon)
            }
        }

        // Publish draft
        App(
            id = appId,
            defaultListingLanguage = defaultListingLanguage,
            organizationId = appDraft.organizationId,
            appPackageId = appPackage.id,
        )
            .persist()
        for (draftListing in appDraft.listings) {
            val remoteIcon = publishedIcons[draftListing.language] ?: throw Status
                .INTERNAL
                .withDescription("no published icon exists for language \"${draftListing.language}\"")
                .asRuntimeException()
            val publishedIcon = PublishedImage(
                id = UUID.randomUUID(),
                bucketId = remoteIcon.bucketId,
                objectId = remoteIcon.objectId,
            )
                .also { it.persist() }
            AppListing(
                appId = appId,
                language = draftListing.language,
                name = draftListing.name,
                shortDescription = draftListing.shortDescription,
                iconPublishedImageId = publishedIcon.id,
            )
                .persist()
        }
        for ((apkPath, apk) in pathsToApks) {
            PublishedApk(
                appPackageId = appPackage.id,
                apkPath = apkPath,
                bucketId = apk.bucketId,
                objectId = apk.objectId,
                size = apk.size,
            )
                .persist()
        }
        appDraft.published = true

        return Uni.createFrom().item { publishAppDraftResponse {} }
    }
}
