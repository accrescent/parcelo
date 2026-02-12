// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.v1.DeviceAttributes
import app.accrescent.appstore.v1.getAppDownloadInfoRequest
import app.accrescent.appstore.v1.getAppListingRequest
import app.accrescent.appstore.v1.getAppPackageInfoRequest
import app.accrescent.appstore.v1.getAppUpdateInfoRequest
import app.accrescent.appstore.v1.listAppListingsRequest
import app.accrescent.console.v1alpha1.ErrorReason
import app.accrescent.console.v1alpha1.createAppDraftRequest
import app.accrescent.console.v1alpha1.createAppEditRequest
import app.accrescent.console.v1alpha1.createAppEditUploadOperationRequest
import app.accrescent.console.v1alpha1.createPublisherRequest
import app.accrescent.console.v1alpha1.createReviewerRequest
import app.accrescent.console.v1alpha1.getAppEditRequest
import app.accrescent.console.v1alpha1.getAppRequest
import app.accrescent.console.v1alpha1.getSelfRequest
import app.accrescent.console.v1alpha1.listAppDraftsRequest
import app.accrescent.console.v1alpha1.listAppsRequest
import app.accrescent.console.v1alpha1.listOrganizationsRequest
import app.accrescent.console.v1alpha1.submitAppEditRequest
import app.accrescent.console.v1alpha1.updateAppEditRequest
import app.accrescent.console.v1alpha1.updateAppRequest
import app.accrescent.server.parcelo.testutil.ApiUtils
import app.accrescent.server.parcelo.testutil.errorInfo
import com.google.longrunning.GetOperationRequest
import com.google.longrunning.Operation
import com.google.protobuf.TextFormat
import com.google.protobuf.fieldMask
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.protobuf.StatusProto
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.time.Duration

private const val APP_ACTIVE_EDIT_LIMIT = 3
private const val ORGANIZATION_ACTIVE_APP_DRAFT_LIMIT = 3

private const val ANCIENT_DEVICE_DEVICE_ATTRIBUTES_PATH = "ancient-device-device-attributes.txtpb"
private const val PIXEL_9_EMULATOR_DEVICE_ATTRIBUTES_PATH = "pixel-9-emulator-device-attributes.txtpb"

private const val PUBLISH_TIMEOUT_SECONDS = 60L

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
        val user1Token = ApiUtils.getCredentials("user1")
        val user2Token = ApiUtils.getCredentials("user2")

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Create the reviewer and publisher we use in this class
            val adminToken = ApiUtils.getCredentials("admin1")

            val reviewerUserId = ApiUtils
                .getUserServiceStub(ApiUtils.getCredentials("reviewer1"))
                .getSelf(getSelfRequest {})
                .userId
            ApiUtils
                .getReviewerServiceStub(adminToken)
                .createReviewer(createReviewerRequest {
                    userId = reviewerUserId
                    email = "reviewer1@example.com"
                })

            val publisherUserId = ApiUtils
                .getUserServiceStub(ApiUtils.getCredentials("publisher1"))
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
            .listOrganizations(listOrganizationsRequest {})
            .organizationsList[0]
            .id

        // We should be able to successfully create as many as ORGANIZATION_ACTIVE_APP_DRAFT_LIMIT
        // app drafts without issue
        val request = createAppDraftRequest { this.organizationId = organizationId }
        repeat(ORGANIZATION_ACTIVE_APP_DRAFT_LIMIT) {
            appDraftService.createAppDraft(request)
        }

        // Creating app drafts beyond ORGANIZATION_ACTIVE_APP_DRAFT_LIMIT should fail because of
        // exceeding the organization quota
        val status = assertThrows<StatusException> { appDraftService.createAppDraft(request) }
            .let { StatusProto.fromThrowable(it)!! }
        assertEquals(
            ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED.toString(),
            status.errorInfo()!!.reason,
        )
    }

    @Test
    fun developerRequestsPublishedAppWithAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user2Token)

        val request = getAppRequest { appId = "com.example.valid" }
        val app = appService.getApp(request).app

        // Assert that the app is retrieved successfully
        assertEquals("com.example.valid", app.id)
        assertEquals("en-US", app.defaultListingLanguage)
        assertTrue(app.publiclyListed)
    }

    @Test
    fun developerRequestsPublishedAppWithNoAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user1Token)

        val request = getAppRequest { appId = "com.example.valid" }
        val status = assertThrows<StatusException> { appService.getApp(request) }
            .let { StatusProto.fromThrowable(it)!! }

        // Assert that the developer can't access the app
        assertEquals(
            ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND.toString(),
            status.errorInfo()!!.reason,
        )
    }

    @Test
    fun developerListsPublishedAppsWithAuthorizationForOne() {
        val appService = ApiUtils.getDevAppServiceStub(user2Token)

        val apps = appService.listApps(listAppsRequest {}).appsList

        // Assert that the expected app is included in the response
        assertEquals(1, apps.size)
        assertEquals("com.example.valid", apps[0].id)
        assertEquals("en-US", apps[0].defaultListingLanguage)
        assertTrue(apps[0].publiclyListed)
    }

    @Test
    fun developerListsPublishedAppsWithNoAuthorization() {
        val appService = ApiUtils.getDevAppServiceStub(user1Token)

        val apps = appService.listApps(listAppsRequest {}).appsList

        // Assert that the developer can't access the app
        assertEquals(0, apps.size)
    }

    @Test
    fun developerTriesToPublishAppsBeyondOrgLimit() {
        // We should be able to publish as many as ORGANIZATION_PUBLISHED_APP_LIMIT apps without
        // issue
        ApiUtils.publishApp("user3", "reviewer1", "publisher1", "valid2")

        val status = assertThrows<StatusException> {
            ApiUtils.publishApp("user3", "reviewer1", "publisher1", "valid3")
        }
            .let { StatusProto.fromThrowable(it)!! }

        // Assert that the organization quota is enforced
        assertEquals(
            ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED.toString(),
            status.errorInfo()!!.reason,
        )
    }

    @Test
    fun developerListsAppDraftsWithAuthorizationForOne() {
        val token = ApiUtils.getCredentials("user4")
        val appDraftService = ApiUtils.getAppDraftServiceStub(token)
        val organizationService = ApiUtils.getOrganizationServiceStub(token)
        val organizationId = organizationService
            .listOrganizations(listOrganizationsRequest {})
            .organizationsList[0]
            .id

        val request = createAppDraftRequest { this.organizationId = organizationId }
        appDraftService.createAppDraft(request)

        val appDrafts = appDraftService.listAppDrafts(listAppDraftsRequest {}).appDraftsList

        // Assert that only the newly created app draft is returned
        assertEquals(1, appDrafts.size)
    }

    @Test
    fun developerTriesToCreateAppEditsBeyondAppLimit() {
        val appEditService = ApiUtils.getAppEditServiceStub(user2Token)

        // We should be able to successfully create as many as APP_ACTIVE_EDIT_LIMIT edits without
        // issue
        val request = createAppEditRequest { appId = "com.example.valid" }
        repeat(APP_ACTIVE_EDIT_LIMIT) {
            appEditService.createAppEdit(request)
        }

        // Creating app drafts beyond APP_ACTIVE_EDIT_LIMIT should fail because of exceeding the
        // organization quota
        val status = assertThrows<StatusException> { appEditService.createAppEdit(request) }
            .let { StatusProto.fromThrowable(it)!! }
        assertEquals(
            ErrorReason.ERROR_REASON_RESOURCE_LIMIT_EXCEEDED.toString(),
            status.errorInfo()!!.reason,
        )
    }

    @Test
    fun developerGetsCreatedAppEdit() {
        val token = ApiUtils.getCredentials("user5")
        val appEditService = ApiUtils.getAppEditServiceStub(token)
        ApiUtils.publishApp("user5", "reviewer1", "publisher1", "valid4")

        val createRequest = createAppEditRequest { appId = "com.example.valid4" }
        val appEditId = appEditService.createAppEdit(createRequest).appEditId
        val getRequest = getAppEditRequest { this.appEditId = appEditId }
        val appEdit = appEditService.getAppEdit(getRequest).appEdit

        // Assert that the newly created app edit is returned
        assertEquals("en-US", appEdit.defaultListingLanguage)
        assertTrue(appEdit.hasCreatedAt())
        assertEquals("com.example.valid4", appEdit.appPackage.appId)
        assertEquals(1, appEdit.appPackage.versionCode)
        assertEquals("1.0", appEdit.appPackage.versionName)
        assertEquals(36, appEdit.appPackage.targetSdk)
        assertFalse(appEdit.hasPublishedAt())
    }

    @Test
    fun developerTriesToUpdateAppEditDefaultListingLanguageWithoutMatchingListing() {
        val appEditService = ApiUtils.getAppEditServiceStub(ApiUtils.getCredentials("user6"))
        ApiUtils.publishApp("user6", "reviewer1", "publisher1", "valid5")
        val createRequest = createAppEditRequest { appId = "com.example.valid5" }
        val appEditId = appEditService.createAppEdit(createRequest).appEditId

        val updateRequest = updateAppEditRequest {
            this.appEditId = appEditId
            defaultListingLanguage = "de-DE"
            updateMask = fieldMask { paths.add("default_listing_language") }
        }
        val status = assertThrows<StatusException> { appEditService.updateAppEdit(updateRequest) }
            .let { StatusProto.fromThrowable(it)!! }

        // Assert that the update failed
        assertEquals(
            ErrorReason.ERROR_REASON_CONSTRAINT_VIOLATION.toString(),
            status.errorInfo()!!.reason,
        )
    }

    @Test
    fun developerSubmitsAppEditWithNoChanges() {
        val credentials = ApiUtils.getCredentials("user7")
        val appEditService = ApiUtils.getAppEditServiceStub(credentials)
        val operationsService = ApiUtils.getOperationsServiceStub(credentials)
        ApiUtils.publishApp("user7", "reviewer1", "publisher1", "valid6")
        val createRequest = createAppEditRequest { appId = "com.example.valid6" }
        val appEditId = appEditService.createAppEdit(createRequest).appEditId

        val submitRequest = submitAppEditRequest { this.appEditId = appEditId }
        val response = appEditService.submitAppEdit(submitRequest)

        assertTrue(response.hasOperation())
        val getOperationRequest = GetOperationRequest
            .newBuilder()
            .setName(response.operation.name)
            .build()
        var operation = Operation.getDefaultInstance()
        await()
            .atMost(Duration.ofSeconds(PUBLISH_TIMEOUT_SECONDS))
            .until {
                operation = operationsService.getOperation(getOperationRequest)
                operation.done
            }
        assertTrue(operation.hasResponse())
    }

    @Test
    fun developerSubmitsAppEditWithValidPackageChange() {
        val credentials = ApiUtils.getCredentials("user8")
        val appEditService = ApiUtils.getAppEditServiceStub(credentials)
        val operationsService = ApiUtils.getOperationsServiceStub(credentials)
        ApiUtils.publishApp("user8", "reviewer1", "publisher1", "valid7")
        val createRequest = createAppEditRequest { appId = "com.example.valid7" }
        val appEditId = appEditService.createAppEdit(createRequest).appEditId

        // Upload the APK set
        val uploadInfoRequest = createAppEditUploadOperationRequest { this.appEditId = appEditId }
        val uploadInfoResponse = appEditService.createAppEditUploadOperation(uploadInfoRequest)
        val updateApkSetPath = System.getProperty("testdata.apkset.valid7-update.path")
        given()
            .header("Host", "storage.googleapis.com")
            .body(File(updateApkSetPath))
            .put(uploadInfoResponse.apkSetUploadUrl)
            .then()
            .statusCode(200)

        // Wait for the upload to be processed successfully
        val getUploadOpRequest = GetOperationRequest
            .newBuilder()
            .setName(uploadInfoResponse.processingOperation.name)
            .build()
        var uploadOp = Operation.getDefaultInstance()
        await().until {
            uploadOp = operationsService.getOperation(getUploadOpRequest)
            uploadOp.done
        }
        assertTrue(uploadOp.hasResponse())

        // Submit the app edit
        val submitRequest = submitAppEditRequest { this.appEditId = appEditId }
        val submitResponse = appEditService.submitAppEdit(submitRequest)

        // Wait for the submission to be processed successfully
        assertTrue(submitResponse.hasOperation())
        val getSubmitOpRequest = GetOperationRequest
            .newBuilder()
            .setName(submitResponse.operation.name)
            .build()
        var submitOp = Operation.getDefaultInstance()
        await()
            .atMost(Duration.ofSeconds(PUBLISH_TIMEOUT_SECONDS))
            .until {
                submitOp = operationsService.getOperation(getSubmitOpRequest)
                submitOp.done
            }
        assertTrue(submitOp.hasResponse())
    }

    @Test
    fun developerUnlistsPublishedApp() {
        val credentials = ApiUtils.getCredentials("user9")
        val devAppService = ApiUtils.getDevAppServiceStub(credentials)

        // Publish an app
        ApiUtils.publishApp("user9", "reviewer1", "publisher1", "valid8")

        // Verify the published app shows up in public listings
        val listingsPreUpdate = storeAppService
            .listAppListings(listAppListingsRequest {})
            .listingsList
        assertTrue { listingsPreUpdate.any { it.appId == "com.example.valid8" } }

        // Unlist the app
        val updateRequest = updateAppRequest {
            appId = "com.example.valid8"
            publiclyListed = false
            updateMask = fieldMask { paths.add("publicly_listed") }
        }
        devAppService.updateApp(updateRequest)

        // Verify the app no longer shows up in public listings
        val listingsPostUpdate = storeAppService
            .listAppListings(listAppListingsRequest {})
            .listingsList
        assertTrue { listingsPostUpdate.none { it.appId == "com.example.valid8" } }
    }
}
