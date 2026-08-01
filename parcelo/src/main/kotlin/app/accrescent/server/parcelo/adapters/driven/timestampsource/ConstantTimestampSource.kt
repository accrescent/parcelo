// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.adapters.driven.timestampsource

import app.accrescent.server.parcelo.domain.ports.driven.timestampsource.TimestampSource
import java.time.OffsetDateTime

/**
 * A [TimestampSource] which produces a constant timestamp value.
 *
 * [ConstantTimestampSource]'s [now] method will always return a timestamp representing the Unix
 * epoch.
 */
class ConstantTimestampSource : TimestampSource {
    private companion object {
        private val UNIX_EPOCH_TIMESTAMP = OffsetDateTime.parse("1970-01-01T00:00:00Z")
    }

    override fun now(): OffsetDateTime {
        return UNIX_EPOCH_TIMESTAMP
    }
}
