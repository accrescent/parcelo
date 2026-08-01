// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import arrow.core.Option
import java.time.OffsetDateTime

sealed class AppDraftListingIconUploadProcessingResult {
    data object Success : AppDraftListingIconUploadProcessingResult()

    sealed class Error : AppDraftListingIconUploadProcessingResult() {
        /**
         * The app draft is immutable because it has already been submitted.
         */
        data object AppDraftSubmitted : Error()

        /**
         * The uploaded file is not a valid PNG image.
         */
        data object InvalidImage : Error()

        /**
         * The uploaded image does not have the required dimensions.
         */
        data object IncorrectImageDimensions : Error()
    }
}

/**
 * An app draft listing icon upload which is pending processing.
 *
 * @property id the unique ID of this pending app draft listing icon upload.
 * @property appDraftListingId the ID of the app draft listing this upload is for.
 * @property externalBlobId the ID of the private external blob reserved for if processing this
 * upload succeeds.
 * @property objectKey the object key of the blob this upload may come from. Doubles as a
 * correlation key between the object upload event and this pending upload.
 * @property createTime the timestamp at which this entity was created.
 * @property result the result of processing this upload, if completed.
 */
data class PendingAppDraftListingIconUpload(
    val id: String,
    val appDraftListingId: String,
    val externalBlobId: String,
    val objectKey: String,
    val createTime: OffsetDateTime,
    val result: Option<AppDraftListingIconUploadProcessingResult>,
)
