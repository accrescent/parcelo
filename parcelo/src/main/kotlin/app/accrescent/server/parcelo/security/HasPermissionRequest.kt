// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

sealed class HasPermissionRequest(val resourceId: String, val subjectId: String) {
    // App permissions
    data class CreateAppEdit(private val appId: String, private val userId: String) :
        HasPermissionRequest(appId, userId)

    data class UpdateApp(private val appId: String, private val userId: String) :
        HasPermissionRequest(appId, userId)

    data class ViewApp(private val appId: String, private val userId: String) :
        HasPermissionRequest(appId, userId)

    // App draft permissions
    data class CreateAppDraft(private val organizationId: String, private val userId: String) :
        HasPermissionRequest(organizationId, userId)

    data class CreateAppDraftListing(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class DeleteAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class DownloadAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class PublishAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class ReplaceAppDraftPackage(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class ReviewAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class SubmitAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class UpdateAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    data class ViewAppDraft(private val appDraftId: String, private val userId: String) :
        HasPermissionRequest(appDraftId, userId)

    // App draft listing permissions
    data class DeleteAppDraftListing(private val appDraftListingId: String, private val userId: String) :
        HasPermissionRequest(appDraftListingId, userId)

    data class DownloadAppDraftListingIcon(
        private val appDraftListingId: String,
        private val userId: String,
    ) : HasPermissionRequest(appDraftListingId, userId)

    data class ReplaceAppDraftListingIcon(
        private val appDraftListingId: String,
        private val userId: String,
    ) : HasPermissionRequest(appDraftListingId, userId)

    // App edit permissions
    data class CreateAppEditListing(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class DeleteAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class DownloadAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class DownloadAppEditListingIcons(
        private val appEditId: String,
        private val userId: String,
    ) : HasPermissionRequest(appEditId, userId)

    data class ReplaceAppEditPackage(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class ReviewAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class SubmitAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class UpdateAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    data class ViewAppEdit(private val appEditId: String, private val userId: String) :
        HasPermissionRequest(appEditId, userId)

    // App edit listing permissions
    data class DeleteAppEditListing(private val appEditListingId: String, private val userId: String) :
        HasPermissionRequest(appEditListingId, userId)

    data class ReplaceAppEditListingIcon(
        private val appEditListingId: String,
        private val userId: String,
    ) : HasPermissionRequest(appEditListingId, userId)

    // Organization permissions
    data class ViewOrganization(private val organizationId: String, private val userId: String) :
        HasPermissionRequest(organizationId, userId)

    // User permissions
    data class UpdateUser(private val targetUserId: String, private val userId: String) :
        HasPermissionRequest(targetUserId, userId)

    data class UpdateUserRoles(private val targetUserId: String, private val userId: String) :
        HasPermissionRequest(targetUserId, userId)

    // Operation permissions
    data class ViewOperation(private val operationId: String, private val userId: String) :
        HasPermissionRequest(operationId, userId)
}
