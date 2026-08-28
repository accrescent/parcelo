// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.domain.android.ApkSetParseError
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import java.time.OffsetDateTime

sealed class AppDraftUploadProcessingError {
    /**
     * The app draft is immutable because it has already been submitted.
     */
    data object AppDraftSubmitted : AppDraftUploadProcessingError()

    /**
     * Parsing the uploaded APK set failed.
     *
     * @property error the original [ApkSetParseError] that caused this error.
     */
    data class ApkSetParseFailed(val error: ApkSetParseError) : AppDraftUploadProcessingError()
}

sealed class PendingAppDraftUpload {
    abstract val id: UString
    abstract val appDraftId: UString
    abstract val objectKey: UString
    abstract val createTime: OffsetDateTime

    data class Incomplete(
        override val id: UString,
        override val appDraftId: UString,
        override val objectKey: UString,
        override val createTime: OffsetDateTime,
        val externalBlobId: UString,
    ) : PendingAppDraftUpload()

    data class Completed(
        override val id: UString,
        override val appDraftId: UString,
        override val objectKey: UString,
        override val createTime: OffsetDateTime,
        val result: Either<AppDraftUploadProcessingError, Unit>,
    ) : PendingAppDraftUpload()

    val optionalResult: Option<Either<AppDraftUploadProcessingError, Unit>>
        get() = when (this) {
            is Incomplete -> None
            is Completed -> Some(result)
        }
}
