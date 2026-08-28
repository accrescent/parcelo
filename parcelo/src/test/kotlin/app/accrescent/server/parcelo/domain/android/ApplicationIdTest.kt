// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.android

import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationIdTest {
    @Test
    fun `fromString rejects value with only one segment`() {
        val appId = ApplicationId.fromUString("example".u)

        assertTrue(appId.isNone())
    }

    @Test
    fun `fromString rejects value with segment starting with non-letter`() {
        val appId = ApplicationId.fromUString("com.3xample".u)

        assertTrue(appId.isNone())
    }

    @Test
    fun `fromString accepts all valid characters`() {
        val appId =
            ApplicationId.fromUString("bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.a".u)

        assertTrue(appId.isSome())
    }

    @Test
    fun `fromString accepts value 128 characters long`() {
        val rawId = "a." + "b".repeat(126)
        assertEquals(128, rawId.length)

        val appId = ApplicationId.fromUString(rawId.u)

        assertTrue(appId.isSome())
    }

    @Test
    fun `fromString rejects value longer than 128 characters`() {
        val rawId = "a." + "b".repeat(127)
        assertEquals(129, rawId.length)

        val appId = ApplicationId.fromUString(rawId.u)

        assertTrue(appId.isNone())
    }

    @Test
    fun `value returns original string`() {
        val rawId = "com.example.myapp"
        val appId = ApplicationId.fromUString(rawId.u).unwrap()

        assertEquals(rawId.u, appId.value)
    }

    @Test
    fun `instances with same value are equal`() {
        val rawId = "com.example.myapp"
        val instance1 = ApplicationId.fromUString(rawId.u).unwrap()
        val instance2 = ApplicationId.fromUString(rawId.u).unwrap()

        assertEquals(instance1, instance2)
    }
}
