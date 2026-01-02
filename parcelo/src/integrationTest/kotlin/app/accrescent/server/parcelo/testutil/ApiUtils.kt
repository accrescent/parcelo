// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.testutil

import app.accrescent.appstore.publish.v1alpha1.AppDraftServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.OrganizationServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.PublisherServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.ReviewServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.ReviewerServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.UserServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.createAppDraftListingRequest
import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.createAppDraftReviewRequest
import app.accrescent.appstore.publish.v1alpha1.getAppDraftListingIconUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.getAppDraftUploadInfoRequest
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsRequest
import app.accrescent.appstore.publish.v1alpha1.publishAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.submitAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.updateAppDraftRequest
import app.accrescent.appstore.v1.AppServiceGrpc
import com.google.protobuf.fieldMask
import io.grpc.ManagedChannelBuilder
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
import java.io.File
import java.io.StringReader
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.use

private const val DEFAULT_SERVER_HOST = "localhost"
private const val DEFAULT_SERVER_PORT = 8081
private const val DEFAULT_USER_PASSWORD = "password"
private const val SERVER_HOST_CONFIG_PROPERTY = "quarkus.http.test-host"
private const val SERVER_PORT_CONFIG_PROPERTY = "quarkus.http.test-port"

private const val ENCODED_PNG = "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIAAQMAAADOtka5AAAAA1BMVEUAAACnej3a" +
        "AAAANklEQVR42u3BAQEAAACCIP+vbkhAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8G4IAAAFjdVCk" +
        "AAAAAElFTkSuQmCC"

object ApiUtils {
    private val config = ConfigProvider.getConfig()
    private val serverHost = config
        .getOptionalValue(SERVER_HOST_CONFIG_PROPERTY, String::class.java)
        .orElse(DEFAULT_SERVER_HOST)
    private val serverPort = config
        .getOptionalValue(SERVER_PORT_CONFIG_PROPERTY, Int::class.java)
        .orElse(DEFAULT_SERVER_PORT)
    private val channel = ManagedChannelBuilder
        .forAddress(serverHost, serverPort)
        .usePlaintext()
        .build()

    fun getAppDraftServiceStub(
        token: BearerToken,
    ): AppDraftServiceGrpc.AppDraftServiceBlockingV2Stub =
        AppDraftServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getAppServiceStub(): AppServiceGrpc.AppServiceBlockingV2Stub =
        AppServiceGrpc.newBlockingV2Stub(channel)

    fun getPublisherServiceStub(
        token: BearerToken,
    ): PublisherServiceGrpc.PublisherServiceBlockingV2Stub =
        PublisherServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getReviewServiceStub(token: BearerToken): ReviewServiceGrpc.ReviewServiceBlockingV2Stub =
        ReviewServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getReviewerServiceStub(
        token: BearerToken
    ): ReviewerServiceGrpc.ReviewerServiceBlockingV2Stub =
        ReviewerServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getUserServiceStub(token: BearerToken): UserServiceGrpc.UserServiceBlockingV2Stub =
        UserServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getOrganizationServiceStub(
        token: BearerToken,
    ): OrganizationServiceGrpc.OrganizationServiceBlockingV2Stub =
        OrganizationServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun generateSessionToken(username: String): BearerToken {
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

    fun publishApp(submitter: String, reviewer: String, publisher: String, apkSetName: String) {
        val token = generateSessionToken(submitter)
        val organizationService = getOrganizationServiceStub(token)
        val appDraftService = getAppDraftServiceStub(token)

        val reviewerToken = generateSessionToken(reviewer)
        val reviewService = getReviewServiceStub(reviewerToken)

        val publisherToken = generateSessionToken(publisher)
        val publisherAppDraftService = getAppDraftServiceStub(publisherToken)

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
            name = "Example Valid App"
            shortDescription = "An example valid app"
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
        val apkSetPath = System.getProperty("testdata.apkset.$apkSetName.path")
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

        // Approve the app draft
        val reviewRequest = createAppDraftReviewRequest {
            this.appDraftId = appDraftId
            approved = true
        }
        reviewService.createAppDraftReview(reviewRequest)

        // Publish the app draft
        val publishRequest = publishAppDraftRequest { this.appDraftId = appDraftId }
        publisherAppDraftService.publishAppDraft(publishRequest)
    }
}
