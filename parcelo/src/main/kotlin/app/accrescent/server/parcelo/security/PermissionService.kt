// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.OrganizationAcl
import jakarta.transaction.Transactional
import java.util.UUID

object PermissionService {
    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewOrganization(userId: UUID, organizationId: UUID): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canViewOrganization == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanCreateAppDraftsInOrganization(userId: UUID, organizationId: UUID): Boolean {
        return OrganizationAcl
            .findByOrganizationIdAndUserId(organizationId = organizationId, userId = userId)
            ?.canCreateAppDrafts == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanCreateAppEditForApp(userId: UUID, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canEditApps == true
    }

    @Transactional
    fun userCanViewApp(userId: UUID, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canViewApps == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canView == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppDraftExistence(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canViewExistence == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanViewAppExistence(userId: UUID, appId: String): Boolean {
        return OrganizationAcl
            .findByAppIdAndUserId(appId = appId, userId = userId)
            ?.canViewApps == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanEditAppDraftListings(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canEditListings == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanReplaceAppDraftPackage(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReplacePackage == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanReviewAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReview == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanSubmitAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canSubmit == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanDeleteAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canDelete == true
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun userCanPublishAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canPublish == true
    }
}
