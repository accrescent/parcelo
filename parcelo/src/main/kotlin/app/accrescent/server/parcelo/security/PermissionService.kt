// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.AppDraftAcl
import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.OrganizationAcl
import app.accrescent.server.parcelo.data.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
class PermissionService @Inject constructor(private val config: ParceloConfig) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun hasPermission(
        resource: ObjectReference,
        permission: Permission,
        subject: ObjectReference,
    ): Boolean {
        if (subject.type != ObjectType.USER) return false

        return when (resource.type) {
            ObjectType.APP -> when (permission) {
                Permission.CREATE_APP_EDIT -> OrganizationAcl
                    .findByAppIdAndUserId(resource.id, subject.id)
                    ?.canEditApps == true

                Permission.VIEW -> OrganizationAcl
                    .findByAppIdAndUserId(resource.id, subject.id)
                    ?.canViewApps == true

                Permission.VIEW_EXISTENCE -> OrganizationAcl
                    .findByAppIdAndUserId(resource.id, subject.id)
                    ?.canViewApps == true

                else -> false
            }

            ObjectType.APP_DRAFT -> when (permission) {
                Permission.CREATE_LISTING -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canUpdate == true

                Permission.DELETE -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canDelete == true

                Permission.PUBLISH -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canPublish == true

                Permission.REPLACE_LISTING_ICON -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canUpdate == true

                Permission.REPLACE_PACKAGE -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canReplacePackage == true

                Permission.REVIEW -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canReview == true

                Permission.SUBMIT -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canSubmit == true

                Permission.UPDATE -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canUpdate == true

                Permission.VIEW -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canView == true

                Permission.VIEW_EXISTENCE -> AppDraftAcl
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.canViewExistence == true

                else -> false
            }

            ObjectType.APP_EDIT -> when (permission) {
                Permission.UPDATE,
                Permission.VIEW,
                Permission.VIEW_EXISTENCE,
                    -> OrganizationAcl
                    .findByAppEditIdAndUserId(resource.id, subject.id)
                    ?.canEditApps == true

                else -> false
            }

            ObjectType.ORGANIZATION -> when (permission) {
                Permission.CREATE_APP_DRAFT -> OrganizationAcl
                    .findByOrganizationIdAndUserId(resource.id, subject.id)
                    ?.canCreateAppDrafts == true

                Permission.VIEW -> OrganizationAcl
                    .findByOrganizationIdAndUserId(resource.id, subject.id)
                    ?.canViewOrganization == true

                Permission.VIEW_EXISTENCE -> OrganizationAcl
                    .findByOrganizationIdAndUserId(resource.id, subject.id)
                    ?.canViewOrganization == true

                else -> false
            }

            ObjectType.PLATFORM -> when (permission) {
                // Intentionally ignore resource.id since there is only one platform
                Permission.CREATE_PUBLISHER,
                Permission.CREATE_REVIEWER -> {
                    val user = User.findById(subject.id) ?: return false

                    user.oidcProvider == OidcProvider.fromConfig(config.admin().oidcProvider())
                            && user.oidcSubject == config.admin().oidcSubject()
                }

                else -> false
            }

            else -> false
        }
    }
}
