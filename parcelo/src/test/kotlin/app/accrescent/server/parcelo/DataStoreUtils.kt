// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo

import app.accrescent.server.parcelo.adapters.driven.timestampsource.ConstantTimestampSource
import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.NameAttribute
import app.accrescent.server.parcelo.domain.android.SdkVersion
import app.accrescent.server.parcelo.domain.android.VersionCode
import app.accrescent.server.parcelo.domain.android.VersionName
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraft
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListingIconUploadProcessingResult
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftUploadProcessingError
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackagePermission
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ExternalBlob
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ListingLanguage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.Organization
import app.accrescent.server.parcelo.domain.ports.driven.datastore.OrganizationOwnerRelationship
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.User
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import java.time.OffsetDateTime

val UNIX_EPOCH = ConstantTimestampSource().now()

fun appDraftListing(
    id: String = "appDraftListing1",
    appDraftId: String = "appDraft1",
    language: ListingLanguage = ListingLanguage.EN_US,
    name: String = "Example App",
    shortDescription: String = "Example Short Description",
): AppDraftListing {
    return AppDraftListing(
        id = id,
        appDraftId = appDraftId,
        language = language,
        name = name,
        shortDescription = shortDescription,
    )
}

fun appPackage(
    id: String = "appPackage1",
    externalBlobId: String = "blob1",
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

fun appPackagePermission(
    id: String = "perm1",
    appPackageId: String = "appPackage1",
    name: NameAttribute = NameAttribute.fromString("android.permission.INTERNET").unwrap(),
    maxSdkVersion: Option<SdkVersion> = None,
): AppPackagePermission {
    return AppPackagePermission(
        id = id,
        appPackageId = appPackageId,
        name = name,
        maxSdkVersion = maxSdkVersion,
    )
}

fun committedExternalBlob(
    id: String = "blob1",
    bucketName: String = "bucket1",
    objectKey: String = "object1",
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

fun deletedExternalBlob(
    id: String = "blob1",
    bucketName: String = "bucket1",
    objectKey: String = "object1",
    createTime: OffsetDateTime = UNIX_EPOCH,
    generation: Option<Long> = Some(1),
    deleteTime: OffsetDateTime = UNIX_EPOCH,
): ExternalBlob<ExternalBlob.Status.Deleted<*>> {
    return ExternalBlob.Local(
        id = id,
        createTime = createTime,
        bucketName = bucketName,
        objectKey = objectKey,
        status = ExternalBlob.Status.Deleted(
            generation.map { ExternalBlob.LocalBlobVersion(it) },
            deleteTime,
        ),
    )
}

fun pendingExternalBlob(
    id: String = "blob1",
    bucketName: String = "bucket1",
    objectKey: String = "object1",
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

fun organization(id: String = "org1", createTime: OffsetDateTime = UNIX_EPOCH): Organization {
    return Organization(id, createTime)
}

fun organizationOwnerRelationship(
    organizationId: String = "org1",
    userId: String = "user1",
): OrganizationOwnerRelationship {
    return OrganizationOwnerRelationship(organizationId, userId)
}

fun pendingAppDraftUpload(
    id: String = "appDraftUpload1",
    appDraftId: String = "appDraft1",
    externalBlobId: String = "blob1",
    objectKey: String = "object1",
    createTime: OffsetDateTime = UNIX_EPOCH,
    result: Option<Either<AppDraftUploadProcessingError, Unit>> = None,
): PendingAppDraftUpload {
    return PendingAppDraftUpload(
        id = id,
        appDraftId = appDraftId,
        externalBlobId = externalBlobId,
        objectKey = objectKey,
        createTime = createTime,
        result = result,
    )
}

fun pendingAppDraftListingIconUpload(
    id: String = "adliu1",
    appDraftListingId: String = "appDraftListing1",
    externalBlobId: String = "blob1",
    objectKey: String = "object1",
    createTime: OffsetDateTime = UNIX_EPOCH,
    result: Option<AppDraftListingIconUploadProcessingResult> = None,
): PendingAppDraftListingIconUpload {
    return PendingAppDraftListingIconUpload(
        id = id,
        appDraftListingId = appDraftListingId,
        externalBlobId = externalBlobId,
        objectKey = objectKey,
        createTime = createTime,
        result = result,
    )
}

fun unsubmittedAppDraft(
    id: String = "appDraft1",
    organizationId: String = "org1",
    defaultAppDraftListingId: Option<String> = None,
    appPackageId: Option<String> = None,
): AppDraft {
    return AppDraft.Unsubmitted(
        id = id,
        organizationId = organizationId,
        createTime = UNIX_EPOCH,
        defaultAppDraftListingId = defaultAppDraftListingId,
        appPackageId = appPackageId,
    )
}

fun user(id: String = "user1"): User {
    return User(id)
}
