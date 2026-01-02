// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.appstore

import app.accrescent.appstore.publish.v1alpha1.createPublisherRequest
import app.accrescent.appstore.publish.v1alpha1.createReviewerRequest
import app.accrescent.appstore.publish.v1alpha1.getSelfRequest
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

private const val ANCIENT_DEVICE_DEVICE_ATTRIBUTES_PATH = "ancient-device-device-attributes.txtpb"
private const val PIXEL_9_EMULATOR_DEVICE_ATTRIBUTES_PATH = "pixel-9-emulator-device-attributes.txtpb"

@QuarkusIntegrationTest
class AppServiceImplIT {
    val appService = ApiUtils.getAppServiceStub()

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
                .createPublisher(createPublisherRequest { userId = publisherUserId })

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
        val exception = assertThrows<StatusException> { appService.getAppListing(request) }
        assertEquals(exception.status.code, Status.Code.NOT_FOUND)
    }

    @Test
    fun userRequestsListingForPublishedApp() {
        val request = getAppListingRequest {
            appId = "com.example.valid"
            preferredLanguages.add("en-US")
        }
        val listing = appService.getAppListing(request).listing

        // Assert the listing matches what was published
        assertEquals("com.example.valid", listing.appId)
        assertEquals("en-US", listing.language)
        assertEquals("Example Valid App", listing.name)
        assertEquals("An example valid app", listing.shortDescription)
    }

    @Test
    fun userListsAppListingsWithOvershoot() {
        val request = listAppListingsRequest { skip = Int.MAX_VALUE }
        val response = appService.listAppListings(request)

        // If there are no more listings, nextPageToken should not be present
        assertFalse(response.hasNextPageToken())
    }

    @Test
    fun userRequestsPackageInfoForUnknownApp() {
        val request = getAppPackageInfoRequest { appId = "non.existent.app" }

        // Assert the package info doesn't exist
        val exception = assertThrows<StatusException> { appService.getAppPackageInfo(request) }
        assertEquals(exception.status.code, Status.Code.NOT_FOUND)
    }

    @Test
    fun userRequestsPackageInfoForPublishedApp() {
        val request = getAppPackageInfoRequest { appId = "com.example.valid" }
        val packageInfo = appService.getAppPackageInfo(request).packageInfo

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
        val downloadInfo = appService.getAppDownloadInfo(request).appDownloadInfo

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
        val exception = assertThrows<StatusException> { appService.getAppDownloadInfo(request) }
        assertEquals(exception.status.code, Status.Code.FAILED_PRECONDITION)
    }

    @Test
    fun userRequestsUpdateInfoForPublishedAppWithCompatibleDeviceWhenNoUpdateIsAvailable() {
        val request = getAppUpdateInfoRequest {
            appId = "com.example.valid"
            deviceAttributes = pixel9EmulatorDeviceAttributes
            baseVersionCode = 2
        }
        val response = appService.getAppUpdateInfo(request)

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

        val response = appService.getAppUpdateInfo(request)

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
        val exception = assertThrows<StatusException> { appService.getAppUpdateInfo(request) }
        assertEquals(exception.status.code, Status.Code.FAILED_PRECONDITION)
    }
}
