// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.encoding.Base62
import java.security.MessageDigest
import java.security.SecureRandom

private const val RAND_BYTE_COUNT = 16

class ApiKey private constructor(val value: String) {
    companion object {
        fun generateNew(type: ApiKeyType): ApiKey {
            val randomBytes = ByteArray(RAND_BYTE_COUNT).also { SecureRandom().nextBytes(it) }
            val encodedBytes = Base62.encode(randomBytes)
            val prefix = when (type) {
                ApiKeyType.USER_SESSION -> "accu"
            }
            val encodedKey = "${prefix}_$encodedBytes"

            return ApiKey(encodedKey)
        }
    }

    fun rawValue(): String {
        return value
    }

    fun sha256Hash(): String {
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHexString()
    }
}
