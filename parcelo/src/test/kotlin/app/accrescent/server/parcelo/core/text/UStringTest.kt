// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core.text

import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.unwrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UStringTest {
    @Test
    fun `fromString rejects string with unpaired surrogate`() {
        val result = UString.fromString("\ud800")

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString accepts valid Unicode string`() {
        val result = UString.fromString("Hello, world!")

        assertTrue(result.isSome())
    }

    @Test
    fun `encodeToBytes returns UTF-8 encoding of string`() {
        val string = UString.fromString("\u0061\u00e4\u20ac\ud834\udd1e").unwrap()

        assertEquals(Bytes("61c3a4e282acf09d849e".hexToByteArray()), string.encodeToBytes())
    }

    @Test
    fun `value returns string passed to fromString`() {
        val originalString = "Hello, world!"

        val unicodeString = UString.fromString(originalString).unwrap()

        assertEquals(originalString, unicodeString.value)
    }

    @Test
    fun `instances created from same string are equal`() {
        val instance1 = UString.fromString("Hello, world!").unwrap()
        val instance2 = UString.fromString("Hello, world!").unwrap()

        assertEquals(instance1, instance2)
    }

    @Test
    fun `instances created from the same string have the same hash code`() {
        val instance1 = UString.fromString("Hello, world!").unwrap()
        val instance2 = UString.fromString("Hello, world!").unwrap()

        assertEquals(instance1.hashCode(), instance2.hashCode())
    }
}
