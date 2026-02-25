// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.error

enum class CommonErrorReason {
    INTERNAL,
    INVALID_REQUEST,
    NO_CREDENTIALS,
    NOT_REGISTERED,
    RATE_LIMIT_EXCEEDED;

    override fun toString(): String {
        // Prefix error reason names with ERROR_REASON_ to allow us to move the error reason
        // definitions into protobuf in the future while preserving backward compatibility and
        // respecting Buf's ENUM_VALUE_PREFIX rule
        // (https://buf.build/docs/lint/rules/#enum_value_prefix).
        return "ERROR_REASON_$name"
    }
}
