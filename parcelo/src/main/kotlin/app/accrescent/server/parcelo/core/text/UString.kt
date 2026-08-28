// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core.text

import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.encodeToBytes
import arrow.core.None
import arrow.core.Option
import arrow.core.Some

/**
 * A valid Unicode string.
 *
 * Kotlin [String]s on the JVM are effectively sequences of UTF-16 code units which do not
 * necessarily constitute valid UTF-16 (and thus Unicode) strings. Specifically, they can contain
 * unpaired surrogates.
 *
 * This class forbids unpaired surrogates, ensuring the string it wraps is valid Unicode.
 *
 * @property value the underlying [String] representation of this Unicode string.
 */
@JvmInline
value class UString private constructor(val value: String) {
    companion object {
        /**
         * Creates a new Unicode string from a Kotlin string.
         *
         * @param value the string to attempt to create a Unicode string from.
         * @return a Unicode string with the value of [value], or [None] if [value] is not a valid
         * Unicode string.
         */
        fun fromString(value: String): Option<UString> {
            return try {
                value.encodeToByteArray(throwOnInvalidSequence = true)
                Some(UString(value))
            } catch (_: CharacterCodingException) {
                None
            }
        }
    }

    /**
     * Encodes this string into UTF-8 bytes.
     *
     * @return the UTF-8 byte encoding of this string.
     */
    fun encodeToBytes(): Bytes {
        return value.encodeToBytes()
    }
}
