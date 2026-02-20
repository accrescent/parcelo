// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.console

import app.accrescent.console.v1alpha1.AppEditService
import app.accrescent.console.v1alpha1.CreateAppEditListingIconUploadOperationRequest
import app.accrescent.console.v1alpha1.CreateAppEditListingIconUploadOperationResponse
import app.accrescent.console.v1alpha1.CreateAppEditListingRequest
import app.accrescent.console.v1alpha1.CreateAppEditListingResponse
import app.accrescent.console.v1alpha1.CreateAppEditRequest
import app.accrescent.console.v1alpha1.CreateAppEditResponse
import app.accrescent.console.v1alpha1.CreateAppEditUploadOperationRequest
import app.accrescent.console.v1alpha1.CreateAppEditUploadOperationResponse
import app.accrescent.console.v1alpha1.DeleteAppEditListingRequest
import app.accrescent.console.v1alpha1.DeleteAppEditListingResponse
import app.accrescent.console.v1alpha1.DeleteAppEditRequest
import app.accrescent.console.v1alpha1.DeleteAppEditResponse
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.GetAppEditDownloadInfoRequest
import app.accrescent.console.v1alpha1.GetAppEditDownloadInfoResponse
import app.accrescent.console.v1alpha1.GetAppEditRequest
import app.accrescent.console.v1alpha1.GetAppEditResponse
import app.accrescent.console.v1alpha1.ListAppEditsRequest
import app.accrescent.console.v1alpha1.ListAppEditsResponse
import app.accrescent.console.v1alpha1.SubmitAppEditRequest
import app.accrescent.console.v1alpha1.SubmitAppEditResponse
import app.accrescent.console.v1alpha1.UpdateAppEditRequest
import app.accrescent.console.v1alpha1.UpdateAppEditResponse
import app.accrescent.console.v1alpha1.appEdit
import app.accrescent.console.v1alpha1.appPackage
import app.accrescent.console.v1alpha1.createAppEditListingIconUploadOperationResponse
import app.accrescent.console.v1alpha1.createAppEditListingResponse
import app.accrescent.console.v1alpha1.createAppEditResponse
import app.accrescent.console.v1alpha1.createAppEditUploadOperationResponse
import app.accrescent.console.v1alpha1.deleteAppEditListingResponse
import app.accrescent.console.v1alpha1.deleteAppEditResponse
import app.accrescent.console.v1alpha1.getAppEditDownloadInfoResponse
import app.accrescent.console.v1alpha1.getAppEditResponse
import app.accrescent.console.v1alpha1.listAppEditsResponse
import app.accrescent.console.v1alpha1.submitAppEditResponse
import app.accrescent.console.v1alpha1.updateAppEditResponse
import app.accrescent.parcelo.impl.v1.ListAppEditsPageToken
import app.accrescent.parcelo.impl.v1.listAppEditsPageToken
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppEditListing
import app.accrescent.server.parcelo.data.AppEditListingIconUploadJob
import app.accrescent.server.parcelo.data.AppEditRelationshipSet
import app.accrescent.server.parcelo.data.AppEditUploadProcessingJob
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.data.OrphanedBlob
import app.accrescent.server.parcelo.data.User
import app.accrescent.server.parcelo.jobs.JobDataKey
import app.accrescent.server.parcelo.jobs.PublishAppEditJob
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

private const val ANDROID_PERMISSION_PREFIX = "android.permission."
private val PERMISSIONS_ALLOWED_WITHOUT_REVIEW = setOf(
    // Basic network functionality is not considered review-worthy for our purposes.
    "android.permission.ACCESS_NETWORK_STATE",
    // Not security sensitive since it applied to only the currently focused application.
    "android.permission.CAPTURE_KEYBOARD",
    // Foreground services are not inherently security-sensitive, though they do have an effect on
    // battery life.
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CAMERA",
    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.FOREGROUND_SERVICE_HEALTH",
    "android.permission.FOREGROUND_SERVICE_LOCATION",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
    "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
    // Basic network functionality is not considered review-worthy for our purposes.
    "android.permission.INTERNET",
    // According to Android documentation, "holding this permission does not have any security
    // implications".
    //
    // https://developer.android.com/reference/android/Manifest.permission#RECEIVE_BOOT_COMPLETED
    "android.permission.RECEIVE_BOOT_COMPLETED",
    // Android doesn't expose biometric authentication data directly to applications.
    "android.permission.USE_BIOMETRIC",
    // Deprecated practical equivalent to a subset of USE_BIOMETRIC.
    "android.permission.USE_FINGERPRINT",
    // Haptics aren't worth reviewing for our purposes.
    "android.permission.VIBRATE",
)

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 50u

@GrpcService
@RegisterInterceptor(GrpcAuthenticationInterceptor::class)
@RegisterInterceptor(GrpcRequestValidationInterceptor::class)
@RegisterInterceptor(GrpcRateLimitInterceptor::class)
class AppEditServiceImpl @Inject constructor(
    private val config: ParceloConfig,
    private val permissionService: PermissionService,
    private val scheduler: Instance<Scheduler>,
    private val storage: Storage,
) : AppEditService {
    @JvmRecord
    data class AppEditAssignedToYouForReviewEmail(
        val appEditId: String,
    ) : MailTemplate.MailTemplateInstance

    @Transactional
    override fun createAppEdit(request: CreateAppEditRequest): Uni<CreateAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreateAppEdit = permissionService
            .hasPermission(HasPermissionRequest.CreateAppEdit(request.appId, userId))
        if (!canCreateAppEdit) {
            val appExists = App.existsById(request.appId)
            val canViewAppExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppExistence(request.appId, userId))

            throw if (!canViewAppExistence || !appExists) {
                appNotFoundException(request.appId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to create edits for app \"${request.appId}\"",
                )
                    .toStatusRuntimeException()
            }
        }
        val app = App.findById(request.appId) ?: throw appNotFoundException(request.appId)

        val appActiveEditLimit = app.activeEditLimit
        val appActiveEditCount = AppEdit.countActiveForApp(app.id)
        if (appActiveEditCount >= appActiveEditLimit) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED,
                "app limit of $appActiveEditLimit active edits already reached",
            )
                .toStatusRuntimeException()
        }

        val appEdit = AppEdit(
            id = Identifier.generateNew(IdType.APP_EDIT),
            appId = request.appId,
            createdAt = OffsetDateTime.now(),
            expectedAppEntityTag = app.entityTag,
            defaultListingLanguage = app.defaultListingLanguage,
            appPackageId = app.appPackageId,
            submittedAt = null,
            reviewId = null,
            publishing = false,
            publishedAt = null,
        )
            .also { it.persist() }
        for (listing in app.listings) {
            AppEditListing(
                id = UUID.randomUUID(),
                appEditId = appEdit.id,
                language = listing.id.language,
                name = listing.name,
                shortDescription = listing.shortDescription,
                iconImageId = listing.iconImageId,
            )
                .persist()
        }

        val response = createAppEditResponse { appEditId = appEdit.id }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppEdit(request: GetAppEditRequest): Uni<GetAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canView = permissionService
            .hasPermission(HasPermissionRequest.ViewAppEdit(request.appEditId, userId))
        if (!canView) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to view app edit",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        val response = getAppEditResponse {
            this.appEdit = appEdit {
                id = appEdit.id
                createdAt = timestamp {
                    seconds = appEdit.createdAt.toEpochSecond()
                    nanos = appEdit.createdAt.nano
                }
                defaultListingLanguage = appEdit.defaultListingLanguage
                appPackage = appPackage {
                    appId = appEdit.appPackage.appId
                    versionCode = appEdit.appPackage.versionCode.toLong()
                    versionName = appEdit.appPackage.versionName
                    targetSdk = appEdit.appPackage.targetSdk.toLong()
                }
                appEdit.submittedAt?.let { submissionTimestamp ->
                    submittedAt = timestamp {
                        seconds = submissionTimestamp.toEpochSecond()
                        nanos = submissionTimestamp.nano
                    }
                }
                appEdit.publishedAt?.let { publicationTimestamp ->
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
    override fun listAppEdits(request: ListAppEditsRequest): Uni<ListAppEditsResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val lastAppEditId = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.UrlSafe.decode(request.pageToken)
                val pageToken = ListAppEditsPageToken.parseFrom(tokenBytes)
                if (!pageToken.hasLastAppEditId()) {
                    throw invalidPageTokenError
                }

                pageToken.lastAppEditId
            } catch (_: IllegalArgumentException) {
                throw invalidPageTokenError
            } catch (_: InvalidProtocolBufferException) {
                throw invalidPageTokenError
            }
        } else {
            null
        }

        val appEdits = AppEdit
            .findForAppAndUserByQuery(request.appId, userId, pageSize, lastAppEditId)
            .map { appEdit ->
                appEdit {
                    id = appEdit.id
                    createdAt = timestamp {
                        seconds = appEdit.createdAt.toEpochSecond()
                        nanos = appEdit.createdAt.nano
                    }
                    defaultListingLanguage = appEdit.defaultListingLanguage
                    appPackage = appPackage {
                        appId = appEdit.appPackage.appId
                        versionCode = appEdit.appPackage.versionCode.toLong()
                        versionName = appEdit.appPackage.versionName
                        targetSdk = appEdit.appPackage.targetSdk.toLong()
                    }
                    appEdit.submittedAt?.let { submissionTimestamp ->
                        submittedAt = timestamp {
                            seconds = submissionTimestamp.toEpochSecond()
                            nanos = submissionTimestamp.nano
                        }
                    }
                    appEdit.publishedAt?.let { publicationTimestamp ->
                        publishedAt = timestamp {
                            seconds = publicationTimestamp.toEpochSecond()
                            nanos = publicationTimestamp.nano
                        }
                    }
                }
            }

        val response = if (appEdits.isNotEmpty()) {
            // Set a page token indicating there may be more results
            val pageToken = listAppEditsPageToken {
                this.lastAppEditId = appEdits.last().id
            }
            val encodedPageToken = Base64.UrlSafe.encode(pageToken.toByteArray())

            listAppEditsResponse {
                this.appEdits.addAll(appEdits)
                nextPageToken = encodedPageToken
            }
        } else {
            listAppEditsResponse {}
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun createAppEditUploadOperation(
        request: CreateAppEditUploadOperationRequest,
    ): Uni<CreateAppEditUploadOperationResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReplacePackage = permissionService
            .hasPermission(HasPermissionRequest.ReplaceAppEditPackage(request.appEditId, userId))
        if (!canReplacePackage) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to replace package",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        if (appEdit.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted edits cannot be modified",
            )
                .toStatusRuntimeException()
        }

        val blobInfo = BlobInfo
            .newBuilder(config.buckets().editUpload(), UUID.randomUUID().toString()).build()
        val uploadUrl = storage.signUploadUrl(blobInfo, UploadType.APK_SET)

        val backgroundOperation = BackgroundOperation(
            id = Identifier.generateNew(IdType.OPERATION),
            type = BackgroundOperationType.UPLOAD_APP_EDIT,
            parentId = request.appEditId,
            createdAt = OffsetDateTime.now(),
            result = null,
            succeeded = false,
        )
            .also { it.persist() }
        AppEditUploadProcessingJob(
            appEditId = request.appEditId,
            bucketId = blobInfo.bucket,
            objectId = blobInfo.name,
            backgroundOperationId = backgroundOperation.id,
        )
            .persist()

        val response = createAppEditUploadOperationResponse {
            apkSetUploadUrl = uploadUrl.toString()
            processingOperation = Operation.newBuilder().setName(backgroundOperation.id).build()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun getAppEditDownloadInfo(
        request: GetAppEditDownloadInfoRequest,
    ): Uni<GetAppEditDownloadInfoResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canDownload = permissionService
            .hasPermission(HasPermissionRequest.DownloadAppEdit(request.appEditId, userId))
        if (!canDownload) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to download app edit",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        val appPackage = appEdit.appPackage

        val apkSetBlob = BlobInfo.newBuilder(appPackage.bucketId, appPackage.objectId).build()
        val downloadUrl = storage.signDownloadUrl(apkSetBlob)

        val response = getAppEditDownloadInfoResponse {
            apkSetUrl = downloadUrl.toString()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun updateAppEdit(request: UpdateAppEditRequest): Uni<UpdateAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canUpdate = permissionService
            .hasPermission(HasPermissionRequest.UpdateAppEdit(request.appEditId, userId))
        if (!canUpdate) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to update app edit",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)

        // Update the app edit based on the update mask
        if (request.updateMask.pathsList.contains("default_listing_language")) {
            // Ensure referential integrity by requiring the default listing language to match an
            // existing listing for the app edit
            if (appEdit.hasListingForLanguage(request.defaultListingLanguage)) {
                appEdit.defaultListingLanguage = request.defaultListingLanguage
            } else {
                throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_CONSTRAINT_VIOLATION,
                    "no listing exists for default listing language " +
                            "\"${request.defaultListingLanguage}\"",
                )
                    .toStatusRuntimeException()
            }
        }

        return Uni.createFrom().item { updateAppEditResponse {} }
    }

    @Transactional
    override fun submitAppEdit(request: SubmitAppEditRequest): Uni<SubmitAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        // Check permissions
        val canSubmit = permissionService
            .hasPermission(HasPermissionRequest.SubmitAppEdit(request.appEditId, userId))
        if (!canSubmit) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to submit app edit",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)

        // Check preconditions
        when {
            appEdit.published -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_PUBLISHED,
                "the app edit has already been published",
            )
                .toStatusRuntimeException()

            appEdit.publishing -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_PUBLISHING,
                "the app edit is already publishing",
            )
                .toStatusRuntimeException()

            appEdit.submitted -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_SUBMITTED,
                "app edit already submitted",
            )
                .toStatusRuntimeException()

            appEdit.expectedAppEntityTag != appEdit.app.entityTag -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INVALIDATED,
                "app edit has been invalidated by another submission",
            )
                .toStatusRuntimeException()

            !appEdit.allListingsHaveIcon() -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
                "all edit listings must have an icon",
            )
                .toStatusRuntimeException()

            AppEdit.activeAndSubmittedEditExistsForAppId(appEdit.appId) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_CONFLICT,
                "an active edit is already submitted for this app",
            )
                .toStatusRuntimeException()
        }

        // Determine whether any permission changes require review
        val appPermissions = appEdit.app.appPackage.permissions.associateBy { it.name }
        val editPermissions = appEdit.appPackage.permissions
        val permissionChangesRequiringReview = editPermissions
            .filter { permission ->
                val isAndroidPermission = permission.name.startsWith(ANDROID_PERMISSION_PREFIX)
                val isAllowedWithoutReview = PERMISSIONS_ALLOWED_WITHOUT_REVIEW
                    .contains(permission.name)
                val oldPermission = appPermissions[permission.name]
                val isMorePermissive = oldPermission == null || run {
                    val oldMaxSdkVersion = oldPermission.maxSdkVersion
                    val maxSdkVersion = permission.maxSdkVersion

                    oldMaxSdkVersion != null
                            && (maxSdkVersion == null || maxSdkVersion > oldMaxSdkVersion)
                }

                isAndroidPermission && isMorePermissive && !isAllowedWithoutReview
            }

        // Determine whether any listing changes require review
        val appListings = appEdit.app.listings.associateBy { it.id.language }
        val editListings = appEdit.listings
        val descriptionChangesRequiringReview = editListings
            .filter { listing ->
                val oldListing = appListings[listing.language]
                val isNewOrModified = oldListing == null
                        || oldListing.name != listing.name
                        || oldListing.shortDescription != listing.shortDescription
                        || oldListing.iconImageId != listing.iconImageId

                isNewOrModified
            }

        val requiresReview = permissionChangesRequiringReview.isNotEmpty()
                || descriptionChangesRequiringReview.isNotEmpty()
        val response = if (requiresReview) {
            // Assign a reviewer
            val reviewer = User.findRandomReviewer() ?: throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ASSIGNEE_UNAVAILABLE,
                "no reviewers available to assign",
            )
                .toStatusRuntimeException()
            val existingRelationshipSet = AppEditRelationshipSet
                .findByAppEditIdAndUserId(request.appEditId, reviewer.id)
            if (existingRelationshipSet == null) {
                AppEditRelationshipSet(
                    appEditId = request.appEditId,
                    userId = reviewer.id,
                    reviewer = true,
                )
                    .persist()
            } else {
                existingRelationshipSet.reviewer = true
            }

            // Notify the reviewer that they are assigned to this edit before the transaction is
            // committed. This approach means reviewers may receive notifications for edits they
            // aren't actually assigned to if the transaction is rolled back. However, it also means
            // we will always send a notification for edits they are actually assigned to, which we
            // want to guarantee to ascertain timely reviews.
            AppEditAssignedToYouForReviewEmail(request.appEditId)
                .to(reviewer.email)
                .subject("A new app edit has been assigned to you")
                .sendAndAwait()

            submitAppEditResponse {}
        } else {
            // Publish the edit immediately
            val job = JobBuilder
                .newJob(PublishAppEditJob::class.java)
                .withIdentity(JobKey.jobKey(Identifier.generateNew(IdType.OPERATION)))
                .withDescription("Publish app edit \"${request.appEditId}\"")
                .usingJobData(JobDataKey.APP_EDIT_ID, request.appEditId)
                .requestRecovery()
                .storeDurably()
                .build()
            val trigger = TriggerBuilder.newTrigger().startNow().build()
            scheduler.get().scheduleJob(job, trigger)

            val backgroundOperation = BackgroundOperation(
                id = job.key.name,
                type = BackgroundOperationType.PUBLISH_APP_EDIT,
                parentId = appEdit.id,
                createdAt = OffsetDateTime.now(),
                result = null,
                succeeded = false,
            )
                .also { it.persist() }
            appEdit.publishing = true

            submitAppEditResponse {
                operation = Operation.newBuilder().setName(backgroundOperation.id).build()
            }
        }
        appEdit.submittedAt = OffsetDateTime.now()

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun deleteAppEdit(request: DeleteAppEditRequest): Uni<DeleteAppEditResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        // Check permissions
        val canDelete = permissionService
            .hasPermission(HasPermissionRequest.DeleteAppEdit(request.appEditId, userId))
        if (!canDelete) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to delete app edit",
                )
                    .toStatusRuntimeException()
            }
        }

        // Check preconditions
        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        if (appEdit.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted app edits cannot be deleted",
            )
                .toStatusRuntimeException()
        }

        // Delete the associated package if it's different from that of the published app
        if (appEdit.appPackageId != appEdit.app.appPackageId) {
            OrphanedBlob(
                bucketId = appEdit.appPackage.bucketId,
                objectId = appEdit.appPackage.objectId,
                orphanedOn = OffsetDateTime.now(),
            )
                .persist()
            appEdit.appPackage.delete()
        }
        appEdit.delete()

        return Uni.createFrom().item { deleteAppEditResponse {} }
    }

    @Transactional
    override fun createAppEditListing(
        request: CreateAppEditListingRequest,
    ): Uni<CreateAppEditListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canCreateListing = permissionService
            .hasPermission(HasPermissionRequest.CreateAppEditListing(request.appEditId, userId))
        if (!canCreateListing) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to create app edit listing",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        when {
            AppEditListing.exists(request.appEditId, request.language) -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_ALREADY_EXISTS,
                "an app listing with that language already exists",
            )
                .toStatusRuntimeException()

            appEdit.submitted -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted edits cannot be modified",
            )
                .toStatusRuntimeException()
        }

        AppEditListing(
            id = UUID.randomUUID(),
            appEditId = request.appEditId,
            language = request.language,
            name = request.name,
            shortDescription = request.shortDescription,
            iconImageId = null,
        )
            .persist()

        return Uni.createFrom().item { createAppEditListingResponse {} }
    }

    @Transactional
    override fun createAppEditListingIconUploadOperation(
        request: CreateAppEditListingIconUploadOperationRequest,
    ): Uni<CreateAppEditListingIconUploadOperationResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canReplaceIcon = permissionService
            .hasPermission(HasPermissionRequest.ReplaceAppEditListingIcon(request.appEditId, userId))
        if (!canReplaceIcon) {
            val editExists = AppEdit.existsById(request.appEditId)
            val listingExists = AppEditListing.exists(request.appEditId, request.language)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw when {
                !editExists || !canViewExistence -> appEditNotFoundException(request.appEditId)
                !listingExists -> appEditListingNotFoundException(request.language)
                else -> throw ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to replace app listing icon",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        if (appEdit.submitted) {
            throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted edits cannot be modified",
            )
                .toStatusRuntimeException()
        }
        val appEditListing = AppEditListing
            .findByAppEditIdAndLanguage(request.appEditId, request.language)
            ?: throw appEditNotFoundException(request.language)

        val blobInfo = BlobInfo
            .newBuilder(config.buckets().editListingIconUpload(), UUID.randomUUID().toString())
            .build()
        val uploadUrl = storage.signUploadUrl(blobInfo, UploadType.ICON)

        val backgroundOperation = BackgroundOperation(
            id = Identifier.generateNew(IdType.OPERATION),
            type = BackgroundOperationType.UPLOAD_APP_EDIT_LISTING_ICON,
            parentId = request.appEditId,
            createdAt = OffsetDateTime.now(),
            result = null,
            succeeded = false,
        )
            .also { it.persist() }
        AppEditListingIconUploadJob(
            appEditListingId = appEditListing.id,
            bucketId = blobInfo.bucket,
            objectId = blobInfo.name,
            backgroundOperationId = backgroundOperation.id,
        )
            .persist()

        val response = createAppEditListingIconUploadOperationResponse {
            this.uploadUrl = uploadUrl.toString()
            processingOperation = Operation.newBuilder().setName(backgroundOperation.id).build()
        }

        return Uni.createFrom().item { response }
    }

    @Transactional
    override fun deleteAppEditListing(
        request: DeleteAppEditListingRequest,
    ): Uni<DeleteAppEditListingResponse> {
        val userId = AuthnContextKey.USER_ID.get()

        val canDelete = permissionService
            .hasPermission(HasPermissionRequest.DeleteAppEditListing(request.appEditId, userId))
        if (!canDelete) {
            val exists = AppEdit.existsById(request.appEditId)
            val canViewExistence = permissionService
                .hasPermission(HasPermissionRequest.ViewAppEditExistence(request.appEditId, userId))

            throw if (!exists || !canViewExistence) {
                appEditNotFoundException(request.appEditId)
            } else {
                ConsoleApiError(
                    ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION,
                    "insufficient permission to delete app edit listing",
                )
                    .toStatusRuntimeException()
            }
        }

        val appEdit = AppEdit
            .findById(request.appEditId)
            ?: throw appEditNotFoundException(request.appEditId)
        when {
            appEdit.submitted -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
                "submitted edits cannot be modified",
            )
                .toStatusRuntimeException()

            request.language == appEdit.defaultListingLanguage -> throw ConsoleApiError(
                ErrorReason.ERROR_REASON_CONSTRAINT_VIOLATION,
                "cannot delete listing for the default listing language",
            )
                .toStatusRuntimeException()
        }
        val appEditListing = AppEditListing
            .findByAppEditIdAndLanguage(request.appEditId, request.language)
            ?: throw appEditListingNotFoundException(request.language)

        // Delete the associated icon if one exists
        appEditListing.icon?.let { icon ->
            OrphanedBlob(
                bucketId = icon.bucketId,
                objectId = icon.objectId,
                orphanedOn = OffsetDateTime.now()
            )
                .persist()
            icon.delete()
        }

        // Delete the listing
        appEditListing.delete()

        return Uni.createFrom().item { deleteAppEditListingResponse {} }
    }

    private companion object {
        private val invalidPageTokenError = ConsoleApiError(
            ErrorReason.ERROR_REASON_INVALID_REQUEST,
            "provided page token is invalid",
        )
            .toStatusRuntimeException()

        private fun appNotFoundException(appId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app with ID \"$appId\" not found",
        )
            .toStatusRuntimeException()

        private fun appEditNotFoundException(appEditId: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "app edit with ID \"$appEditId\" not found",
        )
            .toStatusRuntimeException()

        private fun appEditListingNotFoundException(language: String) = ConsoleApiError(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND,
            "listing with language \"$language\" not found",
        )
            .toStatusRuntimeException()
    }
}
