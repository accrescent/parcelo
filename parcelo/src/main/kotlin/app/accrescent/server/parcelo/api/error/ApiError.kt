// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.error

import com.google.protobuf.Any
import com.google.rpc.Code
import com.google.rpc.ErrorInfo
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import com.google.rpc.Status as GoogleStatus

abstract class ApiError<R>(
    private val reason: R,
    private val message: String,
    private val details: List<Any>,
) {
    protected abstract val domain: String

    protected abstract fun getStatusCode(): Code

    protected fun buildErrorInfo(): ErrorInfo {
        return ErrorInfo
            .newBuilder()
            .setReason(reason.toString())
            .setDomain(domain)
            .build()
    }

    fun toStatus(): GoogleStatus {
        return GoogleStatus
            .newBuilder()
            .setCode(getStatusCode().number)
            .setMessage(message)
            .addDetails(Any.pack(buildErrorInfo()))
            .addAllDetails(details)
            .build()
    }

    fun toStatusRuntimeException(): StatusRuntimeException {
        return StatusProto.toStatusRuntimeException(toStatus())
    }
}
