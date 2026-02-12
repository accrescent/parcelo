// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.server.parcelo.parsers.ApkSetParseError
import io.grpc.Status
import com.google.rpc.Status as GoogleStatus

const val BUCKET_ID_KEY = "bucketId"
const val EVENT_TIME_KEY = "eventTime"
const val EVENT_TYPE_KEY = "eventType"
const val EVENT_TYPE_OBJECT_FINALIZE = "OBJECT_FINALIZE"
const val OBJECT_ID_KEY = "objectId"

fun ApkSetParseError.toStatus(): GoogleStatus {
    val code = when (this) {
        ApkSetParseError.InvalidFormat,
        is ApkSetParseError.RequirementError -> Status.Code.INVALID_ARGUMENT

        ApkSetParseError.IoError -> Status.Code.INTERNAL
    }
    val message = when (this) {
        ApkSetParseError.InvalidFormat -> "the upload is not a valid APK set"
        ApkSetParseError.IoError -> "unknown I/O error parsing APK set"
        ApkSetParseError.RequirementError.Debuggable -> "the app is debuggable"
        ApkSetParseError.RequirementError.LowTargetSdk ->
            "the app's target SDK is lower than the required minimum"

        ApkSetParseError.RequirementError.Missing64BitCode ->
            "the app has 32-bit code but is missing corresponding 64-bit code"

        ApkSetParseError.RequirementError.NoModernSignature ->
            "the app has not been signed with a modern signature format"

        ApkSetParseError.RequirementError.SignedWithDebugCert ->
            "the app has been signed with a debug certificate"

        ApkSetParseError.RequirementError.SignedWithMultipleCerts ->
            "the app has been signed with multiple certificates"

        ApkSetParseError.RequirementError.TestOnly -> "the app is marked test-only"
    }
    val status = GoogleStatus.newBuilder().setCode(code.value()).setMessage(message).build()

    return status
}
