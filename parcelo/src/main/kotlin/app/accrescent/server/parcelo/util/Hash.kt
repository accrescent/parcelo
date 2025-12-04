// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.util

import java.security.MessageDigest

fun sha256Hash(input: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(input).toHexString()
}
