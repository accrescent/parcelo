// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.console.v1.ErrorReason
import app.accrescent.server.parcelo.api.error.ConsoleApiError
import app.accrescent.server.parcelo.parsers.ApkSetParseError

const val BUCKET_ID_KEY = "bucketId"
const val EVENT_TIME_KEY = "eventTime"
const val EVENT_TYPE_KEY = "eventType"
const val EVENT_TYPE_OBJECT_FINALIZE = "OBJECT_FINALIZE"
const val OBJECT_ID_KEY = "objectId"

fun ApkSetParseError.toConsoleApiError(): ConsoleApiError {
    val (reason, message) = when (this) {
        ApkSetParseError.InvalidFormat ->
            ErrorReason.ERROR_REASON_INVALID_PACKAGE to "the upload is not a valid APK set"

        ApkSetParseError.IoError ->
            ErrorReason.ERROR_REASON_INTERNAL to "unknown I/O error parsing APK set"

        ApkSetParseError.RequirementError.Debuggable ->
            ErrorReason.ERROR_REASON_PACKAGE_DEBUGGABLE to "the app is debuggable"

        ApkSetParseError.RequirementError.LowTargetSdk ->
            ErrorReason.ERROR_REASON_LOW_TARGET_SDK to
                    "the app's target SDK is lower than the required minimum"

        ApkSetParseError.RequirementError.Missing64BitCode ->
            ErrorReason.ERROR_REASON_MISSING_64_BIT_CODE to
                    "the app has 32-bit code but is missing corresponding 64-bit code"

        ApkSetParseError.RequirementError.NoModernSignature ->
            ErrorReason.ERROR_REASON_NO_MODERN_SIGNATURE to
                    "the app has not been signed with a modern signature format"

        ApkSetParseError.RequirementError.SignedWithDebugCert ->
            ErrorReason.ERROR_REASON_DEBUG_SIGNER to
                    "the app has been signed with a debug certificate"

        ApkSetParseError.RequirementError.SignedWithMultipleCerts ->
            ErrorReason.ERROR_REASON_MULTIPLE_SIGNERS to
                    "the app has been signed with multiple certificates"

        ApkSetParseError.RequirementError.TestOnly ->
            ErrorReason.ERROR_REASON_TEST_ONLY to "the app is marked test-only"
    }

    return ConsoleApiError(reason, message)
}
