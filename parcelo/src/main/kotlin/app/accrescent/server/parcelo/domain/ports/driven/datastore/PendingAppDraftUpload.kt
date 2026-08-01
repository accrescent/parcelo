// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.domain.android.ApkSetParseError
import arrow.core.Either
import arrow.core.Option
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

/**
 * An app draft upload which is pending processing.
 *
 * @property id the unique ID of this pending app draft upload.
 * @property appDraftId the ID of the app draft this upload is for.
 * @property externalBlobId the ID of the private external blob reserved for if processing this
 * upload succeeds.
 * @property objectKey the object key of the blob this upload may come from. Doubles as a
 * correlation key between the object upload event and this pending upload.
 * @property createTime the timestamp at which this entity was created.
 * @property result the result of processing this upload, if completed.
 */
data class PendingAppDraftUpload(
    val id: String,
    val appDraftId: String,
    val externalBlobId: String,
    val objectKey: String,
    val createTime: OffsetDateTime,
    val result: Option<Either<AppDraftUploadProcessingError, Unit>>,
)
