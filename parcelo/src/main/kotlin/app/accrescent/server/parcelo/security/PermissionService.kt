// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.OrganizationAcl
import jakarta.transaction.Transactional

object PermissionService {
    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewOrganization(userId: String, organizationId: String): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canViewOrganization == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanCreateAppDraftsInOrganization(userId: String, organizationId: String): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canCreateAppDrafts == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanCreateAppEditForApp(userId: String, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canEditApps == true
    }

    @Transactional
    fun userCanViewApp(userId: String, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canViewApps == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppDraft(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canView == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppDraftExistence(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canViewExistence == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppExistence(userId: String, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canViewApps == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanEditAppDraftListings(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canEditListings == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanReplaceAppDraftPackage(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReplacePackage == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanReviewAppDraft(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReview == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanSubmitAppDraft(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canSubmit == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanDeleteAppDraft(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canDelete == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanPublishAppDraft(userId: String, appDraftId: String): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canPublish == true
    }
}
