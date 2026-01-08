// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.createPublisherRequest
import app.accrescent.appstore.publish.v1alpha1.createReviewerRequest
import app.accrescent.appstore.publish.v1alpha1.getAppRequest
import app.accrescent.appstore.publish.v1alpha1.getSelfRequest
import app.accrescent.appstore.publish.v1alpha1.listAppsRequest
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsRequest
import app.accrescent.appstore.v1.DeviceAttributes
import app.accrescent.appstore.v1.getAppDownloadInfoRequest
import app.accrescent.appstore.v1.getAppListingRequest
import app.accrescent.appstore.v1.getAppPackageInfoRequest
import app.accrescent.appstore.v1.getAppUpdateInfoRequest
import app.accrescent.appstore.v1.listAppListingsRequest
import app.accrescent.server.parcelo.testutil.ApiUtils
import com.google.protobuf.TextFormat
import io.grpc.Status
import io.grpc.StatusException
import io.quarkus.test.junit.QuarkusIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val ORGANIZATION_APP_DRAFT_LIMIT = 3

private const val ANCIENT_DEVICE_DEVICE_ATTRIBUTES_PATH = "ancient-device-device-attributes.txtpb"
private const val PIXEL_9_EMULATOR_DEVICE_ATTRIBUTES_PATH = "pixel-9-emulator-device-attributes.txtpb"

@QuarkusIntegrationTest
class ApiIT {
    val storeAppService = ApiUtils.getStoreAppServiceStub()

    val ancientDeviceDeviceAttributes: DeviceAttributes = javaClass
        .classLoader
        .getResourceAsStream(ANCIENT_DEVICE_DEVICE_ATTRIBUTES_PATH)!!
        .use { resourceStream ->
            val builder = DeviceAttributes.newBuilder()
            resourceStream.reader().use { TextFormat.merge(it, builder) }
            builder.build()
        }
    val pixel9EmulatorDeviceAttributes: DeviceAttributes = javaClass
        .classLoader
        .getResourceAsStream(PIXEL_9_EMULATOR_DEVICE_ATTRIBUTES_PATH)!!
        .use { resourceStream ->
            val builder = DeviceAttributes.newBuilder()
            resourceStream.reader().use { TextFormat.merge(it, builder) }
            builder.build()
        }

    companion object {
        val user1Token = ApiUtils.generateSessionToken("user1")
        val user2Token = ApiUtils.generateSessionToken("user2")

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Create the reviewer and publisher we use in this class
            val adminToken = ApiUtils.generateSessionToken("admin1")

            val reviewerUserId = ApiUtils
                .getUserServiceStub(ApiUtils.generateSessionToken("reviewer1"))
                .getSelf(getSelfRequest {})
                .userId
            ApiUtils
                .getReviewerServiceStub(adminToken)
                .createReviewer(createReviewerRequest {
                    userId = reviewerUserId
                    email = "reviewer1@example.com"
                })

            val publisherUserId = ApiUtils
                .getUserServiceStub(ApiUtils.generateSessionToken("publisher1"))
                .getSelf(getSelfRequest {})
                .userId
            ApiUtils
                .getPublisherServiceStub(adminToken)
                .createPublisher(createPublisherRequest {
                    userId = publisherUserId
                    email = "publisher1@example.com"
                })

            // Publish the valid example app
            ApiUtils.publishApp("user2", "reviewer1", "publisher1", "valid")
        }
    }

    @Test
    fun userRequestsListingForUnknownApp() {
        val request = getAppListingRequest {
            appId = "non.existent.app"
            preferredLanguages.add("en-US")
        }

        // Assert that the listing doesn't exist
        val exception = assertThrows<StatusException> { storeAppService.getAppListing(request) }
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun userRequestsListingForPublishedApp() {
        val request = getAppListingRequest {
            appId = "com.example.valid"
            preferredLanguages.add("en-US")
        }
        val listing = storeAppService.getAppListing(request).listing

        // Assert the listing matches what was published
        assertEquals("com.example.valid", listing.appId)
        assertEquals("en-US", listing.language)
        assertEquals("Example Valid App", listing.name)
        assertEquals("An example valid app", listing.shortDescription)
    }

    @Test
    fun userListsAppListingsWithOvershoot() {
        val request = listAppListingsRequest { skip = Int.MAX_VALUE }
        val response = storeAppService.listAppListings(request)

        // If there are no more listings, nextPageToken should not be present
        assertFalse(response.hasNextPageToken())
    }

    @Test
    fun userRequestsPackageInfoForUnknownApp() {
        val request = getAppPackageInfoRequest { appId = "non.existent.app" }

        // Assert the package info doesn't exist
        val exception = assertThrows<StatusException> { storeAppService.getAppPackageInfo(request) }
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun userRequestsPackageInfoForPublishedApp() {
        val request = getAppPackageInfoRequest { appId = "com.example.valid" }
        val packageInfo = storeAppService.getAppPackageInfo(request).packageInfo

        // Assert the package info matches what was published
        assertEquals(2, packageInfo.versionCode)
        assertEquals("2.0", packageInfo.versionName)
    }

    @Test
    fun userRequestsDownloadInfoForPublishedAppWithCompatibleDevice() {
        val request = getAppDownloadInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = pixel9EmulatorDeviceAttributes
        }
        val downloadInfo = storeAppService.getAppDownloadInfo(request).appDownloadInfo

        // Assert download info was retrieved successfully
        assertTrue(downloadInfo.splitDownloadInfoCount > 0)
    }

    @Test
    fun userRequestsDownloadInfoForPublishedAppWithIncompatibleDevice() {
        val request = getAppDownloadInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = ancientDeviceDeviceAttributes
        }

        // Assert that the device is detected as incompatible
        val exception = assertThrows<StatusException> { storeAppService.getAppDownloadInfo(request) }
        assertEquals(Status.Code.FAILED_PRECONDITION, exception.status.code)
    }

    @Test
    fun userRequestsUpdateInfoForPublishedAppWithCompatibleDeviceWhenNoUpdateIsAvailable() {
        val request = getAppUpdateInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = pixel9EmulatorDeviceAttributes
            baseVersionCode = 2
        }
        val response = storeAppService.getAppUpdateInfo(request)

        // Assert that the response indicates there is no update available
        assertFalse(response.hasAppUpdateInfo())
    }

    @Test
    fun userRequestsUpdateInfoForPublishedAppWithCompatibleDeviceWhenUpdateIsAvailable() {
        val request = getAppUpdateInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = pixel9EmulatorDeviceAttributes
            baseVersionCode = 1
        }

        val response = storeAppService.getAppUpdateInfo(request)

        // Assert that update info was retrieved successfully
        assertTrue(response.hasAppUpdateInfo())
        assertTrue(response.appUpdateInfo.splitUpdateInfoCount > 0)
    }

    @Test
    fun userRequestsUpdateInfoForPublishedAppWithIncompatibleDeviceWhenUpdateIsAvailable() {
        val request = getAppUpdateInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = ancientDeviceDeviceAttributes
            baseVersionCode = 1
        }

        // Assert that the device is detected as incompatible
        val exception = assertThrows<StatusException> { storeAppService.getAppUpdateInfo(request) }
        assertEquals(Status.Code.FAILED_PRECONDITION, exception.status.code)
    }

    @Test
    fun developerTriesToCreateAppDraftsBeyondOrgLimit() {
        val organizationService = ApiUtils.getOrganizationServiceStub(user1Token)
        val appDraftService = ApiUtils.getAppDraftServiceStub(user1Token)
        val organizationId = organizationService
            .listMyOrganizations(listMyOrganizationsRequest {})
            .organizationsList[0]
            .id

        // We should be able to successfully create as many as ORGANIZATION_APP_DRAFT_LIMIT app
        // drafts without issue
        val request = createAppDraftRequest { this.organizationId = organizationId }
        repeat(ORGANIZATION_APP_DRAFT_LIMIT) {
            appDraftService.createAppDraft(request)
        }

        // Creating app drafts beyond ORGANIZATION_APP_DRAFT_LIMIT should fail because of exceeding
        // the organization quota
        val exception = assertThrows<StatusException> { appDraftService.createAppDraft(request) }
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, exception.status.code)
    }

    @Test
    fun developerRequestsPublishedAppWithAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user2Token)

        val request = getAppRequest { appId = "com.example.valid" }
        val app = appService.getApp(request).app

        // Assert that the app is retrieved successfully
        assertEquals("com.example.valid", app.id)
        assertEquals("en-US", app.defaultListingLanguage)
    }

    @Test
    fun developerRequestsPublishedAppWithNoAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user1Token)

        val request = getAppRequest { appId = "com.example.valid" }
        val exception = assertThrows<StatusException> { appService.getApp(request) }

        // Assert that the developer can't access the app
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun developerListsPublishedAppsWithAuthorizationForOne() {
        val appService = ApiUtils.getDevAppServiceStub(user2Token)

        val apps = appService.listApps(listAppsRequest {}).appsList

        // Assert that the expected app is included in the response
        assertEquals(1, apps.size)
        assertEquals("com.example.valid", apps[0].id)
        assertEquals("en-US", apps[0].defaultListingLanguage)
    }

    @Test
    fun developerListsPublishedAppsWithNoAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user1Token)

        val apps = appService.listApps(listAppsRequest {}).appsList

        // Assert that the developer can't access the app
        assertEquals(0, apps.size)
    }
}
