// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.OrganizationServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.ReviewerServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.UserServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.createAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.createReviewerRequest
import app.accrescent.appstore.publish.v1alpha1.getAppDraftListingIconUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.getAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.getAppDraftUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.getSelfRequest
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsRequest
import app.accrescent.appstore.publish.v1alpha1.submitAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.updateAppDraftRequest
import app.accrescent.server.parcelo.testutil.BearerToken
import com.google.protobuf.fieldMask
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import jakarta.json.Json
import org.awaitility.Awaitility.await
import org.eclipse.microprofile.config.ConfigProvider
import org.htmlunit.HttpMethod
import org.htmlunit.Page
import org.htmlunit.WebClient
import org.htmlunit.WebRequest
import org.htmlunit.html.HtmlInput
import org.htmlunit.html.HtmlPage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.StringReader
import java.net.URI
import kotlin.io.encoding.Base64

private const val DEFAULT_SERVER_HOST = "localhost"
private const val DEFAULT_USER_PASSWORD = "password"
private const val SERVER_HOST_CONFIG_PROPERTY = "quarkus.http.test-host"
private const val SERVER_PORT_CONFIG_PROPERTY = "quarkus.http.test-port"

private const val ORGANIZATION_APP_DRAFT_LIMIT = 3

private const val ENCODED_PNG = "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIAAQMAAADOtka5AAAAA1BMVEUAAACnej3a" +
        "AAAANklEQVR42u3BAQEAAACCIP+vbkhAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8G4IAAAFjdVCk" +
        "AAAAAElFTkSuQmCC"

@QuarkusIntegrationTest
class AppDraftServiceImplIT {
    companion object {
        lateinit var channel: ManagedChannel
        lateinit var serverHost: String
        var serverPort: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val config = ConfigProvider.getConfig()
            serverHost = config
                .getOptionalValue(SERVER_HOST_CONFIG_PROPERTY, String::class.java)
                .orElse(DEFAULT_SERVER_HOST)
            serverPort = config.getValue(SERVER_PORT_CONFIG_PROPERTY, Int::class.java)
            channel = ManagedChannelBuilder
                .forAddress(serverHost, serverPort)
                .usePlaintext()
                .build()

            // Create a reviewer
            val reviewerToken = generateSessionToken("reviewer1")
            val reviewerUserId = getUserServiceStub(reviewerToken).getSelf(getSelfRequest {}).userId
            val reviewerService = getReviewerServiceStub(generateSessionToken("admin1"))
            val createReviewerRequest = createReviewerRequest { userId = reviewerUserId }
            reviewerService.createReviewer(createReviewerRequest)
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            channel.shutdown()
        }

        private fun generateSessionToken(username: String): BearerToken {
            return WebClient().use { webClient ->
                // Get rid of spurious warnings in test output
                webClient.options.isCssEnabled = false

                // Log in to Keycloak
                val loginPage: HtmlPage = webClient
                    .getPage("http://$serverHost:$serverPort/web/session/login")
                val loginForm = loginPage.forms[0]
                loginForm.getInputByName<HtmlInput>("username").type(username)
                loginForm.getInputByName<HtmlInput>("password").type(DEFAULT_USER_PASSWORD)
                loginForm.getButtonByName("login").click<HtmlPage>()

                // Create a session token
                val createApiKeyRequest = WebRequest(
                    URI.create("http://$serverHost:$serverPort/web/session/tokens").toURL(),
                    HttpMethod.POST,
                )
                val responsePage = webClient.getPage<Page>(createApiKeyRequest)

                // Parse the session token from the response
                val sessionToken = Json
                    .createReader(StringReader(responsePage.webResponse.contentAsString))
                    .use { it.readObject().getString("token") }

                BearerToken(sessionToken)
            }
        }

        private fun getOrganizationServiceStub(
            token: BearerToken,
        ): OrganizationServiceGrpc.OrganizationServiceBlockingV2Stub {
            return OrganizationServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)
        }

        private fun getAppDraftServiceStub(
            token: BearerToken,
        ): AppDraftServiceGrpc.AppDraftServiceBlockingV2Stub {
            return AppDraftServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)
        }

        private fun getReviewerServiceStub(
            token: BearerToken
        ): ReviewerServiceGrpc.ReviewerServiceBlockingV2Stub {
            return ReviewerServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)
        }

        private fun getUserServiceStub(
            token: BearerToken
        ): UserServiceGrpc.UserServiceBlockingV2Stub {
            return UserServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)
        }
    }

    @Test
    fun userTriesToCreateAppDraftsBeyondOrgLimit() {
        val token = generateSessionToken("user1")
        val organizationService = getOrganizationServiceStub(token)
        val appDraftService = getAppDraftServiceStub(token)
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
        assertEquals(exception.status.code, Status.Code.RESOURCE_EXHAUSTED)
    }

    @Test
    fun userSubmitsValidAppDraft() {
        val token = generateSessionToken("user2")
        val organizationService = getOrganizationServiceStub(token)
        val appDraftService = getAppDraftServiceStub(token)
        val organizationId = organizationService
            .listMyOrganizations(listMyOrganizationsRequest {})
            .organizationsList[0]
            .id

        val createAppDraftRequest = createAppDraftRequest { this.organizationId = organizationId }
        val appDraftId = appDraftService.createAppDraft(createAppDraftRequest).appDraftId
        val updateAppDraftRequest = updateAppDraftRequest {
            this.appDraftId = appDraftId
            defaultListingLanguage = "en-US"
            updateMask = fieldMask { paths.add("default_listing_language") }
        }
        appDraftService.updateAppDraft(updateAppDraftRequest)
        val createAppDraftListingRequest = createAppDraftListingRequest {
            this.appDraftId = appDraftId
            language = "en-US"
            name = "Accrescent"
            shortDescription = "A novel Android app store"
        }
        appDraftService.createAppDraftListing(createAppDraftListingRequest)

        // Upload the listing icon
        val getAppDraftListingIconUploadInfoRequest = getAppDraftListingIconUploadInfoRequest {
            this.appDraftId = appDraftId
            language = "en-US"
        }
        val iconUploadUrl = appDraftService
            .getAppDraftListingIconUploadInfo(getAppDraftListingIconUploadInfoRequest)
            .uploadUrl
        given().body(Base64.decode(ENCODED_PNG)).put(iconUploadUrl).then().statusCode(200)

        // Upload the APK set
        val getUploadInfoRequest = getAppDraftUploadInfoRequest { this.appDraftId = appDraftId }
        val apkSetUploadUrl = appDraftService.getAppDraftUploadInfo(getUploadInfoRequest).apkSetUploadUrl
        val apkSetPath = System.getProperty("testdata.apkset.valid.path")
        given()
            .header("Host", "storage.googleapis.com")
            .body(File(apkSetPath))
            .put(apkSetUploadUrl)
            .then()
            .statusCode(200)

        // Submit the app draft
        val submitAppDraftRequest = submitAppDraftRequest { this.appDraftId = appDraftId }
        await()
            .ignoreExceptions()
            .until {
                appDraftService.submitAppDraft(submitAppDraftRequest)
                true
            }

        // Verify the app draft is submitted
        val appDraft = appDraftService
            .getAppDraft(getAppDraftRequest { this.appDraftId = appDraftId })
            .draft

        assertTrue(appDraft.submitted)
    }
}
