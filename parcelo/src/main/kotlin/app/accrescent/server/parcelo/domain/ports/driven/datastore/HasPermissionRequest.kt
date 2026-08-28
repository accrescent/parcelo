// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.text.UString

sealed class HasPermissionRequest(val resourceId: UString, val subjectId: UString) {
    data class CreateAppDraft(private val organizationId: UString, private val userId: UString) :
        HasPermissionRequest(organizationId, userId)

    data class CreateAppDraftListing(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class DeleteAppDraft(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class DeleteAppDraftListing(private val appDraftListingId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftListingId, userId)

    data class DownloadAppDraft(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class DownloadAppDraftListingIcon(private val listingId: UString, private val userId: UString) :
        HasPermissionRequest(listingId, userId)

    data class ReplaceAppDraftPackage(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class SubmitAppDraft(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class UpdateApp(private val appId: UString, private val userId: UString) :
        HasPermissionRequest(appId, userId)

    data class UpdateAppDraft(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class UpdateAppDraftListing(private val listingId: UString, private val userId: UString) :
        HasPermissionRequest(listingId, userId)

    data class UploadAppDraftListingIcon(private val listingId: UString, private val userId: UString) :
        HasPermissionRequest(listingId, userId)

    data class ViewApp(private val appId: UString, private val userId: UString) :
        HasPermissionRequest(appId, userId)

    data class ViewAppDraft(private val appDraftId: UString, private val userId: UString) :
        HasPermissionRequest(appDraftId, userId)

    data class ViewAppDraftListing(private val listingId: UString, private val userId: UString) :
        HasPermissionRequest(listingId, userId)
}