// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.error

import app.accrescent.console.v1.ErrorReason
import com.google.protobuf.Any
import com.google.rpc.Code

data class ConsoleApiError(
    private val reason: ErrorReason,
    private val message: String,
    private val details: List<Any> = emptyList(),
) : ApiError<ErrorReason>(reason, message, details) {
    override val domain = "console-api.accrescent.app"

    override fun getStatusCode(): Code = when (reason) {
        ErrorReason.ERROR_REASON_UNSPECIFIED,
        ErrorReason.UNRECOGNIZED ->
            throw IllegalArgumentException("reason must not be UNSPECIFIED or UNRECOGNIZED")

        ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND -> Code.NOT_FOUND
        ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED,
        ErrorReason.ERROR_REASON_RATE_LIMIT_EXCEEDED -> Code.RESOURCE_EXHAUSTED

        ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION -> Code.PERMISSION_DENIED
        ErrorReason.ERROR_REASON_INVALID_REQUEST,
        ErrorReason.ERROR_REASON_INVALID_IMAGE,
        ErrorReason.ERROR_REASON_INCORRECT_IMAGE_DIMENSIONS,
        ErrorReason.ERROR_REASON_INVALID_PACKAGE,
        ErrorReason.ERROR_REASON_PACKAGE_DEBUGGABLE,
        ErrorReason.ERROR_REASON_LOW_TARGET_SDK,
        ErrorReason.ERROR_REASON_MISSING_64_BIT_CODE,
        ErrorReason.ERROR_REASON_NO_MODERN_SIGNATURE,
        ErrorReason.ERROR_REASON_DEBUG_SIGNER,
        ErrorReason.ERROR_REASON_MULTIPLE_SIGNERS,
        ErrorReason.ERROR_REASON_TEST_ONLY -> Code.INVALID_ARGUMENT

        ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
        ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
        ErrorReason.ERROR_REASON_ASSIGNEE_UNAVAILABLE,
        ErrorReason.ERROR_REASON_RESOURCE_INVALIDATED,
        ErrorReason.ERROR_REASON_CONSTRAINT_VIOLATION,
        ErrorReason.ERROR_REASON_APP_ID_MISMATCH,
        ErrorReason.ERROR_REASON_NOT_AN_UPGRADE,
        ErrorReason.ERROR_REASON_SIGNING_CERT_MISMATCH -> Code.FAILED_PRECONDITION

        ErrorReason.ERROR_REASON_ALREADY_SUBMITTED,
        ErrorReason.ERROR_REASON_RESOURCE_CONFLICT,
        ErrorReason.ERROR_REASON_ALREADY_EXISTS,
        ErrorReason.ERROR_REASON_ALREADY_PUBLISHED,
        ErrorReason.ERROR_REASON_RESOURCE_PUBLISHING -> Code.ALREADY_EXISTS

        ErrorReason.ERROR_REASON_NO_CREDENTIALS,
        ErrorReason.ERROR_REASON_NOT_REGISTERED -> Code.UNAUTHENTICATED

        ErrorReason.ERROR_REASON_INTERNAL -> Code.INTERNAL
    }
}
