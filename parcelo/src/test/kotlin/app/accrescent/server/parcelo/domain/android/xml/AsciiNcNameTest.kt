// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.android.xml

import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsciiNcNameTest {
    @Test
    fun `fromString rejects value containing colon`() {
        val name = AsciiNcName.fromUString("example:name".u)

        assertTrue(name.isNone())
    }

    @Test
    fun `fromString rejects value containing non-ASCII character`() {
        val name = AsciiNcName.fromUString("exämple".u)

        assertTrue(name.isNone())
    }

    @Test
    fun `fromString accepts all valid characters`() {
        val name =
            AsciiNcName.fromUString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-".u)

        assertTrue(name.isSome())
    }

    @Test
    fun `value returns original string`() {
        val rawName = "example-name".u
        val name = AsciiNcName.fromUString(rawName).unwrap()

        assertEquals(rawName, name.value)
    }

    @Test
    fun `instances with same value are equal`() {
        val rawName = "example-name".u
        val instance1 = AsciiNcName.fromUString(rawName).unwrap()
        val instance2 = AsciiNcName.fromUString(rawName).unwrap()

        assertEquals(instance1, instance2)
    }
}
