// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data.net

import app.accrescent.parcelo.apksparser.ParseApkResult
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class ApiError private constructor(
    @SerialName("error_code") val errorCode: Int,
    val title: String,
    val message: String,
) {
    companion object {
        fun androidManifest(message: String) = ApiError(1, "Invalid Android manifest", message)
        fun apkFormat(message: String) = ApiError(2, "APK parsing failed", message)
        fun debugCertificate(message: String) = ApiError(3, "Debug certificate detected", message)
        fun signatureVerification(message: String) =
            ApiError(4, "Signature verification failed", message)

        fun signatureVersion(message: String) = ApiError(5, "Signature version(s) invalid", message)
        fun zipFormat(message: String) = ApiError(6, "ZIP format error", message)
        fun debuggable(message: String) = ApiError(14, "Application is debuggable", message)
        fun manifestInfoInconsistent(message: String) =
            ApiError(17, "Inconsistent manifest info", message)

        fun signingCertMismatch(message: String) =
            ApiError(18, "Signing certificates don't match", message)

        fun targetSdkNotFound(message: String) = ApiError(19, "Target SDK not found", message)
        fun testOnly(message: String) = ApiError(20, "Application is test only", message)
        fun versionNameNotFound(message: String) = ApiError(21, "Version name not found", message)
        fun iconImageFormat() =
            ApiError(22, "Image is not in an acceptable format", "Icon must be a PNG")

        fun imageResolution() = ApiError(
            23,
            "Image is not in an acceptable resolution",
            "Image resolution must be 512 x 512",
        )

        fun labelLength() = ApiError(
            24,
            "Label has invalid length",
            "Label length must be between 3 and 30 characters long (inclusive)",
        )

        fun unknownPartName(partName: String?) =
            ApiError(25, "Unknown part name encountered", "Unknown part name \"$partName\"")

        fun appAlreadyExists() =
            ApiError(26, "App already exists", "An app with this ID already exists")

        fun minTargetSdk(min: Int, actual: Int) = ApiError(
            27,
            "Target SDK is too small",
            "Target SDK $actual is smaller than the minimum $min",
        )

        fun missingPartName() = ApiError(
            29,
            "Missing request part names",
            "An expected part or parts is missing from the request",
        )

        fun invalidUuid(id: String) = ApiError(
            30,
            "Invalid UUID",
            "The ID \"$id\" supplied in the request is not a valid UUID",
        )

        fun draftNotFound(id: UUID) =
            ApiError(31, "Draft not found", "A draft with id \"$id\" does not exist")

        fun reviewerAlreadyAssigned() = ApiError(
            32,
            "Reviewer already assigned",
            "A reviewer has already been assigned to this object",
        )

        fun alreadyReviewed() =
            ApiError(33, "Already reviewed", "This object has already been reviewed")

        fun reviewForbidden() = ApiError(
            34,
            "Review forbidden",
            "This user does not have sufficient access rights to review this object",
        )

        fun publishForbidden() = ApiError(
            35,
            "Publish forbidden",
            "This user does not have sufficient access rights to publish this object",
        )

        fun updateCreationForbidden() = ApiError(
            36,
            "Update forbidden",
            "This user does not have sufficient permissions to create an update for this app",
        )

        fun updateNotFound(id: UUID) =
            ApiError(37, "Update not found", "An update with id \"$id\" does not exist")

        fun appNotFound(id: String) =
            ApiError(38, "App not found", "An app with id \"$id\" does not exist")

        fun readForbidden() = ApiError(
            39,
            "Read forbidden",
            "This user does not have sufficient access rights to read this object",
        )

        fun updateVersionTooLow(updateVersion: Int, appVersion: Int) = ApiError(
            40,
            "Update version is too low",
            "Update version code \"$updateVersion\" is less than published app version \"$appVersion\"",
        )

        fun updateAppIdDoesntMatch(appId: String, updateAppId: String) = ApiError(
            41,
            "Update app ID doesn't match",
            "The update's app ID \"$updateAppId\" doesn't match the published app ID \"$appId\"",
        )

        fun downloadForbidden() = ApiError(
            42,
            "Download forbidden",
            "This user does not have sufficient access rights to download this object",
        )

        fun deleteForbidden() = ApiError(
            43,
            "Deletion forbidden",
            "This user does not have sufficient access rights to delete this object",
        )

        fun alreadyUpdating(appId: String) = ApiError(
            44,
            "Already updating",
            "The app \"$appId\" is currently updating. Please try again later.",
        )

        fun editCreationForbidden() = ApiError(
            45,
            "Edit forbidden",
            "This user does not have sufficient permissions to create an edit for this app",
        )

        fun editNotFound(id: UUID) =
            ApiError(46, "Edit not found", "An edit with id \"$id\" does not exist")

        fun submissionConflict() = ApiError(
            47,
            "Conflict with another submission",
            "This object cannot be submitted since another is already submitted",
        )

        fun shortDescriptionLength() = ApiError(
            48,
            "Short description has invalid length",
            "Short description must be between 3 and 80 characters long (inclusive)",
        )

        fun ioError(message: String) = ApiError(49, "I/O error", message)
        fun apkSetFormat(message: String) = ApiError(50, "Invalid APK set format", message)
    }
}

fun toApiError(error: ParseApkResult.Error): ApiError = with(error) {
    when (this) {
        ParseApkResult.Error.AndroidManifestError -> ApiError.androidManifest(message)
        ParseApkResult.Error.ApkFormatError -> ApiError.apkFormat(message)
        ParseApkResult.Error.DebugCertificateError -> ApiError.debugCertificate(message)
        ParseApkResult.Error.SignatureVerificationError -> ApiError.signatureVerification(message)
        ParseApkResult.Error.SignatureVersionError -> ApiError.signatureVersion(message)
        ParseApkResult.Error.ZipFormatError -> ApiError.zipFormat(message)
    }
}

fun toApiError(error: ParseApkSetResult.Error): ApiError = with(error) {
    when (this) {
        is ParseApkSetResult.Error.ApkParseError -> toApiError(this.error)
        ParseApkSetResult.Error.DebuggableError -> ApiError.debuggable(message)
        is ParseApkSetResult.Error.IoError -> ApiError.ioError(message)
        is ParseApkSetResult.Error.MismatchedAppIdError -> ApiError.manifestInfoInconsistent(message)
        is ParseApkSetResult.Error.MismatchedVersionCodeError -> ApiError.manifestInfoInconsistent(
            message
        )

        is ParseApkSetResult.Error.MissingApkError -> ApiError.apkSetFormat(message)
        ParseApkSetResult.Error.MissingPathError -> ApiError.apkSetFormat(message)
        ParseApkSetResult.Error.MissingVersionCodeError -> ApiError.apkSetFormat(message)
        ParseApkSetResult.Error.SigningCertMismatchError -> ApiError.signingCertMismatch(message)
        ParseApkSetResult.Error.TargetSdkNotFoundError -> ApiError.targetSdkNotFound(message)
        ParseApkSetResult.Error.TestOnlyError -> ApiError.testOnly(message)
        ParseApkSetResult.Error.TocNotFound -> ApiError.apkSetFormat(message)
        ParseApkSetResult.Error.VersionNameNotFoundError -> ApiError.versionNameNotFound(message)
        ParseApkSetResult.Error.ZipFormatError -> ApiError.zipFormat(message)
    }
}
