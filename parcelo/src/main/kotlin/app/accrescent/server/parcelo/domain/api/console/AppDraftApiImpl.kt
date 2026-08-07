// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.api.console

import app.accrescent.parcelo.impl.v1.ListAppDraftListingsPageToken
import app.accrescent.parcelo.impl.v1.ListAppDraftsPageToken
import app.accrescent.parcelo.impl.v1.listAppDraftListingsPageToken
import app.accrescent.parcelo.impl.v1.listAppDraftsPageToken
import app.accrescent.server.parcelo.core.bindMapLeft
import app.accrescent.server.parcelo.core.toEitherBind
import app.accrescent.server.parcelo.domain.IdGenerator
import app.accrescent.server.parcelo.domain.IdType
import app.accrescent.server.parcelo.domain.ports.driven.blobstorage.BlobId
import app.accrescent.server.parcelo.domain.ports.driven.blobstorage.BlobStorage
import app.accrescent.server.parcelo.domain.ports.driven.blobstorage.BlobStorageBackend
import app.accrescent.server.parcelo.domain.ports.driven.blobstorage.UploadType
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ExternalBlob
import app.accrescent.server.parcelo.domain.ports.driven.datastore.HasPermissionRequest
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftUpload
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSource
import app.accrescent.server.parcelo.domain.ports.driven.timestampsource.TimestampSource
import app.accrescent.server.parcelo.domain.ports.driving.console.ActiveAppDraftLimitExceededError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftApi
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftHasNoDefaultListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftHasNoPackageError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListingAlreadyExistsError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListingNotFoundError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftPackageNotFoundError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftSubmittedError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftSubmittedForAppIdError
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftListingResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftListingIconError
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftListingIconRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftListingIconResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftListingResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.InsufficientPermissionError
import app.accrescent.server.parcelo.domain.ports.driving.console.InvalidPageTokenError
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftListingsError
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftListingsRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftListingsResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftsError
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftsRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftsResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.PublishAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.PublishAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.PublishAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.PublishedAppLimitExceededError
import app.accrescent.server.parcelo.domain.ports.driving.console.ServerError
import app.accrescent.server.parcelo.domain.ports.driving.console.SubmitAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.SubmitAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftError
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftListingIconError
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftListingIconRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftListingIconResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.toServerError
import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.google.protobuf.InvalidProtocolBufferException
import kotlin.io.encoding.Base64
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraft as DataAppDraft
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListing as DataAppDraftListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackage as DataAppPackage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ListingLanguage as DataListingLanguage
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraft as ApiAppDraft
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListing as ApiAppDraftListing
import app.accrescent.server.parcelo.domain.ports.driving.console.AppPackage as ApiAppPackage
import app.accrescent.server.parcelo.domain.ports.driving.console.ListingLanguage as ApiListingLanguage

private val ACTIVE_APP_DRAFT_LIMIT = 3uL
private val PUBLISHED_APP_LIMIT = 1uL

class AppDraftApiImpl(
    private val dataStore: DataStore,
    private val objectStorage: BlobStorage<BlobId>,
    randomSource: RandomSource,
    private val timestampSource: TimestampSource,
    private val appDraftUploadBucketName: String,
    private val appDraftListingIconUploadBucketName: String,
) : AppDraftApi {
    private val idGenerator = IdGenerator(randomSource)

    override fun createAppDraft(
        callerUserId: String,
        request: CreateAppDraftRequest,
    ): Either<CreateAppDraftError, CreateAppDraftResponse> = either {
        val appDraftId = dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.CreateAppDraft(request.organizationId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            val activeAppDraftCount = tx.appDrafts
                .countActiveInOrganization(request.organizationId)
                .bindMapLeft(::toServerError)
            ensure(activeAppDraftCount < ACTIVE_APP_DRAFT_LIMIT) {
                ActiveAppDraftLimitExceededError(ACTIVE_APP_DRAFT_LIMIT)
            }

            val appDraftId = idGenerator.generateId(IdType.APP_DRAFT).bindMapLeft(::toServerError)
            tx.appDrafts
                .save(
                    DataAppDraft.Unsubmitted(
                        id = appDraftId,
                        organizationId = request.organizationId,
                        createTime = timestampSource.now(),
                        defaultAppDraftListingId = None,
                        appPackageId = None,
                    )
                )
                .bindMapLeft(::toServerError)
            appDraftId
        }
            .bindMapLeft(::toServerError)
            .bind()

        CreateAppDraftResponse(appDraftId)
    }

    override fun getAppDraft(
        callerUserId: String,
        request: GetAppDraftRequest,
    ): Either<GetAppDraftError, GetAppDraftResponse> = either {
        val appDraft = dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.ViewAppDraft(request.appDraftId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            tx.appDrafts
                .requireById(request.appDraftId)
                .bindMapLeft(::toServerError)
                .toApiResource(tx)
                .bind()
        }
            .bindMapLeft(::toServerError)
            .bind()

        GetAppDraftResponse(appDraft)
    }

    override fun listAppDrafts(
        callerUserId: String,
        request: ListAppDraftsRequest,
    ): Either<ListAppDraftsError, ListAppDraftsResponse> = either {
        val lastAppDraftId = request.pageToken?.let {
            try {
                val bytes = Base64.UrlSafe.decode(it)
                val token = ListAppDraftsPageToken.parseFrom(bytes)
                if (!token.hasLastAppDraftId()) {
                    raise(InvalidPageTokenError)
                }

                token.lastAppDraftId
            } catch (_: IllegalArgumentException) {
                raise(InvalidPageTokenError)
            } catch (_: InvalidProtocolBufferException) {
                raise(InvalidPageTokenError)
            }
        }

        val appDrafts = dataStore.runTxWithRetry { tx ->
            tx.appDrafts
                .findForOrganizationAndUserByQuery(
                    organizationId = request.organizationId,
                    userId = callerUserId,
                    maxResults = request.pageSize,
                    afterAppDraftId = lastAppDraftId,
                )
                .bindMapLeft(::toServerError)
                .map { appDraft -> appDraft.toApiResource(tx).bind() }
        }
            .bindMapLeft(::toServerError)
            .bind()
        val nextPageToken = if (appDrafts.isNotEmpty()) {
            val token = listAppDraftsPageToken { this.lastAppDraftId = appDrafts.last().id }
            Base64.UrlSafe.encode(token.toByteArray())
        } else {
            null
        }

        ListAppDraftsResponse(appDrafts, nextPageToken)
    }

    override fun uploadAppDraft(
        callerUserId: String,
        request: UploadAppDraftRequest,
    ): Either<UploadAppDraftError, UploadAppDraftResponse> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.ReplaceAppDraftPackage(request.appDraftId, callerUserId)
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts
                .requireById(request.appDraftId)
                .bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(request.appDraftId) }

            // Generate a signed URI for upload
            val objectKey =
                idGenerator.generateId(IdType.BLOB_OBJECT_KEY).bindMapLeft(::toServerError)
            val blobLocation = BlobId.Location(appDraftUploadBucketName, objectKey)
            val (storageBackend, uploadUri) = objectStorage
                .signUploadUri(type = UploadType.APK_SET, location = blobLocation)
                .bindMapLeft(::toServerError)

            // Persist a pending reference to the blob we intend to create
            val now = timestampSource.now()
            val blobId = idGenerator.generateId(IdType.EXTERNAL_BLOB).bindMapLeft(::toServerError)
            val blob = when (storageBackend) {
                BlobStorageBackend.GCS -> ExternalBlob.Gcs(
                    id = blobId,
                    createTime = now,
                    bucketName = blobLocation.bucketName,
                    objectKey = blobLocation.objectKey,
                    status = ExternalBlob.Status.Pending,
                )

                BlobStorageBackend.LOCAL -> ExternalBlob.Local(
                    id = blobId,
                    createTime = now,
                    bucketName = blobLocation.bucketName,
                    objectKey = blobLocation.objectKey,
                    status = ExternalBlob.Status.Pending,
                )
            }
            tx.externalBlobs.save(blob).bindMapLeft(::toServerError)

            // Upsert the app draft's pending upload
            val pendingUploadExists = tx.appDrafts
                .pendingUploadExistsByAppDraftId(appDraft.id)
                .bindMapLeft(::toServerError)
            if (pendingUploadExists) {
                tx.appDrafts
                    .deletePendingUploadByAppDraftId(appDraft.id)
                    .bindMapLeft(::toServerError)
            }
            tx.appDrafts
                .saveUpload(
                    PendingAppDraftUpload(
                        id = idGenerator
                            .generateId(IdType.PENDING_APP_DRAFT_UPLOAD)
                            .bindMapLeft(::toServerError),
                        appDraftId = appDraft.id,
                        externalBlobId = blob.id,
                        objectKey = objectKey,
                        createTime = now,
                        result = None,
                    )
                )
                .bindMapLeft(::toServerError)

            UploadAppDraftResponse(uploadUri)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun downloadAppDraft(
        callerUserId: String,
        request: DownloadAppDraftRequest
    ): Either<DownloadAppDraftError, DownloadAppDraftResponse> = either {
        val blob = dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.DownloadAppDraft(request.appDraftId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            tx.appPackages.findByAppDraftId(request.appDraftId)
                .bindMapLeft(::toServerError)
                .map {
                    tx.externalBlobs
                        .requireCommittedById(it.externalBlobId)
                        .bindMapLeft(::toServerError)
                }
        }
            .bindMapLeft(::toServerError)
            .bind()
            // The app draft with the ID used to find this package is guaranteed to exist since
            // permission was granted, so we know the package must be missing, not the app draft
            .toEitherBind { AppDraftPackageNotFoundError(request.appDraftId) }

        val uri = objectStorage
            .signDownloadUri(blob.toBlobId())
            .bindMapLeft(::toServerError)

        DownloadAppDraftResponse(uri)
    }

    override fun updateAppDraft(
        callerUserId: String,
        request: UpdateAppDraftRequest
    ): Either<UpdateAppDraftError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.UpdateAppDraft(request.appDraftId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(request.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(request.appDraftId) }

            val listingExists = tx.appDrafts
                .listingExistsByIdForAppDraft(request.defaultAppDraftListingId, request.appDraftId)
                .bindMapLeft(::toServerError)
            ensure(listingExists) { AppDraftListingNotFoundError(request.defaultAppDraftListingId) }

            tx.appDrafts
                .updateDefaultListing(request.appDraftId, Some(request.defaultAppDraftListingId))
                .bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun submitAppDraft(
        callerUserId: String,
        request: SubmitAppDraftRequest
    ): Either<SubmitAppDraftError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.SubmitAppDraft(request.appDraftId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(request.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft is DataAppDraft.Unsubmitted) { AppDraftSubmittedError(request.appDraftId) }
            val appPackageId = appDraft.appPackageId
                .toEitherBind { AppDraftHasNoPackageError(request.appDraftId) }
            ensure(appDraft.defaultAppDraftListingId.isSome()) {
                AppDraftHasNoDefaultListingError(request.appDraftId)
            }

            val appPackage = tx.appPackages.requireById(appPackageId).bindMapLeft(::toServerError)
            val appDraftSubmittedForAppId = tx.appDrafts
                .existsSubmittedForAppId(appPackage.appId)
                .bindMapLeft(::toServerError)
            ensure(!appDraftSubmittedForAppId) {
                AppDraftSubmittedForAppIdError(appPackage.appId.intoInner())
            }

            val publishedAppCount = tx.apps
                .countInOrganization(appDraft.organizationId)
                .bindMapLeft(::toServerError)
            ensure(publishedAppCount < PUBLISHED_APP_LIMIT) {
                PublishedAppLimitExceededError(PUBLISHED_APP_LIMIT)
            }

            tx.appDrafts
                .updateSubmitTime(request.appDraftId, timestampSource.now())
                .bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun deleteAppDraft(
        callerUserId: String,
        request: DeleteAppDraftRequest,
    ): Either<DeleteAppDraftError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(HasPermissionRequest.DeleteAppDraft(request.appDraftId, callerUserId))
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(request.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft is DataAppDraft.Unsubmitted) { AppDraftSubmittedError(request.appDraftId) }

            tx.appDrafts.deleteById(appDraft.id).bindMapLeft(::toServerError)

            appDraft.appPackageId.onSome { appPackageId ->
                val appPackage = tx.appPackages.requireById(appPackageId).bindMapLeft(::toServerError)
                tx.appPackages.deleteById(appPackage.id).bindMapLeft(::toServerError)
                tx.externalBlobs
                    .markDeleted(appPackage.externalBlobId, timestampSource.now())
                    .bindMapLeft(::toServerError)
            }
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun createAppDraftListing(
        callerUserId: String,
        request: CreateAppDraftListingRequest,
    ): Either<CreateAppDraftListingError, CreateAppDraftListingResponse> = either {
        val listingId = dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.CreateAppDraftListing(request.appDraftId, callerUserId)
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(request.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(request.appDraftId) }

            val dataStoreLanguage = request.language.toDataStoreRepresentation()
            val listingExists = tx.appDrafts
                .listingExistsByLanguageForAppDraft(request.appDraftId, dataStoreLanguage)
                .bindMapLeft(::toServerError)
            ensure(!listingExists) {
                AppDraftListingAlreadyExistsError(request.appDraftId, request.language.toString())
            }

            val listingId = idGenerator
                .generateId(IdType.APP_DRAFT_LISTING)
                .bindMapLeft(::toServerError)
            tx.appDrafts
                .saveListing(
                    DataAppDraftListing(
                        id = listingId,
                        appDraftId = request.appDraftId,
                        language = dataStoreLanguage,
                        name = request.name,
                        shortDescription = request.shortDescription,
                    )
                )
                .bindMapLeft(::toServerError)

            listingId
        }
            .bindMapLeft(::toServerError)
            .bind()

        CreateAppDraftListingResponse(listingId)
    }

    override fun getAppDraftListing(
        callerUserId: String,
        request: GetAppDraftListingRequest,
    ): Either<GetAppDraftListingError, GetAppDraftListingResponse> = either {
        val listing = dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.ViewAppDraftListing(request.appDraftListingId, callerUserId)
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft listing is guaranteed to exist since permission is granted
            tx.appDrafts.requireListingById(request.appDraftListingId).bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()

        GetAppDraftListingResponse(
            ApiAppDraftListing(
                id = listing.id,
                appDraftId = listing.appDraftId,
                language = listing.language.toString(),
                name = listing.name,
                shortDescription = listing.shortDescription,
            )
        )
    }

    override fun listAppDraftListings(
        callerUserId: String,
        request: ListAppDraftListingsRequest,
    ): Either<ListAppDraftListingsError, ListAppDraftListingsResponse> = either {
        val lastLanguage = request.nextPageToken?.let {
            try {
                val bytes = Base64.UrlSafe.decode(it)
                val token = ListAppDraftListingsPageToken.parseFrom(bytes)
                if (!token.hasLastLanguage()) {
                    raise(InvalidPageTokenError)
                }

                DataListingLanguage.fromString(token.lastLanguage)
                    .toEitherBind { InvalidPageTokenError }
            } catch (_: IllegalArgumentException) {
                raise(InvalidPageTokenError)
            } catch (_: InvalidProtocolBufferException) {
                raise(InvalidPageTokenError)
            }
        }

        val listings = dataStore.runTxWithRetry { tx ->
            tx.appDrafts
                .findListingsForAppDraftAndUserByQuery(
                    appDraftId = request.appDraftId,
                    userId = callerUserId,
                    maxResults = request.pageSize,
                    afterLanguage = lastLanguage,
                )
                .bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()
            .map { listing ->
                ApiAppDraftListing(
                    id = listing.id,
                    appDraftId = listing.appDraftId,
                    language = listing.language.toString(),
                    name = listing.name,
                    shortDescription = listing.shortDescription,
                )
            }
        val nextPageToken = if (listings.isNotEmpty()) {
            val token = listAppDraftListingsPageToken { this.lastLanguage = listings.last().language }
            Base64.UrlSafe.encode(token.toByteArray())
        } else {
            null
        }

        ListAppDraftListingsResponse(listings, nextPageToken)
    }

    override fun updateAppDraftListing(
        callerUserId: String,
        request: UpdateAppDraftListingRequest,
    ): Either<UpdateAppDraftListingError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.UpdateAppDraftListing(request.appDraftListingId, callerUserId)
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft listing is guaranteed to exist since permission is granted
            val listing = tx.appDrafts
                .requireListingById(request.appDraftListingId)
                .bindMapLeft(::toServerError)

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(listing.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(listing.appDraftId) }

            tx.appDrafts
                .updateListing(
                    listingId = request.appDraftListingId,
                    name = request.name,
                    shortDescription = request.shortDescription,
                )
                .bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun uploadAppDraftListingIcon(
        callerUserId: String,
        request: UploadAppDraftListingIconRequest,
    ): Either<UploadAppDraftListingIconError, UploadAppDraftListingIconResponse> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.UploadAppDraftListingIcon(
                        request.appDraftListingId,
                        callerUserId,
                    )
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft listing is guaranteed to exist since permission is granted
            val listing = tx.appDrafts
                .requireListingById(request.appDraftListingId)
                .bindMapLeft(::toServerError)

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(listing.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(listing.appDraftId) }

            // Generate a signed URI for upload
            val objectKey =
                idGenerator.generateId(IdType.BLOB_OBJECT_KEY).bindMapLeft(::toServerError)
            val blobLocation = BlobId.Location(appDraftListingIconUploadBucketName, objectKey)
            val (storageBackend, uploadUri) = objectStorage
                .signUploadUri(UploadType.ICON, blobLocation)
                .bindMapLeft(::toServerError)

            // Persist a pending reference to the blob we intend to create
            val now = timestampSource.now()
            val blobId = idGenerator.generateId(IdType.EXTERNAL_BLOB).bindMapLeft(::toServerError)
            val blob = when (storageBackend) {
                BlobStorageBackend.GCS -> ExternalBlob.Gcs(
                    id = blobId,
                    createTime = now,
                    bucketName = blobLocation.bucketName,
                    objectKey = blobLocation.objectKey,
                    status = ExternalBlob.Status.Pending,
                )

                BlobStorageBackend.LOCAL -> ExternalBlob.Local(
                    id = blobId,
                    createTime = now,
                    bucketName = blobLocation.bucketName,
                    objectKey = blobLocation.objectKey,
                    status = ExternalBlob.Status.Pending,
                )
            }
            tx.externalBlobs.save(blob).bindMapLeft(::toServerError)

            // Upsert the app draft listing's pending icon upload
            val pendingUploadExists = tx.appDrafts
                .pendingListingIconUploadExistsByListingId(listing.id)
                .bindMapLeft(::toServerError)
            if (pendingUploadExists) {
                tx.appDrafts
                    .deletePendingListingIconUploadByListingId(listing.id)
                    .bindMapLeft(::toServerError)
            }
            tx.appDrafts
                .saveListingIconUpload(
                    PendingAppDraftListingIconUpload(
                        id = idGenerator
                            .generateId(IdType.PENDING_APP_DRAFT_LISTING_ICON_UPLOAD)
                            .bindMapLeft(::toServerError),
                        appDraftListingId = listing.id,
                        externalBlobId = blob.id,
                        objectKey = objectKey,
                        createTime = now,
                        result = None,
                    )
                )
                .bindMapLeft(::toServerError)

            UploadAppDraftListingIconResponse(uploadUri)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun downloadAppDraftListingIcon(
        callerUserId: String,
        request: DownloadAppDraftListingIconRequest
    ): Either<DownloadAppDraftListingIconError, DownloadAppDraftListingIconResponse> {
        TODO()
    }

    override fun deleteAppDraftListing(
        callerUserId: String,
        request: DeleteAppDraftListingRequest
    ): Either<DeleteAppDraftListingError, Unit> = either {
        dataStore.runTxWithRetry { tx ->
            val hasPermission = tx.authz
                .hasPermission(
                    HasPermissionRequest.DeleteAppDraftListing(request.appDraftListingId, callerUserId)
                )
                .bindMapLeft(::toServerError)
            ensure(hasPermission) { InsufficientPermissionError }

            // App draft listing is guaranteed to exist since permission is granted
            val listing = tx.appDrafts
                .requireListingById(request.appDraftListingId)
                .bindMapLeft(::toServerError)

            // App draft is guaranteed to exist since permission is granted
            val appDraft = tx.appDrafts.requireById(listing.appDraftId).bindMapLeft(::toServerError)
            ensure(appDraft !is DataAppDraft.Submitted) { AppDraftSubmittedError(listing.appDraftId) }

            val isDefaultListing = appDraft.optionalDefaultAppDraftListingId
                .isSome { it == request.appDraftListingId }
            if (isDefaultListing) {
                tx.appDrafts
                    .updateDefaultListing(listing.appDraftId, None)
                    .bindMapLeft(::toServerError)
            }

            tx.appDrafts.deleteListingById(request.appDraftListingId).bindMapLeft(::toServerError)
        }
            .bindMapLeft(::toServerError)
            .bind()
    }

    override fun publishAppDraft(
        callerUserId: String,
        request: PublishAppDraftRequest
    ): Either<PublishAppDraftError, PublishAppDraftResponse> {
        TODO()
    }

    private fun DataAppDraft.toApiResource(
        tx: DataStore.Transaction,
    ): Either<ServerError, ApiAppDraft> = either {
        when (val appDraft = this@toApiResource) {
            is DataAppDraft.Unsubmitted -> ApiAppDraft.Unsubmitted(
                id = appDraft.id,
                createTime = appDraft.createTime,
                defaultAppDraftListingId = appDraft.defaultAppDraftListingId,
                appPackage = appDraft.appPackageId.map { id ->
                    tx.appPackages.requireById(id).bindMapLeft(::toServerError).toApiResource()
                },
            )

            is DataAppDraft.Submitted -> ApiAppDraft.Submitted(
                id = appDraft.id,
                createTime = appDraft.createTime,
                defaultAppDraftListingId = appDraft.defaultAppDraftListingId,
                appPackage = tx.appPackages
                    .requireById(appDraft.appPackageId)
                    .bindMapLeft(::toServerError)
                    .toApiResource(),
                submitTime = appDraft.submitTime,
            )
        }
    }

    private fun DataAppPackage.toApiResource(): ApiAppPackage = ApiAppPackage(
        androidApplicationId = this.appId,
        versionCode = this.versionCode,
        versionName = this.versionName,
        targetSdk = this.targetSdk,
    )

    private fun ApiListingLanguage.toDataStoreRepresentation(): DataListingLanguage {
        return when (this) {
            ApiListingLanguage.EN_US -> DataListingLanguage.EN_US
        }
    }

    private fun ExternalBlob<ExternalBlob.Status.Committed<*>>.toBlobId(): BlobId {
        return when (val version = this.status.version) {
            is ExternalBlob.GcsBlobVersion -> BlobId.Gcs(
                BlobId.Location(this.bucketName, this.objectKey),
                BlobId.Version.Gcs(version.generation, version.metaGeneration),
            )

            is ExternalBlob.LocalBlobVersion -> BlobId.Local(
                BlobId.Location(this.bucketName, this.objectKey),
                BlobId.Version.Local(version.generation),
            )
        }
    }
}
