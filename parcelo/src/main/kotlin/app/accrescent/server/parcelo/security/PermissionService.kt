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
    fun hasPermission(
        resource: ObjectReference,
        permission: Permission,
        subject: ObjectReference,
    ): Boolean {
        if (subject.type != ObjectType.USER) return false

        return when (resource.type) {
            ObjectType.APP -> when (permission) {
                Permission.CREATE_APP_EDIT,
                Permission.UPDATE,
                Permission.VIEW,
                Permission.VIEW_EXISTENCE,
                    -> OrganizationRelationshipSet
                    .findByAppIdAndUserId(resource.id, subject.id)
                    ?.owner == true

                else -> false
            }

            ObjectType.APP_DRAFT -> when (permission) {
                Permission.CREATE_LISTING,
                Permission.DELETE,
                Permission.REPLACE_LISTING_ICON,
                Permission.REPLACE_PACKAGE,
                Permission.SUBMIT,
                Permission.UPDATE,
                Permission.VIEW,
                Permission.VIEW_EXISTENCE -> {
                    val isOrgOwner = OrganizationRelationshipSet
                        .findByAppDraftIdAndUserId(resource.id, subject.id)
                        ?.owner == true
                    val (isReviewer, isPublisher) = AppDraftRelationshipSet
                        .findByAppDraftIdAndUserId(resource.id, subject.id)
                        .let { Pair(it?.reviewer == true, it?.publisher == true) }

                    isOrgOwner || isReviewer || isPublisher
                }

                Permission.PUBLISH -> AppDraftRelationshipSet
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.publisher == true

                Permission.REVIEW -> AppDraftRelationshipSet
                    .findByAppDraftIdAndUserId(resource.id, subject.id)
                    ?.reviewer == true

                else -> false
            }

            ObjectType.APP_EDIT -> when (permission) {
                Permission.REVIEW -> AppEditRelationshipSet
                    .findByAppEditIdAndUserId(resource.id, subject.id)
                    ?.reviewer == true

                Permission.CREATE_LISTING,
                Permission.REPLACE_LISTING_ICON,
                Permission.REPLACE_PACKAGE,
                Permission.SUBMIT,
                Permission.UPDATE,
                Permission.VIEW,
                Permission.VIEW_EXISTENCE -> {
                    val isOrgOwner = OrganizationRelationshipSet
                        .findByAppEditIdAndUserId(resource.id, subject.id)
                        ?.owner == true
                    val (isReviewer, isPublisher) = AppDraftRelationshipSet
                        .findByAppDraftIdAndUserId(resource.id, subject.id)
                        .let { Pair(it?.reviewer == true, it?.publisher == true) }

                    isOrgOwner || isReviewer || isPublisher
                }

                else -> false
            }

            ObjectType.ORGANIZATION -> when (permission) {
                Permission.CREATE_APP_DRAFT,
                Permission.VIEW,
                Permission.VIEW_EXISTENCE -> OrganizationRelationshipSet
                    .findByOrganizationIdAndUserId(resource.id, subject.id)
                    ?.owner == true

                else -> false
            }

            ObjectType.USER -> when (permission) {
                Permission.UPDATE,
                Permission.UPDATE_ROLES -> {
                    val user = User.findById(subject.id) ?: return false

                    user.oidcProvider == OidcProvider.fromConfig(config.admin().oidcProvider())
                            && user.oidcSubject == config.admin().oidcSubject()
                }

                else -> false
            }
        }
    }
}
