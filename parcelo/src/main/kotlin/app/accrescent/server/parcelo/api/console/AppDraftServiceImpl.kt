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
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.AppDraftListing
import app.accrescent.server.parcelo.data.AppDraftListingIconUploadJob
import app.accrescent.server.parcelo.data.AppDraftUploadProcessingJob
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.data.Reviewer
import app.accrescent.server.parcelo.jobs.JobDataKey
import app.accrescent.server.parcelo.jobs.PublishAppDraftJob
import app.accrescent.server.parcelo.security.AuthnContextKey
import app.accrescent.server.parcelo.security.GrpcAuthenticationInterceptor
import app.accrescent.server.parcelo.security.GrpcRateLimitInterceptor
import app.accrescent.server.parcelo.security.IdType
import app.accrescent.server.parcelo.security.Identifier
import app.accrescent.server.parcelo.security.ObjectReference
import app.accrescent.server.parcelo.security.ObjectType
import app.accrescent.server.parcelo.security.Permission
import app.accrescent.server.parcelo.security.PermissionService
import app.accrescent.server.parcelo.validation.GrpcRequestValidationInterceptor
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.longrunning.Operation
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.timestamp
import io.grpc.Status
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
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

// 1 MiB
private const val MAX_ICON_SIZE_BYTES = 1048576

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

        val canCreateAppDraft = permissionService.hasPermission(
            ObjectReference(ObjectType.ORGANIZATION, request.organizationId),
            Permission.CREATE_APP_DRAFT,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canCreateAppDraft) {
            val orgExists = Organization.existsById(request.organizationId)
            val canViewOrgExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.ORGANIZATION, request.organizationId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!orgExists || !canViewOrgExistence) {
                organizationNotFoundException(request.organizationId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription(
                        "insufficient permission to create app drafts in organization " +
                                "\"${request.organizationId}\""
                    )
                    .asRuntimeException()
            }
        }

        val organization = Organization
            .findById(request.organizationId)
            ?: throw organizationNotFoundException(request.organizationId)
        val orgActiveAppDraftLimit = organization.activeAppDraftLimit
        val orgActiveAppDraftCount = AppDraft.countActiveInOrganization(request.organizationId)
        if (orgActiveAppDraftCount >= orgActiveAppDraftLimit) {
            throw Status
                .RESOURCE_EXHAUSTED
                .withDescription(
                    "organization limit of $orgActiveAppDraftLimit active app drafts already reached"
                )
                .asRuntimeException()
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
        AppDraftAcl(
            appDraftId = appDraft.id,
            userId = userId,
            canDelete = true,
            canPublish = false,
            canReplacePackage = true,
            canReview = false,
            canSubmit = true,
            canUpdate = true,
            canView = true,
            canViewExistence = true,
        )
            .persist()

        val response = createAppDraftResponse {
            appDraftId = appDraft.id
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppDraft(request: GetAppDraftRequest): Uni<GetAppDraftResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.VIEW,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canView) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to view app draft")
                    .asRuntimeException()
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

        val canReplacePackage = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.REPLACE_PACKAGE,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canReplacePackage) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to replace package")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
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

        val canDownload = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.DOWNLOAD,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canDownload) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to download app draft")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft.appPackage ?: throw Status
            .NOT_FOUND
            .withDescription("app draft \"${request.appDraftId}\" has no package")
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

        val canUpdate = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.UPDATE,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canUpdate) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to update app draft")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
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

        val canSubmit = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.SUBMIT,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canSubmit) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to submit app draft")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft.appPackage ?: throw Status
            .FAILED_PRECONDITION
            .withDescription("draft must have a package uploaded before it can be submitted")
            .asRuntimeException()
        val defaultListingLanguage = appDraft.defaultListingLanguage
        val orgPublishedAppLimit = appDraft.organization.publishedAppLimit
        val orgPublishedAppCount = App.countInOrganization(appDraft.organizationId)
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

            orgPublishedAppCount >= orgPublishedAppLimit -> throw Status
                .RESOURCE_EXHAUSTED
                .withDescription(
                    "organization limit of $orgPublishedAppLimit published apps already reached"
                )
                .asRuntimeException()
        }

        // Assign a reviewer
        val reviewer = Reviewer.findRandom() ?: throw Status
            .FAILED_PRECONDITION
            .withDescription("no reviewers available to assign")
            .asRuntimeException()
        val existingAcl = AppDraftAcl.findByAppDraftIdAndUserId(request.appDraftId, reviewer.userId)
        if (existingAcl == null) {
            AppDraftAcl(
                appDraftId = request.appDraftId,
                userId = reviewer.userId,
                canDelete = false,
                canPublish = false,
                canReplacePackage = false,
                canReview = true,
                canSubmit = false,
                canUpdate = false,
                canView = false,
                canViewExistence = true,
            )
                .persist()
        } else {
            existingAcl.canReview = true
            existingAcl.canViewExistence = true
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

        val canDelete = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.DELETE,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canDelete) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to delete app draft")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
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

        val canCreateListing = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.CREATE_LISTING,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canCreateListing) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to create app draft listing")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (AppDraftListing.exists(request.appDraftId, request.language)) {
            throw Status
                .ALREADY_EXISTS
                .withDescription(
                    "an app listing for app draft \"${request.appDraftId}\" with language " +
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

        val canReplaceIcon = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.REPLACE_LISTING_ICON,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canReplaceIcon) {
            val draftExists = AppDraft.existsById(request.appDraftId)
            val listingExists = AppDraftListing.exists(request.appDraftId, request.language)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw when {
                !draftExists || !canViewExistence -> appDraftNotFoundException(request.appDraftId)
                !listingExists -> appDraftListingNotFoundException(request.language)
                else -> throw Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to replace app listing icon")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
        }
        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(request.appDraftId, request.language)
            ?: throw appDraftListingNotFoundException(request.language)

        val blobInfo = BlobInfo
            .newBuilder(config.draftListingIconUploadBucket(), UUID.randomUUID().toString()).build()
        val uploadUrl = storage.signUrl(
            blobInfo,
            UPLOAD_URL_EXPIRATION_SECONDS,
            TimeUnit.SECONDS,
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withExtHeaders(
                mapOf("X-Goog-Content-Length-Range" to "0,$MAX_ICON_SIZE_BYTES")
            ),
        )

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
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.DOWNLOAD_LISTING_ICONS,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canDownload) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to download app draft listing icon")
                    .asRuntimeException()
            }
        }

        val appDraftListing = AppDraftListing
            .findByAppDraftIdAndLanguage(request.appDraftId, request.language)
            ?: throw appDraftListingNotFoundException(request.language)
        val icon = appDraftListing.icon ?: throw appDraftListingIconNotFoundException(request.language)

        val iconBlob = BlobInfo.newBuilder(icon.bucketId, icon.objectId).build()
        val downloadUrl = storage.signUrl(
            iconBlob,
            DOWNLOAD_URL_EXPIRATION_SECONDS,
            TimeUnit.SECONDS,
            Storage.SignUrlOption.withV4Signature(),
        )

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

        val canDelete = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.DELETE_LISTING,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canDelete) {
            val draftExists = AppDraft.existsById(request.appDraftId)
            val listingExists = AppDraftListing.exists(request.appDraftId, request.language)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw when {
                !draftExists || !canViewExistence -> appDraftNotFoundException(request.appDraftId)
                !listingExists -> appDraftListingNotFoundException(request.language)
                else -> throw Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to delete app draft listing")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        if (appDraft.submitted) {
            throw Status
                .FAILED_PRECONDITION
                .withDescription("submitted drafts cannot be modified")
                .asRuntimeException()
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

        val canPublish = permissionService.hasPermission(
            ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
            Permission.PUBLISH,
            ObjectReference(ObjectType.USER, userId),
        )
        if (!canPublish) {
            val exists = AppDraft.existsById(request.appDraftId)
            val canViewExistence = permissionService.hasPermission(
                ObjectReference(ObjectType.APP_DRAFT, request.appDraftId),
                Permission.VIEW_EXISTENCE,
                ObjectReference(ObjectType.USER, userId),
            )

            throw if (!exists || !canViewExistence) {
                appDraftNotFoundException(request.appDraftId)
            } else {
                Status
                    .PERMISSION_DENIED
                    .withDescription("insufficient permission to publish app draft")
                    .asRuntimeException()
            }
        }

        val appDraft = AppDraft
            .findById(request.appDraftId)
            ?: throw appDraftNotFoundException(request.appDraftId)
        val appPackage = appDraft.appPackage ?: throw Status
            .INTERNAL
            .withDescription("app draft has no package")
            .asRuntimeException()
        when {
            appDraft.published -> throw Status
                .ALREADY_EXISTS
                .withDescription("the app draft has already been published")
                .asRuntimeException()

            appDraft.publishing -> throw Status
                .ALREADY_EXISTS
                .withDescription("the app draft is already publishing")
                .asRuntimeException()

            appDraft.defaultListingLanguage == null -> throw Status
                .INTERNAL
                .withDescription("app draft has no default listing language")
                .asRuntimeException()

            appDraft.listings.any { it.icon == null } -> throw Status
                .DATA_LOSS
                .withDescription("one or more app listings have no icon")
                .asRuntimeException()

            App.existsById(appPackage.appId) -> throw Status
                .ALREADY_EXISTS
                .withDescription("an app with ID \"${appPackage.appId}\" has already been published")
                .asRuntimeException()
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
        private val invalidPageTokenError = Status
            .INVALID_ARGUMENT
            .withDescription("provided page token is invalid")
            .asRuntimeException()

        private fun appDraftListingNotFoundException(language: String) = Status
            .NOT_FOUND
            .withDescription("listing with language \"$language\" not found")
            .asRuntimeException()

        private fun appDraftListingIconNotFoundException(language: String) = Status
            .NOT_FOUND
            .withDescription("listing with language \"$language\" has no icon")
            .asRuntimeException()

        private fun appDraftNotFoundException(appDraftId: String) = Status
            .NOT_FOUND
            .withDescription("app draft \"$appDraftId\" not found")
            .asRuntimeException()

        private fun organizationNotFoundException(organizationId: String) = Status
            .NOT_FOUND
            .withDescription("organization \"$organizationId\" not found")
            .asRuntimeException()
    }
}
