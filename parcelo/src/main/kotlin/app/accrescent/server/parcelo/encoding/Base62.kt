// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.encoding

import java.math.BigInteger

class Base62 {
    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private val BASE = ALPHABET.length.toBigInteger()

        fun encode(bytes: ByteArray): String {
            // Encode byte array to base 62
            val stringBuilder = StringBuilder()
            var number = BigInteger(1, bytes)
            while (number > BigInteger.ZERO) {
                val (quotient, remainder) = number.divideAndRemainder(BASE)
                stringBuilder.append(ALPHABET[remainder.toInt()])
                number = quotient
            }

            // Encode the leading zeroes that were lost when converting to a BigInteger
            val leadingZeroes = bytes.takeWhile { it == 0.toByte() }.size
            repeat(leadingZeroes) {
                stringBuilder.append(ALPHABET[0])
            }

            return stringBuilder.reverse().toString()
        }
    }
}
