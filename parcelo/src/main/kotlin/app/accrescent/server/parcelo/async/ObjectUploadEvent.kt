// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import java.time.OffsetDateTime

data class ObjectUploadEvent(
    val bucketId: String,
    val objectId: String,
    val timestamp: OffsetDateTime,
)
