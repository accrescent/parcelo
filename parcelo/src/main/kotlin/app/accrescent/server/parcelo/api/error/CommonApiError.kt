// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.error

import com.google.protobuf.Any
import com.google.rpc.Code

data class CommonApiError(
    private val reason: CommonErrorReason,
    private val message: String,
    private val details: List<Any> = emptyList(),
) : ApiError<CommonErrorReason>(reason, message, details) {
    override val domain = "api.accrescent.app"

    override fun getStatusCode(): Code = when (reason) {
        CommonErrorReason.INTERNAL -> Code.INTERNAL
        CommonErrorReason.INVALID_REQUEST -> Code.INVALID_ARGUMENT
        CommonErrorReason.NO_CREDENTIALS,
        CommonErrorReason.NOT_REGISTERED -> Code.UNAUTHENTICATED

        CommonErrorReason.RATE_LIMIT_EXCEEDED -> Code.RESOURCE_EXHAUSTED
    }
}
