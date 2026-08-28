// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core

import app.accrescent.server.parcelo.core.text.u
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonEmptyUStringTest {
    @Test
    fun `fromString returns None for empty string`() {
        val result = NonEmptyUString.fromUString("".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString returns successfully for non-empty string`() {
        val result = NonEmptyUString.fromUString("a".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `value returns original string`() {
        val rawString = "Hello, world!"
        val nonEmptyString = NonEmptyUString.fromUString(rawString.u).unwrap()

        assertEquals(rawString.u, nonEmptyString.value)
    }

    @Test
    fun `instances with same value are equal`() {
        val instance1 = NonEmptyUString.fromUString("Hello, world!".u).unwrap()
        val instance2 = NonEmptyUString.fromUString("Hello, world!".u).unwrap()

        assertEquals(instance1, instance2)
    }
}
