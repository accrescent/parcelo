// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo

import app.accrescent.server.parcelo.adapters.driven.timestampsource.FixedTimestampSource
import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.NameAttribute
import app.accrescent.server.parcelo.domain.android.SdkVersion
import app.accrescent.server.parcelo.domain.android.VersionCode
import app.accrescent.server.parcelo.domain.android.VersionName
import app.accrescent.server.parcelo.domain.appstore.ListingLanguage
import app.accrescent.server.parcelo.domain.authn.ExternalUserId
import app.accrescent.server.parcelo.domain.crypto.Sha256Hash
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftApiView
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListingApiView
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackageApiView
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStoreResult
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ExternalBlob
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftUpload
import arrow.core.None
import arrow.core.Option
import arrow.core.raise.either
import java.time.OffsetDateTime

val UNIX_EPOCH = FixedTimestampSource().now()

fun appDraftListingApiView(
    id: UString = "appDraftListing1".u,
    appDraftId: UString = "appDraft1".u,
    language: ListingLanguage = ListingLanguage.EN_US,
    name: UString = "Example App".u,
    shortDescription: UString = "Example Short Description".u,
): AppDraftListingApiView {
    return AppDraftListingApiView(
        id = id,
        appDraftId = appDraftId,
        language = language,
        name = name,
        shortDescription = shortDescription,
    )
}

fun createAppDraftListing(
    tx: DataStore.Transaction,
    id: UString = "appDraftListing1".u,
    appDraftId: UString = "appDraft1".u,
    language: ListingLanguage = ListingLanguage.EN_US,
    name: UString = "Example App".u,
    shortDescription: UString = "Example Short Description".u,
): DataStoreResult<Unit> {
    return tx.appDrafts.createListing(
        id = id,
        appDraftId = appDraftId,
        language = language,
        name = name,
        shortDescription = shortDescription,
    )
}

fun signInNewUser(
    tx: DataStore.Transaction,
    userId: UString = "user1".u,
    organizationId: UString = "org1".u,
    externalUserId: ExternalUserId = ExternalUserId.Github(1),
    sessionId: UString = "session1".u,
): DataStoreResult<Unit> = either {
    tx.organizations.saveWithOwner(organizationId, userId, externalUserId, UNIX_EPOCH).bind()
    tx.sessions
        .create(Sha256Hash.hash(sessionId.encodeToBytes()), userId, UNIX_EPOCH, UNIX_EPOCH.plusDays(1))
        .bind()
}

fun appPackage(
    id: UString = "appPackage1".u,
    appDraftId: UString = "appDraft1".u,
    externalBlobId: UString = "blob1".u,
    uploadEventTime: OffsetDateTime = UNIX_EPOCH,
    appId: ApplicationId = ApplicationId.fromString("com.example.app").unwrap(),
    versionCode: VersionCode = VersionCode.fromInt(1).unwrap(),
    versionName: VersionName = VersionName.fromString("1.0").unwrap(),
    targetSdk: SdkVersion = SdkVersion.fromInt(37).unwrap(),
    signingCertificate: Bytes = Bytes(byteArrayOf(1, 2, 3)),
    buildApksResult: Bytes = Bytes(byteArrayOf(4, 5, 6)),
): AppPackage {
    return AppPackage(
        id = id,
        appDraftId = appDraftId,
        externalBlobId = externalBlobId,
        uploadEventTime = uploadEventTime,
        appId = appId,
        versionCode = versionCode,
        versionName = versionName,
        targetSdk = targetSdk,
        signerCertificate = signingCertificate,
        buildApksResult = buildApksResult,
    )
}

/**
 * Saves an app package for an existing app draft by way of the pending upload it must come from.
 *
 * An app package can only enter the data store by committing a pending app draft upload, since a
 * committed external blob is always one an app package took over from an upload. Tests needing a
 * package therefore have to create that upload and its blob first, which this does for them.
 *
 * @param tx the transaction to save the app package within.
 * @param appPackage the app package to save.
 * @param permissions the permissions to save as part of the app package.
 * @param pendingUploadId the ID to give the pending upload the package is committed from.
 * @param bucketName the bucket to place the package's blob in.
 * @param objectKey the object key to give both the pending upload and the package's blob.
 */
fun saveAppPackageFromNewUpload(
    tx: DataStore.Transaction,
    appPackage: AppPackage = appPackage(),
    permissions: Map<NameAttribute, Option<SdkVersion>> = emptyMap(),
    pendingUploadId: UString = "appDraftUpload1".u,
    bucketName: UString = "bucket1".u,
    objectKey: UString = "object1".u,
): DataStoreResult<Unit> = either {
    tx.appDrafts
        .saveUpload(
            incompletePendingAppDraftUpload(
                id = pendingUploadId,
                appDraftId = appPackage.appDraftId,
                externalBlobId = appPackage.externalBlobId,
                objectKey = objectKey,
                createTime = UNIX_EPOCH,
            ),
            pendingExternalBlob(
                id = appPackage.externalBlobId,
                bucketName = bucketName,
                objectKey = objectKey,
                createTime = UNIX_EPOCH,
            ),
        )
        .bind()
    tx.appPackages
        .saveFromPendingUpload(
            pendingUploadId = pendingUploadId,
            appPackage = appPackage,
            permissions = permissions,
            blobVersion = ExternalBlob.LocalBlobVersion(1),
            replacedBlobDeleteTime = UNIX_EPOCH,
        )
        .bind()
}

fun committedExternalBlob(
    id: UString = "blob1".u,
    bucketName: UString = "bucket1".u,
    objectKey: UString = "object1".u,
    createTime: OffsetDateTime = UNIX_EPOCH,
    generation: Long = 1,
): ExternalBlob<ExternalBlob.Status.Committed<*>> {
    return ExternalBlob.Local(
        id = id,
        createTime = createTime,
        bucketName = bucketName,
        objectKey = objectKey,
        status = ExternalBlob.Status.Committed(ExternalBlob.LocalBlobVersion(generation)),
    )
}

fun pendingExternalBlob(
    id: UString = "blob1".u,
    bucketName: UString = "bucket1".u,
    objectKey: UString = "object1".u,
    createTime: OffsetDateTime = UNIX_EPOCH,
): ExternalBlob<ExternalBlob.Status.Pending> {
    return ExternalBlob.Local(
        id = id,
        createTime = createTime,
        bucketName = bucketName,
        objectKey = objectKey,
        status = ExternalBlob.Status.Pending,
    )
}

fun incompletePendingAppDraftUpload(
    id: UString = "appDraftUpload1".u,
    appDraftId: UString = "appDraft1".u,
    externalBlobId: UString = "blob1".u,
    objectKey: UString = "object1".u,
    createTime: OffsetDateTime = UNIX_EPOCH,
): PendingAppDraftUpload.Incomplete {
    return PendingAppDraftUpload.Incomplete(
        id = id,
        appDraftId = appDraftId,
        objectKey = objectKey,
        createTime = createTime,
        externalBlobId = externalBlobId,
    )
}

fun incompletePendingAppDraftListingIconUpload(
    id: UString = "adliu1".u,
    appDraftListingId: UString = "appDraftListing1".u,
    externalBlobId: UString = "blob1".u,
    objectKey: UString = "object1".u,
    createTime: OffsetDateTime = UNIX_EPOCH,
): PendingAppDraftListingIconUpload.Incomplete {
    return PendingAppDraftListingIconUpload.Incomplete(
        id = id,
        appDraftListingId = appDraftListingId,
        objectKey = objectKey,
        createTime = createTime,
        externalBlobId = externalBlobId,
    )
}

fun unsubmittedAppDraftApiView(
    id: UString = "appDraft1".u,
    defaultAppDraftListingId: Option<UString> = None,
    appPackage: Option<AppPackageApiView> = None,
): AppDraftApiView.Unsubmitted {
    return AppDraftApiView.Unsubmitted(
        id = id,
        createTime = UNIX_EPOCH,
        defaultAppDraftListingId = defaultAppDraftListingId,
        appPackage = appPackage,
    )
}
