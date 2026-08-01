// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

import arrow.core.Either

interface AppDraftApi {
    fun createAppDraft(
        callerUserId: String,
        request: CreateAppDraftRequest,
    ): Either<CreateAppDraftError, CreateAppDraftResponse>

    fun getAppDraft(
        callerUserId: String,
        request: GetAppDraftRequest,
    ): Either<GetAppDraftError, GetAppDraftResponse>

    fun listAppDrafts(
        callerUserId: String,
        request: ListAppDraftsRequest,
    ): Either<ListAppDraftsError, ListAppDraftsResponse>

    fun uploadAppDraft(
        callerUserId: String,
        request: UploadAppDraftRequest,
    ): Either<UploadAppDraftError, UploadAppDraftResponse>

    fun downloadAppDraft(
        callerUserId: String,
        request: DownloadAppDraftRequest,
    ): Either<DownloadAppDraftError, DownloadAppDraftResponse>

    fun updateAppDraft(
        callerUserId: String,
        request: UpdateAppDraftRequest,
    ): Either<UpdateAppDraftError, Unit>

    fun submitAppDraft(
        callerUserId: String,
        request: SubmitAppDraftRequest,
    ): Either<SubmitAppDraftError, Unit>

    fun deleteAppDraft(
        callerUserId: String,
        request: DeleteAppDraftRequest,
    ): Either<DeleteAppDraftError, Unit>

    fun createAppDraftListing(
        callerUserId: String,
        request: CreateAppDraftListingRequest,
    ): Either<CreateAppDraftListingError, CreateAppDraftListingResponse>

    fun getAppDraftListing(
        callerUserId: String,
        request: GetAppDraftListingRequest,
    ): Either<GetAppDraftListingError, GetAppDraftListingResponse>

    /**
     * Lists app draft listings for a given app draft.
     *
     * Returned listings are sorted alphabetically by their language's BCP-47 code.
     */
    fun listAppDraftListings(
        callerUserId: String,
        request: ListAppDraftListingsRequest,
    ): Either<ListAppDraftListingsError, ListAppDraftListingsResponse>

    fun updateAppDraftListing(
        callerUserId: String,
        request: UpdateAppDraftListingRequest,
    ): Either<UpdateAppDraftListingError, Unit>

    /**
     * Requests an upload for an app draft listing's icon.
     */
    fun uploadAppDraftListingIcon(
        callerUserId: String,
        request: UploadAppDraftListingIconRequest,
    ): Either<UploadAppDraftListingIconError, UploadAppDraftListingIconResponse>

    /**
     * Requests a download for an app draft listing's icon.
     */
    fun downloadAppDraftListingIcon(
        callerUserId: String,
        request: DownloadAppDraftListingIconRequest,
    ): Either<DownloadAppDraftListingIconError, DownloadAppDraftListingIconResponse>

    /**
     * Deletes an app draft listing.
     *
     * If the listing is its app draft's default, the app draft's default listing is unset.
     */
    fun deleteAppDraftListing(
        callerUserId: String,
        request: DeleteAppDraftListingRequest,
    ): Either<DeleteAppDraftListingError, Unit>

    /**
     * Publishes a given app draft.
     */
    fun publishAppDraft(
        callerUserId: String,
        request: PublishAppDraftRequest,
    ): Either<PublishAppDraftError, PublishAppDraftResponse>
}
