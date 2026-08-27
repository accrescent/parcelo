// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringExtTest {
    @Test
    fun `encodeToBytes returns UTF-8 encoding of string`() {
        val bytes = "\u0061\u00e4\u20ac\ud834\udd1e".encodeToBytes()

        assertEquals(Bytes("61c3a4e282acf09d849e".hexToByteArray()), bytes)
    }

    @Test
    fun `encodeToBytes replaces invalid Unicode`() {
        val bytes = "\ud800".encodeToBytes()

        assertEquals(Bytes("3f".hexToByteArray()), bytes)
    }
}
