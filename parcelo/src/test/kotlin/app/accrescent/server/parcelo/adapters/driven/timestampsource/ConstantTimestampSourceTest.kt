// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.adapters.driven.timestampsource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ConstantTimestampSourceTest {
    private companion object {
        private val UNIX_EPOCH_TIMESTAMP = OffsetDateTime.parse("1970-01-01T00:00:00Z")
    }

    @Test
    fun `now returns Unix epoch timestamp`() {
        val timestamp = ConstantTimestampSource().now()

        assertEquals(UNIX_EPOCH_TIMESTAMP, timestamp)
    }
}
