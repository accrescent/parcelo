// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.AppDraftService
import app.accrescent.console.v1alpha1.CreateAppDraftListingIconUploadOperationRequest
import app.accrescent.console.v1alpha1.CreateAppDraftListingIconUploadOperationResponse
import app.accrescent.console.v1alpha1.CreateAppDraftListingRequest
import app.accrescent.console.v1alpha1.CreateAppDraftListingResponse
import app.accrescent.console.v1alpha1.CreateAppDraftRequest
import app.accrescent.console.v1alpha1.CreateAppDraftResponse
import app.accrescent.console.v1alpha1.CreateAppDraftUploadOperationRequest
import app.accrescent.console.v1alpha1.CreateAppDraftUploadOperationResponse
import app.accrescent.console.v1alpha1.DeleteAppDraftListingRequest
import app.accrescent.console.v1alpha1.DeleteAppDraftListingResponse
import app.accrescent.console.v1alpha1.DeleteAppDraftRequest
import app.accrescent.console.v1alpha1.DeleteAppDraftResponse
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.GetAppDraftDownloadInfoRequest
import app.accrescent.console.v1alpha1.GetAppDraftDownloadInfoResponse
import app.accrescent.console.v1alpha1.GetAppDraftListingIconDownloadInfoRequest
import app.accrescent.console.v1alpha1.GetAppDraftListingIconDownloadInfoResponse
import app.accrescent.console.v1alpha1.GetAppDraftRequest
import app.accrescent.console.v1alpha1.GetAppDraftResponse
import app.accrescent.console.v1alpha1.ListAppDraftsRequest
import app.accrescent.console.v1alpha1.ListAppDraftsResponse
import app.accrescent.console.v1alpha1.PublishAppDraftRequest
import app.accrescent.console.v1alpha1.PublishAppDraftResponse
import app.accrescent.console.v1alpha1.SubmitAppDraftRequest
import app.accrescent.console.v1alpha1.SubmitAppDraftResponse
import app.accrescent.console.v1alpha1.UpdateAppDraftRequest
import app.accrescent.console.v1alpha1.UpdateAppDraftResponse
import app.accrescent.console.v1alpha1.appDraft
import app.accrescent.console.v1alpha1.appPackage
import app.accrescent.console.v1alpha1.createAppDraftListingIconUploadOperationResponse
import app.accrescent.console.v1alpha1.createAppDraftListingResponse
import app.accrescent.console.v1alpha1.createAppDraftResponse
import app.accrescent.console.v1alpha1.createAppDraftUploadOperationResponse
import app.accrescent.console.v1alpha1.deleteAppDraftListingResponse
import app.accrescent.console.v1alpha1.deleteAppDraftResponse
import app.accrescent.console.v1alpha1.getAppDraftDownloadInfoResponse
import app.accrescent.console.v1alpha1.getAppDraftListingIconDownloadInfoResponse
import app.accrescent.console.v1alpha1.getAppDraftResponse
import app.accrescent.console.v1alpha1.listAppDraftsResponse
import app.accrescent.console.v1alpha1.publishAppDraftResponse
import app.accrescent.console.v1alpha1.submitAppDraftResponse
import app.accrescent.console.v1alpha1.updateAppDraftResponse
import app.accrescent.parcelo.impl.v1.ListAppDraftsPageToken
import app.accrescent.parcelo.impl.v1.listAppDraftsPageToken
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftListing
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.AppDraftRelationshipSet
import app.accrescent.server.parcelo.data.AppDraftUploadProcessingJob
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.jobs.JobDataKey
import app.accrescent.server.parcelo.jobs.PublishAppDraftJob
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.HasPermissionRequest
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.longrunning.Operation
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.timestamp
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.quarkus.mailer.MailTemplate
import io.smallrye.mutiny.Uni
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.io.encoding.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 50u

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppDraftServiceImpl @Inject constructor(
    private val config: ParceloConfig,
    private val permissionService: PermissionService,
    private val scheduler: Instance<Scheduler>,
    private val storage: Storage,
) : AppDraftService {
    @JvmRecord
    data class AppDraftAssignedToYouForReviewEmail(
        val appDraftId: String,
    ) : MailTemplate.MailTemplateInstance

    @Transactional
    override fun createAppDraft(request: CreateAppDraftRequest): Uni<CreateAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreateAppDraft = permissionService
            .hasPermission(HasPermissionRequest.CreateAppDraft(request.organizationId, userId))
        if (!canCreateAppDraft) {
            val orgExists = Organization.existsById(request.organizationId)
            val canViewOrgExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewOrganizationExistence(request.organizationId, userId),
            )

            throw if (!orgExists || !canViewOrgExistence) {
                organizationNotFoundException(request.organizationId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to create app drafts in organization " +
                            "\"${request.organizationId}\"",
                )
                    .toStatusRuntimeException()
            }
        }

        val organization = Organization
            .findById(request.organizationId)
            ?: throw organizationNotFoundException(request.organizationId)
        val orgActiveAppDraftLimit = organization.activeAppDraftLimit
        val orgActiveAppDraftCount = AppDraft.countActiveInOrganization(request.organizationId)
        if (orgActiveAppDraftCount >= orgActiveAppDraftLimit) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED,
                "organization limit of $orgActiveAppDraftLimit active app drafts already reached",
            )
                .toStatusRuntimeException()
        }

        val appDraft = AppDraft(
            id = Identifier.generateNew(IdType.APP_DRAFT),
            organizationId = request.organizationId,
            createdAt = OffsetDateTime.now(),
            appPackageId = null,
            defaultListingLanguage = null,
            submittedAt = null,
            reviewId = null,
            publishing = false,
            publishedAt = null,
        )
            .also { it.persist() }

        val response = createAppDraftResponse {
            appDraftId = appDraft.id
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraft(request: GetAppDraftRequest): Uni<GetAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService
            .hasPermission(HasPermissionRequest.ViewAppDraft(request.appDraftId, userId))
        if (!canView) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to view app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val response = getAppDraftResponse {
            draft = appDraft {
                id = appDraft.id
                createdAt = timestamp {
                    seconds = appDraft.createdAt.toEpochSecond()
                    nanos = appDraft.createdAt.nano
                }
                appDraft.defaultListingLanguage?.let { defaultListingLanguage = it }
                appDraft.appPackage?.let { pkg ->
                    appPackage = appPackage {
                        appId = pkg.appId
                        versionCode = pkg.versionCode.toLong()
                        versionName = pkg.versionName
                        targetSdk = pkg.targetSdk.toLong()
                    }
                }
                appDraft.submittedAt?.let { submissionTimestamp ->
                    submittedAt = timestamp {
                        seconds = submissionTimestamp.toEpochSecond()
                        nanos = submissionTimestamp.nano
                    }
                }
                appDraft.publishedAt?.let { publicationTimestamp ->
                    publishedAt = timestamp {
                        seconds = publicationTimestamp.toEpochSecond()
                        nanos = publicationTimestamp.nano
                    }
                }
            }
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun listAppDrafts(request: ListAppDraftsRequest): Uni<ListAppDraftsResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val lastAppDraftId = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val pageToken = ListAppDraftsPageToken.parseFrom(tokenBytes)
                if (!pageToken.hasLastAppDraftId()) {
                    throw invalidPageTokenError
                }

                pageToken.lastAppDraftId
            } catch (_: IllegalArgumentException) {
                throw invalidPageTokenError
            } catch (_: InvalidProtocolBufferException) {
                throw invalidPageTokenError
            }
        } else {
            null
        }

        val appDrafts = AppDraft
            .findForUserByQuery(userId, pageSize, lastAppDraftId)
            .map { appDraft ->
                appDraft {
                    id = appDraft.id
                    createdAt = timestamp {
                        seconds = appDraft.createdAt.toEpochSecond()
                        nanos = appDraft.createdAt.nano
                    }
                    appDraft.defaultListingLanguage?.let { defaultListingLanguage = it }
                    appDraft.appPackage?.let { pkg ->
                        appPackage = appPackage {
                            appId = pkg.appId
                            versionCode = pkg.versionCode.toLong()
                            versionName = pkg.versionName
                            targetSdk = pkg.targetSdk.toLong()
                        }
                    }
                    appDraft.submittedAt?.let { submissionTimestamp ->
                        submittedAt = timestamp {
                            seconds = submissionTimestamp.toEpochSecond()
                            nanos = submissionTimestamp.nano
                        }
                    }
                    appDraft.publishedAt?.let { publicationTimestamp ->
                        publishedAt = timestamp {
                            seconds = publicationTimestamp.toEpochSecond()
                            nanos = publicationTimestamp.nano
                        }
                    }
                }
            }

        val response = if (appDrafts.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listAppDraftsPageToken {
                this.lastAppDraftId = appDrafts.last().id
            }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listAppDraftsResponse {
                this.appDrafts.addAll(appDrafts)
                nextPageToken = encodedPageToken
            }
        } else {
            listAppDraftsResponse {}
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun createAppDraftUploadOperation(
        request: CreateAppDraftUploadOperationRequest,
    ): Uni<CreateAppDraftUploadOperationResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReplacePackage = permissionService
            .hasPermission(HasPermissionRequest.ReplaceAppDraftPackage(request.appDraftId, userId))
        if (!canReplacePackage) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to replace package",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted drafts cannot be modified",
            )
                .toStatusRuntimeException()
        }

        val blobInfo = BlobInfo
            .newBuilder(config.buckets().appUpload(), UUID.randomUUID().toString()).build()
        val uploadUrl = storage.signUploadUrl(blobInfo, UploadType.APK_SET)

        val backgroundOperation = BackgroundOperation(
            id = Identifier.generateNew(IdType.OPERATION),
            type = BackgroundOperationType.UPLOAD_APP_DRAFT,
            parentId = request.appDraftId,
            createdAt = OffsetDateTime.now(),
            result = null,
            succeeded = false,
        )
            .also { it.persist() }
        AppDraftUploadProcessingJob(
            appDraftId = request.appDraftId,
            bucketId = blobInfo.bucket,
            objectId = blobInfo.name,
            backgroundOperationId = backgroundOperation.id,
        )
            .persist()

        val response = createAppDraftUploadOperationResponse {
            apkSetUploadUrl = uploadUrl.toString()
            processingOperation = Operation.newBuilder().setName(backgroundOperation.id).build()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraftDownloadInfo(
        request: GetAppDraftDownloadInfoRequest,
    ): Uni<GetAppDraftDownloadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canDownload = permissionService
            .hasPermission(HasPermissionRequest.DownloadAppDraft(request.appDraftId, userId))
        if (!canDownload) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to download app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft
            .appPackage
            ?: throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
                "app draft \"${request.appDraftId}\" has no package",
            )
                .toStatusRuntimeException()

        val apkSetBlob = BlobInfo.newBuilder(appPackage.bucketId, appPackage.objectId).build()
        val downloadUrl = storage.signDownloadUrl(apkSetBlob)

        val response = getAppDraftDownloadInfoResponse {
            apkSetUrl = downloadUrl.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateAppDraft(request: UpdateAppDraftRequest): Uni<UpdateAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canUpdate = permissionService
            .hasPermission(HasPermissionRequest.UpdateAppDraft(request.appDraftId, userId))
        if (!canUpdate) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to update app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted app drafts cannot be modified",
            )
                .toStatusRuntimeException()
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

        val canSubmit = permissionService
            .hasPermission(HasPermissionRequest.SubmitAppDraft(request.appDraftId, userId))
        if (!canSubmit) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to submit app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft
            .appPackage
            ?: throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
                "draft must have a package uploaded before it can be submitted",
            )
                .toStatusRuntimeException()
        val defaultListingLanguage = appDraft.defaultListingLanguage
        val orgPublishedAppLimit = appDraft.organization.publishedAppLimit
        val orgPublishedAppCount = App.countInOrganization(appDraft.organizationId)
        when {
            defaultListingLanguage == null -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
                "draft must have a default listing language set before it can be submitted",
            )
                .toStatusRuntimeException()

            !appDraft.hasListingForLanguage(defaultListingLanguage) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
                "draft must have a listing for the default listing language",
            )
                .toStatusRuntimeException()

            !appDraft.allListingsHaveIcon() -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
                "all draft listings must have an icon",
            )
                .toStatusRuntimeException()

            appDraft.submitted -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_SUBMITTED,
                "draft already submitted",
            )
                .toStatusRuntimeException()

            AppDraft.submittedDraftExistsWithAppId(appPackage.appId) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_CONFLICT,
                "a draft has already been submitted for app ID \"${appPackage.appId}\"",
            )
                .toStatusRuntimeException()

            orgPublishedAppCount >= orgPublishedAppLimit -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED,
                "organization limit of $orgPublishedAppLimit published apps already reached",
            )
                .toStatusRuntimeException()
        }

        // Assign a reviewer
        val reviewer = User.findRandomReviewer() ?: throw ConsoleApiError(
            ErrorReason.ERROR_REASON_ASSIGNEE_UNAVAILABLE,
            "no reviewers available to assign",
        )
            .toStatusRuntimeException()
        val existingRelationshipSet = AppDraftRelationshipSet
            .findByAppDraftIdAndUserId(request.appDraftId, reviewer.id)
        if (existingRelationshipSet == null) {
            AppDraftRelationshipSet(
                appDraftId = request.appDraftId,
                userId = reviewer.id,
                reviewer = true,
                publisher = false,
            )
                .persist()
        } else {
            existingRelationshipSet.reviewer = true
        }
        appDraft.submittedAt = OffsetDateTime.now()

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

        val canDelete = permissionService
            .hasPermission(HasPermissionRequest.DeleteAppDraft(request.appDraftId, userId))
        if (!canDelete) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to delete app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted drafts cannot be deleted",
            )
                .toStatusRuntimeException()
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

        val canCreateListing = permissionService
            .hasPermission(HasPermissionRequest.CreateAppDraftListing(request.appDraftId, userId))
        if (!canCreateListing) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to create app draft listing",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        when {
            AppDraftListing.exists(request.appDraftId, request.language) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_EXISTS,
                "an app listing for app draft \"${request.appDraftId}\" with language " +
                        "\"${request.language}\" already exists"
            )
                .toStatusRuntimeException()

            appDraft.submitted -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted drafts cannot be modified",
            )
                .toStatusRuntimeException()

        }

        AppDraftListing(
            id = UUID.randomUUID(),
            appDraftId = request.appDraftId,
            language = request.language,
            name = request.name,
            shortDescription = request.shortDescription,
            iconImageId = null,
        )
            .persist()

        return Uni.createFrom().item { createAppDraftListingResponse {} }
    }

    @Transactional
    override fun createAppDraftListingIconUploadOperation(
        request: CreateAppDraftListingIconUploadOperationRequest,
    ): Uni<CreateAppDraftListingIconUploadOperationResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReplaceIcon = permissionService
            .hasPermission(HasPermissionRequest.ReplaceAppDraftListingIcon(request.appDraftId, userId))
        if (!canReplaceIcon) {
            val draftExists = AppDraft.existsById(request.appDraftId)
            val listingExists = AppDraftListing.exists(request.appDraftId, request.language)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw when {
                !draftExists || !canViewExistence -> appDraftNotFoundException(request.appDraftId)
                !listingExists -> appDraftListingNotFoundException(request.language)
                else -> throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to replace app listing icon",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted drafts cannot be modified",
            )
                .toStatusRuntimeException()
        }
        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(request.appDraftId, request.language)
            ?: throw appDraftListingNotFoundException(request.language)

        val blobInfo = BlobInfo
            .newBuilder(config.buckets().draftListingIconUpload(), UUID.randomUUID().toString())
            .build()
        val uploadUrl = storage.signUploadUrl(blobInfo, UploadType.ICON)

        val backgroundOperation = BackgroundOperation(
            id = Identifier.generateNew(IdType.OPERATION),
            type = BackgroundOperationType.UPLOAD_APP_DRAFT_LISTING_ICON,
            parentId = request.appDraftId,
            createdAt = OffsetDateTime.now(),
            result = null,
            succeeded = false,
        )
            .also { it.persist() }
        AppDraftListingIconUploadJob(
            appDraftListingId = appDraftListing.id,
            bucketId = blobInfo.bucket,
            objectId = blobInfo.name,
            backgroundOperationId = backgroundOperation.id,
        )
            .persist()

        val response = createAppDraftListingIconUploadOperationResponse {
            this.uploadUrl = uploadUrl.toString()
            processingOperation = Operation.newBuilder().setName(backgroundOperation.id).build()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraftListingIconDownloadInfo(
        request: GetAppDraftListingIconDownloadInfoRequest,
    ): Uni<GetAppDraftListingIconDownloadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canDownload = permissionService.hasPermission(
            HasPermissionRequest.DownloadAppDraftListingIcons(request.appDraftId, userId),
        )
        if (!canDownload) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to download app draft listing icon",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(request.appDraftId, request.language)
            ?: throw appDraftListingNotFoundException(request.language)
        val icon = appDraftListing.icon ?: throw appDraftListingIconNotFoundException(request.language)

        val iconBlob = BlobInfo.newBuilder(icon.bucketId, icon.objectId).build()
        val downloadUrl = storage.signDownloadUrl(iconBlob)

        val response = getAppDraftListingIconDownloadInfoResponse {
            iconUrl = downloadUrl.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun deleteAppDraftListing(
        request: DeleteAppDraftListingRequest,
    ): Uni<DeleteAppDraftListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canDelete = permissionService
            .hasPermission(HasPermissionRequest.DeleteAppDraftListing(request.appDraftId, userId))
        if (!canDelete) {
            val draftExists = AppDraft.existsById(request.appDraftId)
            val listingExists = AppDraftListing.exists(request.appDraftId, request.language)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw when {
                !draftExists || !canViewExistence -> appDraftNotFoundException(request.appDraftId)
                !listingExists -> appDraftListingNotFoundException(request.language)
                else -> throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to delete app draft listing",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted drafts cannot be modified",
            )
                .toStatusRuntimeException()
        }
        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(request.appDraftId, request.language)
            ?: throw appDraftListingNotFoundException(request.language)

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

        val canPublish = permissionService
            .hasPermission(HasPermissionRequest.PublishAppDraft(request.appDraftId, userId))
        if (!canPublish) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                HasPermissionRequest.ViewAppDraftExistence(request.appDraftId, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to publish app draft",
                )
                    .toStatusRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft.appPackage ?: throw ConsoleApiError(
            ErrorReason.ERROR_REASON_INTERNAL,
            "app draft has no package",
        )
            .toStatusRuntimeException()
        when {
            appDraft.published -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_PUBLISHED,
                "the app draft has already been published",
            )
                .toStatusRuntimeException()

            appDraft.publishing -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_PUBLISHING,
                "the app draft is already publishing",
            )
                .toStatusRuntimeException()

            appDraft.defaultListingLanguage == null -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_INTERNAL,
                "app draft has no default listing language",
            )
                .toStatusRuntimeException()

            appDraft.listings.any { it.icon == null } -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_INTERNAL,
                "one or more app listings have no icon",
            )
                .toStatusRuntimeException()

            App.existsById(appPackage.appId) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_CONFLICT,
                "an app with ID \"${appPackage.appId}\" has already been published",
            )
                .toStatusRuntimeException()
        }

        // Publish draft
        val job = JobBuilder
            .newJob(PublishAppDraftJob::class.java)
            .withIdentity(JobKey.jobKey(Identifier.generateNew(IdType.OPERATION)))
            .withDescription("Publish app draft ${request.appDraftId}")
            .usingJobData(JobDataKey.APP_DRAFT_ID, request.appDraftId)
            .requestRecovery()
            .storeDurably()
            .build()
        val trigger = TriggerBuilder.newTrigger().startNow().build()
        scheduler.get().scheduleJob(job, trigger)

        val backgroundOperation = BackgroundOperation(
            id = job.key.name,
            type = BackgroundOperationType.PUBLISH_APP_DRAFT,
            parentId = appDraft.id,
            createdAt = OffsetDateTime.now(),
            result = null,
            succeeded = false,
        )
            .also { it.persist() }
        appDraft.publishing = true

        val response = publishAppDraftResponse {
            operation = Operation.newBuilder().setName(backgroundOperation.id).setDone(false).build()
        }

        return Uni.createFrom().item { response }
    }

    private companion object {
        private val invalidPageTokenError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INVALID_REQUEST,
            "provided page token is invalid",
        )
            .toStatusRuntimeException()

        private fun appDraftListingNotFoundException(language: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "listing with language \"$language\" not found',"
        )
            .toStatusRuntimeException()

        private fun appDraftListingIconNotFoundException(language: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "listing with language \"$language\" has no icon",
        )
            .toStatusRuntimeException()

        private fun appDraftNotFoundException(appDraftId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app draft \"$appDraftId\" not found",
        )
            .toStatusRuntimeException()

        private fun organizationNotFoundException(organizationId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "organization \"$organizationId\" not found",
        )
            .toStatusRuntimeException()
    }
}
