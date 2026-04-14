// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.review

import app.accrescent.server.parcelo.data.App
import app.accrescent.server.parcelo.data.AppEdit
import app.accrescent.server.parcelo.data.AppEditListing
import app.accrescent.server.parcelo.data.AppListing
import app.accrescent.server.parcelo.data.AppPackage
import app.accrescent.server.parcelo.data.AppPackagePermission
import app.accrescent.server.parcelo.data.Image
import app.accrescent.server.parcelo.data.Organization
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.util.UUID

private const val APP_EDIT_ID = "ae_G27TpHwSD6ox7sOIcLUab"
private const val APP_ID = "com.example.app"
private const val APP_LISTING_ID = "al_adGW8SkSfP1WFDfmQ8y5xV"
private const val ORIGINAL_APP_LISTING_NAME = "Example App"
private const val ORIGINAL_APP_LISTING_SHORT_DESCRIPTION = "An example application"
private val DUMMY_TIMESTAMP = OffsetDateTime.parse("2000-01-01T00:00:00Z")
private val ORIGINAL_APP_PACKAGE_ID = UUID.fromString("4b8d5f54-6346-4ef9-bc3a-db651025fe42")
private val EDIT_APP_PACKAGE_ID = UUID.fromString("7c1a2b3d-4e5f-6789-0abc-def012345678")
private val ORIGINAL_LISTING_ICON_ID = UUID.fromString("ac384a6b-235a-47e5-99d1-7d6b5b300373")
private val EDIT_LISTING_ICON_ID = UUID.fromString("9803cbdd-7e59-46b2-be08-519df67b38cb")
private const val APP_EDIT_LISTING_EN_US_ID = "ael_WElnutSzGWHqKrImaxDT0t"
private const val APP_EDIT_LISTING_DE_DE_ID = "ael_S9826jy2vRPXUfE1EYXi76"

@QuarkusTest
class ReviewServiceTest {
    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var reviewService: ReviewService

    @TestTransaction
    @ParameterizedTest
    @MethodSource("genParamsForAppEditRequiresReviewReturnsCorrectValue")
    fun appEditRequiresReviewReturnsCorrectValue(
        params: AppEditRequiresReviewReturnsCorrectValueParams,
    ) {
        params.createAppEdit()
        // Flush inserts to the database and clear the L1 cache so that all entities are loaded
        // fresh from the database with properly initialized lazy proxies/collections
        entityManager.flush()
        entityManager.clear()
        val appEdit = AppEdit.findById(APP_EDIT_ID)!!

        val result = reviewService.appEditRequiresReview(appEdit)

        assertEquals(params.expectedResult, result)
    }

    private companion object {
        @JvmStatic
        private fun genParamsForAppEditRequiresReviewReturnsCorrectValue():
                List<AppEditRequiresReviewReturnsCorrectValueParams> {
            return listOf(
                // Listing changes
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Add new listing
                        createAppEditWithEditListing()
                        AppEditListing(
                            id = APP_EDIT_LISTING_DE_DE_ID,
                            appEditId = APP_EDIT_ID,
                            language = "de-DE",
                            name = "Beispiel-App",
                            shortDescription = "Eine Beispielanwendung",
                            iconImageId = null,
                        )
                            .persist()
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Delete listing (by not including it in the edit)
                        createApp()
                        createAppEdit()
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    // Modify listing name
                    createAppEdit = { createAppEditWithEditListing(name = "Modified App Name") },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Modify listing short description
                        createAppEditWithEditListing(
                            shortDescription = "$ORIGINAL_APP_LISTING_SHORT_DESCRIPTION (modified)",
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Modify listing icon
                        createEditIcon()
                        createAppEditWithEditListing(iconImageId = EDIT_LISTING_ICON_ID)
                    },
                ),
                // Permission changes
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Add reviewable permission
                        createAppEditWithPermissionChange(
                            editPermission = "android.permission.CAMERA" to null,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Add custom permission
                        createAppEditWithPermissionChange(
                            editPermission = "app.accrescent.client.permission.SHAVE_YAK" to null,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Remove reviewable permission
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.CAMERA" to 30,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Increase reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.CAMERA" to 30,
                            editPermission = "android.permission.CAMERA" to 35,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Decrease reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.CAMERA" to 35,
                            editPermission = "android.permission.CAMERA" to 30,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = true,
                    createAppEdit = {
                        // Remove reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.CAMERA" to 30,
                            editPermission = "android.permission.CAMERA" to null,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Increase non-reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.INTERNET" to 30,
                            editPermission = "android.permission.INTERNET" to 35,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Decrease non-reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.INTERNET" to 35,
                            editPermission = "android.permission.INTERNET" to 30,
                        )
                    },
                ),
                AppEditRequiresReviewReturnsCorrectValueParams(
                    expectedResult = false,
                    createAppEdit = {
                        // Remove non-reviewable permission maxSdkVersion
                        createAppEditWithPermissionChange(
                            originalPermission = "android.permission.INTERNET" to 30,
                            editPermission = "android.permission.INTERNET" to null,
                        )
                    },
                ),
            )
        }

        private fun createApp() {
            val organization = Organization(id = "org_3kYhYSaJdbgzkab9YVDqWP").also { it.persist() }
            val appPackage = AppPackage(
                id = ORIGINAL_APP_PACKAGE_ID,
                bucketId = "app-packages",
                objectId = "app-package-1",
                uploadPubSubEventTime = DUMMY_TIMESTAMP,
                appId = APP_ID,
                versionCode = 1,
                versionName = "1.0",
                targetSdk = 36,
                signingCertificate = ByteArray(0),
                buildApksResult = ByteArray(0),
            )
                .also { it.persist() }
            App(
                id = APP_ID,
                defaultListingLanguage = "en-US",
                organizationId = organization.id,
                entityTag = 0,
                appPackageId = appPackage.id,
                publiclyListed = true,
            )
                .persist()
            val icon = Image(
                id = ORIGINAL_LISTING_ICON_ID,
                bucketId = "images",
                objectId = "image-1",
                uploadPubSubEventTime = DUMMY_TIMESTAMP,
            )
                .also { it.persist() }
            AppListing(
                id = APP_LISTING_ID,
                appId = APP_ID,
                language = "en-US",
                name = ORIGINAL_APP_LISTING_NAME,
                shortDescription = ORIGINAL_APP_LISTING_SHORT_DESCRIPTION,
                iconImageId = icon.id,
            )
                .persist()
        }

        private fun createAppEdit(packageId: UUID = ORIGINAL_APP_PACKAGE_ID) {
            AppEdit(
                id = APP_EDIT_ID,
                appId = APP_ID,
                createdAt = DUMMY_TIMESTAMP,
                expectedAppEntityTag = 0,
                defaultListingLanguage = "en-US",
                appPackageId = packageId,
                submittedAt = null,
                reviewId = null,
                publishing = false,
                publishedAt = null,
            )
                .persist()
        }

        private fun createEditPackage(permission: Pair<String, Int?>? = null) {
            AppPackage(
                id = EDIT_APP_PACKAGE_ID,
                bucketId = "app-packages",
                objectId = "app-package-2",
                uploadPubSubEventTime = DUMMY_TIMESTAMP,
                appId = APP_ID,
                versionCode = 2,
                versionName = "2.0",
                targetSdk = 36,
                signingCertificate = ByteArray(0),
                buildApksResult = ByteArray(0),
            )
                .persist()
            if (permission != null) {
                AppPackagePermission(
                    appPackageId = EDIT_APP_PACKAGE_ID,
                    name = permission.first,
                    maxSdkVersion = permission.second,
                )
                    .persist()
            }
        }

        private fun createEditIcon() {
            Image(
                id = EDIT_LISTING_ICON_ID,
                bucketId = "images",
                objectId = "image-2",
                uploadPubSubEventTime = DUMMY_TIMESTAMP,
            )
                .persist()
        }

        private fun createAppEditWithEditListing(
            name: String = ORIGINAL_APP_LISTING_NAME,
            shortDescription: String = ORIGINAL_APP_LISTING_SHORT_DESCRIPTION,
            iconImageId: UUID? = ORIGINAL_LISTING_ICON_ID,
        ) {
            createApp()
            createAppEdit()
            AppEditListing(
                id = APP_EDIT_LISTING_EN_US_ID,
                appEditId = APP_EDIT_ID,
                language = "en-US",
                name = name,
                shortDescription = shortDescription,
                iconImageId = iconImageId,
            )
                .persist()
        }

        private fun createAppEditWithPermissionChange(
            originalPermission: Pair<String, Int?>? = null,
            editPermission: Pair<String, Int?>? = null,
        ) {
            createApp()
            if (originalPermission != null) {
                AppPackagePermission(
                    appPackageId = ORIGINAL_APP_PACKAGE_ID,
                    name = originalPermission.first,
                    maxSdkVersion = originalPermission.second,
                )
                    .persist()
            }
            createEditPackage(editPermission)
            createAppEdit(EDIT_APP_PACKAGE_ID)
            AppEditListing(
                id = APP_EDIT_LISTING_EN_US_ID,
                appEditId = APP_EDIT_ID,
                language = "en-US",
                name = ORIGINAL_APP_LISTING_NAME,
                shortDescription = ORIGINAL_APP_LISTING_SHORT_DESCRIPTION,
                iconImageId = ORIGINAL_LISTING_ICON_ID,
            )
                .persist()
        }
    }
}

data class AppEditRequiresReviewReturnsCorrectValueParams(
    val expectedResult: Boolean,
    val createAppEdit: () -> Unit,
)
