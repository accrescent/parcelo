// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BytesTest {
    @Test
    fun `instances with same contents are equal`() {
        val instance1 = Bytes("deadbeef".hexToByteArray())
        val instance2 = Bytes("deadbeef".hexToByteArray())

        assertEquals(instance1, instance2)
    }

    @Test
    fun `instances with same contents have same hashCode`() {
        val instance1 = Bytes("deadbeef".hexToByteArray())
        val instance2 = Bytes("deadbeef".hexToByteArray())

        assertEquals(instance1.hashCode(), instance2.hashCode())
    }
}
