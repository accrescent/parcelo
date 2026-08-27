// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core

/**
 * Encodes this string into UTF-8 bytes.
 *
 * If the string contains an unpaired surrogate, it is replaced with the '?' character.
 *
 * @return the UTF-8 byte encoding of this string.
 */
fun String.encodeToBytes(): Bytes {
    return Bytes(toByteArray())
}
