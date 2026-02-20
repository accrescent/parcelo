// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftRelationshipSet
import app.accrescent.server.parcelo.data.AppEditRelationshipSet
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
            is HasPermissionRequest.ViewApp,
            is HasPermissionRequest.ViewAppExistence -> OrganizationRelationshipSet
                .findByAppIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.CreateAppDraftListing,
            is HasPermissionRequest.DeleteAppDraft,
            is HasPermissionRequest.DeleteAppDraftListing,
            is HasPermissionRequest.DownloadAppDraft,
            is HasPermissionRequest.DownloadAppDraftListingIcons,
            is HasPermissionRequest.ReplaceAppDraftListingIcon,
            is HasPermissionRequest.ReplaceAppDraftPackage,
            is HasPermissionRequest.SubmitAppDraft,
            is HasPermissionRequest.UpdateAppDraft,
            is HasPermissionRequest.ViewAppDraft -> OrganizationRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.PublishAppDraft -> AppDraftRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.publisher == true

            is HasPermissionRequest.ReviewAppDraft -> AppDraftRelationshipSet
                .findByAppDraftIdAndUserId(request.resourceId, request.subjectId)
                ?.reviewer == true

            is HasPermissionRequest.ViewAppDraftExistence -> {
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
            is HasPermissionRequest.DownloadAppEdit,
            is HasPermissionRequest.DownloadAppEditListingIcons,
            is HasPermissionRequest.ReplaceAppEditListingIcon,
            is HasPermissionRequest.ReplaceAppEditPackage,
            is HasPermissionRequest.SubmitAppEdit,
            is HasPermissionRequest.UpdateAppEdit,
            is HasPermissionRequest.ViewAppEdit -> OrganizationRelationshipSet
                .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.ReviewAppEdit -> AppEditRelationshipSet
                .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                ?.reviewer == true

            is HasPermissionRequest.ViewAppEditExistence -> {
                val isOrgOwner = OrganizationRelationshipSet
                    .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                    ?.owner == true
                val isReviewer = AppEditRelationshipSet
                    .findByAppEditIdAndUserId(request.resourceId, request.subjectId)
                    ?.reviewer == true

                isOrgOwner || isReviewer
            }

            is HasPermissionRequest.CreateAppDraft,
            is HasPermissionRequest.ViewOrganization,
            is HasPermissionRequest.ViewOrganizationExistence -> OrganizationRelationshipSet
                .findByOrganizationIdAndUserId(request.resourceId, request.subjectId)
                ?.owner == true

            is HasPermissionRequest.UpdateUser,
            is HasPermissionRequest.UpdateUserRoles -> User
                .findById(request.subjectId)
                ?.let { isAdmin(it) } == true

            is HasPermissionRequest.ViewUserExistence -> if (request.resourceId == request.subjectId) {
                // A user should always be able to view the existence of themselves
                true
            } else {
                // Admins can view the existence of any user
                User
                    .findById(request.subjectId)
                    ?.let { isAdmin(it) } == true
            }
        }
    }

    private fun isAdmin(user: User): Boolean {
        return user.oidcProvider == OidcProvider.fromConfig(config.admin().oidcProvider())
                && user.oidcSubject == config.admin().oidcSubject()
    }
}
