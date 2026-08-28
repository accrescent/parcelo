// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.async

import app.accrescent.server.parcelo.core.text.UString
import java.time.OffsetDateTime

sealed interface UploadEvent {
    val bucketName: UString
    val objectKey: UString
    val eventTime: OffsetDateTime

    data class Local(
        override val bucketName: UString,
        override val objectKey: UString,
        override val eventTime: OffsetDateTime,
        val generation: Long,
    ) : UploadEvent

    data class Gcs(
        override val bucketName: UString,
        override val objectKey: UString,
        override val eventTime: OffsetDateTime,
        val generation: Long,
        val metaGeneration: Long,
    ) : UploadEvent
}
