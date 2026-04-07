// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftRelationshipSet
import app.accrescent.server.parcelo.data.AppEditRelationshipSet
import app.accrescent.server.parcelo.data.BackgroundOperation
import app.accrescent.server.parcelo.data.BackgroundOperationType
import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.OrganizationRelationshipSet
import app.accrescent.server.parcelo.data.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
class PermissionService @Inject constructor(private val config: ParceloConfig) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun hasPermission(request: HasPermissionRequest): Boolean {
        return when (request) {
            is HasPermissionRequest.CreateAppEdit,
            is HasPermissionRequest.UpdateApp,
            is HasPermissionRequest.ViewApp -> OrganizationRelationshipSet
                .findByAppIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.CreateAppDraftListing,
            is HasPermissionRequest.DeleteAppDraft,
            is HasPermissionRequest.DeleteAppDraftListing,
            is HasPermissionRequest.ReplaceAppDraftListingIcon,
            is HasPermissionRequest.ReplaceAppDraftPackage,
            is HasPermissionRequest.SubmitAppDraft,
            is HasPermissionRequest.UpdateAppDraft -> OrganizationRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.DownloadAppDraft,
            is HasPermissionRequest.DownloadAppDraftListingIcons -> {
                val isOrgOwner = OrganizationRelationshipSet
                    .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                    ?.owner == true
                val isReviewer = AppDraftRelationshipSet
                    .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                    ?.reviewer == true

                isOrgOwner || isReviewer
            }

            is HasPermissionRequest.PublishAppDraft -> AppDraftRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.publisher == true

            is HasPermissionRequest.ReviewAppDraft -> AppDraftRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.reviewer == true

            is HasPermissionRequest.ViewAppDraft -> {
                val isOrgOwner = OrganizationRelationshipSet
                    .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                    ?.owner == true
                val (isReviewer, isPublisher) = AppDraftRelationshipSet
                    .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                    .let { Pair(it?.reviewer == true, it?.publisher == true) }

                isOrgOwner || isReviewer || isPublisher
            }

            is HasPermissionRequest.CreateAppEditListing,
            is HasPermissionRequest.DeleteAppEdit,
            is HasPermissionRequest.DeleteAppEditListing,
            is HasPermissionRequest.ReplaceAppEditListingIcon,
            is HasPermissionRequest.ReplaceAppEditPackage,
            is HasPermissionRequest.SubmitAppEdit,
            is HasPermissionRequest.UpdateAppEdit -> OrganizationRelationshipSet
                .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.ReviewAppEdit -> AppEditRelationshipSet
                .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                ?.reviewer == true

            is HasPermissionRequest.DownloadAppEdit,
            is HasPermissionRequest.DownloadAppEditListingIcons,
            is HasPermissionRequest.ViewAppEdit -> {
                val isOrgOwner = OrganizationRelationshipSet
                    .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                    ?.owner == true
                val isReviewer = AppEditRelationshipSet
                    .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                    ?.reviewer == true

                isOrgOwner || isReviewer
            }

            is HasPermissionRequest.CreateAppDraft,
            is HasPermissionRequest.ViewOrganization -> OrganizationRelationshipSet
                .findByOrganizationIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.UpdateUser,
            is HasPermissionRequest.UpdateUserRoles -> User
                .findById(request.subjectId)
                ?.let { isAdmin(it) } == true

            is HasPermissionRequest.ViewOperation -> {
                val operation = BackgroundOperation.findById(request.resourceId) ?: return false

                when (operation.type) {
                    BackgroundOperationType.PUBLISH_APP_DRAFT -> AppDraftRelationshipSet
                        .findByAppDraftIdAndUserId(operation.parentId, request.subjectId)
                        ?.publisher == true

                    BackgroundOperationType.UPLOAD_APP_DRAFT,
                    BackgroundOperationType.UPLOAD_APP_DRAFT_LISTING_ICON -> OrganizationRelationshipSet
                        .findByAppDraftIdAndUserId(operation.parentId, request.subjectId)
                        ?.owner == true

                    BackgroundOperationType.PUBLISH_APP_EDIT,
                    BackgroundOperationType.UPLOAD_APP_EDIT,
                    BackgroundOperationType.UPLOAD_APP_EDIT_LISTING_ICON -> OrganizationRelationshipSet
                        .findByAppEditIdAndUserId(operation.parentId, request.subjectId)
                        ?.owner == true
                }
            }
        }
    }

    private fun isAdmin(user: User): Boolean {
        return user.oidcProvider == OidcProvider.fromConfig(config.admin().oidcProvider())
                && user.oidcSubject == config.admin().oidcSubject()
    }
}
