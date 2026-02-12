// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.error

import app.accrescent.console.v1alpha1.ErrorReason
import com.google.protobuf.Any
import com.google.rpc.ErrorInfo
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import com.google.rpc.Status as GoogleStatus

private const val DOMAIN = "console-api.accrescent.app"

data class ConsoleApiError(
    private val reason: ErrorReason,
    private val message: String,
) {
    fun toStatusRuntimeException(): StatusRuntimeException {
        val code = when (reason) {
            ErrorReason.ERROR_REASON_UNSPECIFIED,
            ErrorReason.UNRECOGNIZED ->
                throw IllegalArgumentException("reason must not be UNSPECIFIED or UNRECOGNIZED")

            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND -> Status.Code.NOT_FOUND
            ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED,
            ErrorReason.ERROR_REASON_RATE_LIMIT_EXCEEDED -> Status.Code.RESOURCE_EXHAUSTED

            ErrorReason.ERROR_REASON_INSUFFICIENT_PERMISSION -> Status.Code.PERMISSION_DENIED
            ErrorReason.ERROR_REASON_INVALID_REQUEST -> Status.Code.INVALID_ARGUMENT
            ErrorReason.ERROR_REASON_RESOURCE_IMMUTABLE,
            ErrorReason.ERROR_REASON_RESOURCE_INCOMPLETE,
            ErrorReason.ERROR_REASON_ASSIGNEE_UNAVAILABLE,
            ErrorReason.ERROR_REASON_RESOURCE_INVALIDATED,
            ErrorReason.ERROR_REASON_CONSTRAINT_VIOLATION -> Status.Code.FAILED_PRECONDITION

            ErrorReason.ERROR_REASON_ALREADY_SUBMITTED,
            ErrorReason.ERROR_REASON_RESOURCE_CONFLICT,
            ErrorReason.ERROR_REASON_ALREADY_EXISTS,
            ErrorReason.ERROR_REASON_ALREADY_PUBLISHED,
            ErrorReason.ERROR_REASON_RESOURCE_PUBLISHING -> Status.Code.ALREADY_EXISTS

            ErrorReason.ERROR_REASON_NO_CREDENTIALS,
            ErrorReason.ERROR_REASON_NOT_REGISTERED -> Status.Code.UNAUTHENTICATED
        }

        val errorInfo = ErrorInfo
            .newBuilder()
            .setReason(reason.toString())
            .setDomain(DOMAIN)
            .build()
        val status = GoogleStatus
            .newBuilder()
            .setCode(code.value())
            .setMessage(message)
            .addDetails(Any.pack(errorInfo))
            .build()

        return StatusProto.toStatusRuntimeException(status)
    }
}
