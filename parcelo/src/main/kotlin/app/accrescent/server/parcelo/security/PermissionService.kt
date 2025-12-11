// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.OrganizationAcl
import jakarta.transaction.Transactional
import java.util.UUID

object PermissionService {
    @Transactional
    fun userCanViewOrganization(userId: UUID, organizationId: UUID): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canViewOrganization == true
    }

    @Transactional
    fun userCanCreateAppDraftsInOrganization(userId: UUID, organizationId: UUID): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canCreateAppDrafts == true
    }

    @Transactional
    fun userCanViewAppDraftExistence(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canViewExistence == true
    }

    @Transactional
    fun userCanEditAppDraftListings(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canEditListings == true
    }

    @Transactional
    fun userCanReplaceAppDraftPackage(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReplacePackage == true
    }

    @Transactional
    fun userCanReviewAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReview == true
    }

    @Transactional
    fun userCanSubmitAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canSubmit == true
    }

    @Transactional
    fun userCanDeleteAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canDelete == true
    }

    @Transactional
    fun userCanPublishAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canPublish == true
    }
}
