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
        fun apkSetFormat(message: String) = ApiError(1, "Could not parse APK set", message)

        fun apkSetInvalid(message: String) = ApiError(2, "APK set is invalid", message)

        fun apkSetUnsupported(message: String) = ApiError(3, "APK set is unsupported", message)

        fun apkSetSignature(message: String) = ApiError(4, "APK set is incorrectly signed", message)

        fun apkSetDebuggable(message: String) = ApiError(5, "APK set is in debug mode", message)

        fun apkSetTestable(message: String) = ApiError(6, "APK set is in test mode", message)

        fun apkFormat(message: String) = ApiError(7, "Could not parse APK", message)

        fun apkInvalid(message: String) = ApiError(8, "APK is invalid", message)

        fun apkSignature(message: String) = ApiError(9, "APK is incorrectly signed", message)

        fun iconImageFormat() =
            ApiError(10, "Image is not in an acceptable format", "Icon must be a PNG")

        fun imageResolution() = ApiError(
            11,
            "Image is not in an acceptable resolution",
            "Image resolution must be 512 x 512",
        )

        fun labelLength() = ApiError(
            12,
            "Label has invalid length",
            "Label length must be between 3 and 30 characters long (inclusive)",
        )

        fun unknownPartName(partName: String?) =
            ApiError(13, "Unknown part name encountered", "Unknown part name \"$partName\"")

        fun appAlreadyExists() =
            ApiError(14, "App already exists", "An app with this ID already exists")

        fun minTargetSdk(min: Int, actual: Int) = ApiError(
            15,
            "Target SDK is too small",
            "Target SDK $actual is smaller than the minimum $min",
        )

        fun minBundletoolVersion(min: String, actual: String) = ApiError(
            16,
            "Bundletool version is too small",
            "Bundletool version $actual is smaller than the minimum $min",
        )

        fun missingPartName() = ApiError(
            17,
            "Missing request part names",
            "An expected part or parts is missing from the request",
        )

        fun invalidUuid(id: String) = ApiError(
            18,
            "Invalid UUID",
            "The ID \"$id\" supplied in the request is not a valid UUID",
        )

        fun draftNotFound(id: UUID) =
            ApiError(19, "Draft not found", "A draft with id \"$id\" does not exist")

        fun reviewerAlreadyAssigned() = ApiError(
            20,
            "Reviewer already assigned",
            "A reviewer has already been assigned to this object",
        )

        fun alreadyReviewed() =
            ApiError(21, "Already reviewed", "This object has already been reviewed")

        fun reviewForbidden() = ApiError(
            22,
            "Review forbidden",
            "This user does not have sufficient access rights to review this object",
        )

        fun publishForbidden() = ApiError(
            23,
            "Publish forbidden",
            "This user does not have sufficient access rights to publish this object",
        )

        fun updateCreationForbidden() = ApiError(
            24,
            "Update forbidden",
            "This user does not have sufficient permissions to create an update for this app",
        )

        fun updateNotFound(id: UUID) =
            ApiError(25, "Update not found", "An update with id \"$id\" does not exist")

        fun appNotFound(id: String) =
            ApiError(26, "App not found", "An app with id \"$id\" does not exist")

        fun readForbidden() = ApiError(
            27,
            "Read forbidden",
            "This user does not have sufficient access rights to read this object",
        )

        fun updateVersionTooLow(updateVersion: Int, appVersion: Int) = ApiError(
            28,
            "Update version is too low",
            "Update version code \"$updateVersion\" is less than published app version \"$appVersion\"",
        )
    }
}

fun toApiError(error: ParseApkResult.Error): ApiError = with(error) {
    when (this) {
        ParseApkResult.Error.AndroidManifestError -> ApiError.apkInvalid(message)
        ParseApkResult.Error.ApkFormatError -> ApiError.apkInvalid(message)
        ParseApkResult.Error.DebugCertificateError -> ApiError.apkSignature(message)
        ParseApkResult.Error.SignatureVerificationError -> ApiError.apkSignature(message)
        ParseApkResult.Error.SignatureVersionError -> ApiError.apkSignature(message)
        ParseApkResult.Error.ZipFormatError -> ApiError.apkFormat(message)
    }
}

fun toApiError(error: ParseApkSetResult.Error): ApiError = with(error) {
    when (this) {
        is ParseApkSetResult.Error.MetadataParseError -> ApiError.apkSetFormat(message)
        is ParseApkSetResult.Error.ApkParseError -> toApiError(this.error)
        is ParseApkSetResult.Error.SigningCertMismatchError -> ApiError.apkSetSignature(message)
        is ParseApkSetResult.Error.DebuggableError -> ApiError.apkSetDebuggable(message)
        is ParseApkSetResult.Error.TestOnlyError -> ApiError.apkSetTestable(message)
        is ParseApkSetResult.Error.ManifestInfoInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.ZipFormatError -> ApiError.apkSetFormat(message)
        is ParseApkSetResult.Error.MetadataNotFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.ApksNotFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.BundletoolVersionError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.MetadataUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.VariantsNotFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.VariantTargetingUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.VariantTargetsNothingError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.VariantSDKUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.DuplicateVariantError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.ApkSetUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.ModuleUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.ModuleTargetingUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.ApkDescriptionUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.BaseApkDescriptionInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.DuplicateBaseSplitsError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.MultipleBaseApkDescriptionsError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.NoBaseSplitsFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.MultipleBaseSplitsFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.DuplicateSplitPathsError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.ApkTargetingUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.AbiUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.LangUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.DensityUnsupportedError -> ApiError.apkSetUnsupported(message)
        is ParseApkSetResult.Error.ApkTargetsNothingError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.MultipleApkDescriptionsError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.SplitIdInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.NoSplitsFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.MultipleSplitsFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.BaseApkNotFoundError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.UnaccountedSplitsError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.AppIdInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.VersionCodeInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.VersionNameUnspecifiedError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.VersionNameInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.SdkUnspecifiedError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.SdkInconsistentError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.TargetSdkUnspecifiedError -> ApiError.apkSetInvalid(message)
        is ParseApkSetResult.Error.ReviewIssueInconsistentError -> ApiError.apkSetInvalid(message)
    }
}
