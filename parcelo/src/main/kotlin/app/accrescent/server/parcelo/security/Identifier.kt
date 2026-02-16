// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.encoding.Base62
import java.security.SecureRandom

private const val RAND_BYTE_COUNT = 16

object Identifier {
    private val secureRandom = SecureRandom()

    fun generateNew(type: IdType): String {
        val randomBytes = ByteArray(RAND_BYTE_COUNT).also { secureRandom.nextBytes(it) }
        val encodedBytes = Base62.encode(randomBytes)
        val prefix = when (type) {
            IdType.APP_DRAFT -> "ad"
            IdType.APP_EDIT -> "ae"
            IdType.OPERATION -> "op"
            IdType.ORGANIZATION -> "org"
            IdType.USER -> "user"
            IdType.USER_SESSION -> "accu"
        }
        val encodedKey = "${prefix}_$encodedBytes"

        return encodedKey
    }
}
