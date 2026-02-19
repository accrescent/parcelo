// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppDraft
import app.accrescent.server.parcelo.data.AppDraftRelationshipSet
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppEditRelationshipSet
import app.accrescent.server.parcelo.data.AppPackage
import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.Organization
import app.accrescent.server.parcelo.data.OrganizationRelationshipSet
import app.accrescent.server.parcelo.data.User
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.util.UUID

private const val TEST_APP_ID = "com.example.app"
private const val TEST_APP_DRAFT_ID = "ad_3kij4XC0fQH88oA1e5w1z0"
private const val TEST_APP_EDIT_ID = "ae_1xpp2iRrFYD5YJu0OoMq0i"
private val TEST_APP_PACKAGE_ID = UUID.fromString("ec5371bb-2471-4e7d-8beb-ec10fd2f4749")
private const val TEST_ORGANIZATION_ID = "org_4YucHKDdgFrZBfqotNhUCk"

private const val TEST_ADMIN_USER_ID = "user_23xNQqvWAhJSjTVHUpNIzk"
private const val TEST_USER_ID = "user_2jJhXL1SXJDbWdCJeNRsHA"
private const val TEST_REVIEWER_USER_ID = "user_6gD8P9iTHTXZ5KtssBcxDv"
private const val TEST_PUBLISHER_USER_ID = "user_3MjZ3zHQjR9FRKk57pdRlC"

@QuarkusTest
class PermissionServiceTest {
    @Inject
    lateinit var permissionService: PermissionService

    @TestTransaction
    @ParameterizedTest
    @MethodSource("genParamsForHasPermissionReturnsTrueWhenRequired")
    fun hasPermissionReturnsTrueWhenRequired(params: HasPermissionReturnsTrueWhenRequiredParams) {
        params.setUpData()

        val result = permissionService.hasPermission(
            resource = params.resource,
            permission = params.permission,
            subject = ObjectReference(ObjectType.USER, params.userId),
        )

        assertTrue(result)
    }

    private companion object {
        @JvmStatic
        private fun genParamsForHasPermissionReturnsTrueWhenRequired():
                List<HasPermissionReturnsTrueWhenRequiredParams> {
            return listOf(
                // App permissions
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP, TEST_APP_ID),
                    permission = Permission.CREATE_APP_EDIT,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP, TEST_APP_ID),
                    permission = Permission.UPDATE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP, TEST_APP_ID),
                    permission = Permission.VIEW,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP, TEST_APP_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppData() },
                ),
                // App draft permissions
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.CREATE_LISTING,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.DELETE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.DELETE_LISTING,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.DOWNLOAD,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.DOWNLOAD_LISTING_ICONS,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.PUBLISH,
                    userId = TEST_PUBLISHER_USER_ID,
                    setUpData = { setUpAppDraftPublisherData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.REPLACE_LISTING_ICON,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.REPLACE_PACKAGE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.REVIEW,
                    userId = TEST_REVIEWER_USER_ID,
                    setUpData = { setUpAppDraftReviewerData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.SUBMIT,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.UPDATE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.VIEW,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppDraftData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_PUBLISHER_USER_ID,
                    setUpData = { setUpAppDraftPublisherData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_DRAFT, TEST_APP_DRAFT_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_REVIEWER_USER_ID,
                    setUpData = { setUpAppDraftReviewerData() },
                ),
                // App edit permissions
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.CREATE_LISTING,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.DELETE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.DELETE_LISTING,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.DOWNLOAD,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.DOWNLOAD_LISTING_ICONS,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.REPLACE_LISTING_ICON,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.REPLACE_PACKAGE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.REVIEW,
                    userId = TEST_REVIEWER_USER_ID,
                    setUpData = { setUpAppEditReviewerData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.SUBMIT,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.UPDATE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.VIEW,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpAppEditData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.APP_EDIT, TEST_APP_EDIT_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_REVIEWER_USER_ID,
                    setUpData = { setUpAppEditReviewerData() },
                ),
                // Organization permissions
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.ORGANIZATION, TEST_ORGANIZATION_ID),
                    permission = Permission.CREATE_APP_DRAFT,
                    userId = TEST_USER_ID,
                    setUpData = { setUpOrganizationData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.ORGANIZATION, TEST_ORGANIZATION_ID),
                    permission = Permission.VIEW,
                    userId = TEST_USER_ID,
                    setUpData = { setUpOrganizationData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.ORGANIZATION, TEST_ORGANIZATION_ID),
                    permission = Permission.VIEW_EXISTENCE,
                    userId = TEST_USER_ID,
                    setUpData = { setUpOrganizationData() },
                ),
                // User permissions
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.USER, TEST_USER_ID),
                    permission = Permission.UPDATE,
                    userId = TEST_ADMIN_USER_ID,
                    setUpData = { setUpUserAdminData() },
                ),
                HasPermissionReturnsTrueWhenRequiredParams(
                    resource = ObjectReference(ObjectType.USER, TEST_USER_ID),
                    permission = Permission.UPDATE_ROLES,
                    userId = TEST_ADMIN_USER_ID,
                    setUpData = { setUpUserAdminData() },
                ),
            )
        }

        private fun setUpAppData() {
            createOrgWithOwner()
            createApp()
        }

        private fun setUpAppDraftData() {
            createOrgWithOwner()
            createAppDraft()
        }

        private fun setUpAppDraftPublisherData() {
            setUpAppDraftData()
            User(
                id = TEST_PUBLISHER_USER_ID,
                oidcProvider = OidcProvider.UNKNOWN,
                oidcIssuer = "http://localhost",
                oidcSubject = "publisher-1",
                email = "example@example.com",
                reviewer = false,
                publisher = true,
            )
                .persist()
            AppDraftRelationshipSet(
                appDraftId = TEST_APP_DRAFT_ID,
                userId = TEST_PUBLISHER_USER_ID,
                reviewer = false,
                publisher = true,
            )
                .persist()
        }

        private fun setUpAppDraftReviewerData() {
            setUpAppDraftData()
            User(
                id = TEST_REVIEWER_USER_ID,
                oidcProvider = OidcProvider.UNKNOWN,
                oidcIssuer = "http://localhost",
                oidcSubject = "reviewer-1",
                email = "example@example.com",
                reviewer = true,
                publisher = false,
            )
                .persist()
            AppDraftRelationshipSet(
                appDraftId = TEST_APP_DRAFT_ID,
                userId = TEST_REVIEWER_USER_ID,
                reviewer = true,
                publisher = false,
            )
                .persist()
        }

        private fun setUpAppEditData() {
            setUpAppData()
            createAppEdit()
        }

        private fun setUpAppEditReviewerData() {
            setUpAppEditData()
            User(
                id = TEST_REVIEWER_USER_ID,
                oidcProvider = OidcProvider.UNKNOWN,
                oidcIssuer = "http://localhost",
                oidcSubject = "reviewer-1",
                email = "example@example.com",
                reviewer = true,
                publisher = false,
            )
                .persist()
            AppEditRelationshipSet(
                appEditId = TEST_APP_EDIT_ID,
                userId = TEST_REVIEWER_USER_ID,
                reviewer = true,
            )
                .persist()
        }

        private fun setUpOrganizationData() {
            createOrgWithOwner()
        }

        private fun setUpUserAdminData() {
            User(
                id = TEST_ADMIN_USER_ID,
                oidcProvider = OidcProvider.LOCAL,
                oidcIssuer = "http://localhost",
                oidcSubject = "c54374b4-b1f6-4d4b-8cb9-d6cfea172a58",
                email = "example@example.com",
                reviewer = false,
                publisher = false,
            )
                .persist()
        }

        private fun createApp() {
            AppPackage(
                id = TEST_APP_PACKAGE_ID,
                bucketId = "app-packages",
                objectId = "app-package-1",
                uploadPubSubEventTime = OffsetDateTime.parse("2000-01-01T00:00:00Z"),
                appId = TEST_APP_ID,
                versionCode = 1,
                versionName = "1.0",
                targetSdk = 36,
                signingCertificate = ByteArray(0),
                buildApksResult = ByteArray(0),
            )
                .persist()
            App(
                id = TEST_APP_ID,
                defaultListingLanguage = "en-US",
                organizationId = TEST_ORGANIZATION_ID,
                entityTag = 0,
                appPackageId = TEST_APP_PACKAGE_ID,
                publiclyListed = true,
            )
                .persist()
        }

        private fun createAppDraft() {
            AppDraft(
                id = TEST_APP_DRAFT_ID,
                organizationId = TEST_ORGANIZATION_ID,
                createdAt = OffsetDateTime.parse("2000-01-01T00:00:00Z"),
                appPackageId = null,
                defaultListingLanguage = null,
                submittedAt = null,
                reviewId = null,
                publishing = false,
                publishedAt = null,
            )
                .persist()
        }

        private fun createAppEdit() {
            AppEdit(
                id = TEST_APP_EDIT_ID,
                appId = TEST_APP_ID,
                createdAt = OffsetDateTime.parse("2000-01-01T00:00:00Z"),
                expectedAppEntityTag = 0,
                defaultListingLanguage = "en-US",
                appPackageId = TEST_APP_PACKAGE_ID,
                submittedAt = null,
                reviewId = null,
                publishing = false,
                publishedAt = null,
            )
                .persist()
        }

        private fun createOrgWithOwner() {
            Organization(id = TEST_ORGANIZATION_ID).persist()
            User(
                id = TEST_USER_ID,
                oidcProvider = OidcProvider.UNKNOWN,
                oidcIssuer = "http://localhost",
                oidcSubject = "user-1",
                email = "example@example.com",
                reviewer = false,
                publisher = false,
            )
                .persist()
            OrganizationRelationshipSet(
                organizationId = TEST_ORGANIZATION_ID,
                userId = TEST_USER_ID,
                owner = true,
            )
                .persist()
        }
    }
}

data class HasPermissionReturnsTrueWhenRequiredParams(
    val resource: ObjectReference,
    val permission: Permission,
    val userId: String,
    val setUpData: () -> Unit,
)
