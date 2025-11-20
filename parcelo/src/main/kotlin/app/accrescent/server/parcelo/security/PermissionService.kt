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
    fun userCanViewAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canView == true
    }

    @Transactional
    fun userCanReplaceAppDraftPackage(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canReplacePackage == true
    }

    @Transactional
    fun userCanDeleteAppDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canDelete == true
    }

    @Transactional
    fun userCanCreateListingsForDraft(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canCreateListings == true
    }

    @Transactional
    fun userCanDeleteAppDraftListings(userId: UUID, appDraftId: UUID): Boolean {
        return AppDraftAcl
            .findByAppDraftIdAndUserId(appDraftId = appDraftId, userId = userId)
            ?.canDeleteListings == true
    }
}
