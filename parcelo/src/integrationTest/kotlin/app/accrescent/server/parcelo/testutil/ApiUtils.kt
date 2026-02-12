// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.testutil

import app.accrescent.console.v1alpha1.AppDraftServiceGrpc
import app.accrescent.console.v1alpha1.AppEditServiceGrpc
import app.accrescent.console.v1alpha1.OrganizationServiceGrpc
import app.accrescent.console.v1alpha1.PublisherServiceGrpc
import app.accrescent.console.v1alpha1.ReviewServiceGrpc
import app.accrescent.console.v1alpha1.ReviewerServiceGrpc
import app.accrescent.console.v1alpha1.UserServiceGrpc
import app.accrescent.console.v1alpha1.createAppDraftListingIconUploadOperationRequest
import app.accrescent.console.v1alpha1.createAppDraftListingRequest
import app.accrescent.console.v1alpha1.createAppDraftRequest
import app.accrescent.console.v1alpha1.createAppDraftReviewRequest
import app.accrescent.console.v1alpha1.createAppDraftUploadOperationRequest
import app.accrescent.console.v1alpha1.getAppDraftRequest
import app.accrescent.console.v1alpha1.listOrganizationsRequest
import app.accrescent.console.v1alpha1.publishAppDraftRequest
import app.accrescent.console.v1alpha1.submitAppDraftRequest
import app.accrescent.console.v1alpha1.updateAppDraftRequest
import com.google.longrunning.GetOperationRequest
import com.google.longrunning.Operation
import com.google.longrunning.OperationsGrpc
import com.google.protobuf.fieldMask
import io.grpc.CallCredentials
import io.grpc.ManagedChannelBuilder
import io.quarkus.oidc.runtime.OidcUtils
import io.restassured.RestAssured.given
import org.awaitility.Awaitility.await
import org.eclipse.microprofile.config.ConfigProvider
import org.htmlunit.HttpMethod
import org.htmlunit.Page
import org.htmlunit.WebClient
import org.htmlunit.WebRequest
import org.htmlunit.html.HtmlInput
import org.htmlunit.html.HtmlPage
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.net.URI
import java.time.Duration
import kotlin.io.encoding.Base64
import app.accrescent.appstore.v1.AppServiceGrpc as StoreAppServiceGrpc
import app.accrescent.console.v1alpha1.AppServiceGrpc as DevAppServiceGrpc

private const val DEFAULT_SERVER_HOST = "localhost"
private const val DEFAULT_SERVER_PORT = 8081
private const val DEFAULT_USER_PASSWORD = "password"
private const val SERVER_HOST_CONFIG_PROPERTY = "quarkus.http.test-host"
private const val SERVER_PORT_CONFIG_PROPERTY = "quarkus.http.test-port"

private const val ENCODED_PNG = "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIAAQMAAADOtka5AAAAA1BMVEUAAACnej3a" +
        "AAAANklEQVR42u3BAQEAAACCIP+vbkhAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8G4IAAAFjdVCk" +
        "AAAAAElFTkSuQmCC"

private const val PUBLISH_TIMEOUT_SECONDS = 60L

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
        credentials: CallCredentials,
    ): AppDraftServiceGrpc.AppDraftServiceBlockingV2Stub =
        AppDraftServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getAppEditServiceStub(
        credentials: CallCredentials,
    ): AppEditServiceGrpc.AppEditServiceBlockingV2Stub =
        AppEditServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getDevAppServiceStub(
        credentials: CallCredentials,
    ): DevAppServiceGrpc.AppServiceBlockingV2Stub =
        DevAppServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getOperationsServiceStub(
        credentials: CallCredentials,
    ): OperationsGrpc.OperationsBlockingV2Stub =
        OperationsGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getPublisherServiceStub(
        credentials: CallCredentials,
    ): PublisherServiceGrpc.PublisherServiceBlockingV2Stub =
        PublisherServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getReviewServiceStub(
        credentials: CallCredentials,
    ): ReviewServiceGrpc.ReviewServiceBlockingV2Stub =
        ReviewServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getReviewerServiceStub(
        credentials: CallCredentials
    ): ReviewerServiceGrpc.ReviewerServiceBlockingV2Stub =
        ReviewerServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getStoreAppServiceStub(): StoreAppServiceGrpc.AppServiceBlockingV2Stub =
        StoreAppServiceGrpc.newBlockingV2Stub(channel)

    fun getUserServiceStub(
        credentials: CallCredentials,
    ): UserServiceGrpc.UserServiceBlockingV2Stub =
        UserServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(credentials)

    fun getOrganizationServiceStub(
        token: CallCredentials,
    ): OrganizationServiceGrpc.OrganizationServiceBlockingV2Stub =
        OrganizationServiceGrpc.newBlockingV2Stub(channel).withCallCredentials(token)

    fun getCredentials(username: String): CallCredentials {
        return WebClient().use { webClient ->
            // Get rid of spurious warnings in test output
            webClient.options.isCssEnabled = false

            // Log in to Keycloak
            val loginPage: HtmlPage = webClient
                .getPage("http://$serverHost:$serverPort/web/account/login")
            val loginForm = loginPage.forms[0]
            loginForm.getInputByName<HtmlInput>("username").type(username)
            loginForm.getInputByName<HtmlInput>("password").type(DEFAULT_USER_PASSWORD)
            loginForm.getButtonByName("login").click<HtmlPage>()

            // Register the user
            val registerRequest = WebRequest(
                URI.create("http://$serverHost:$serverPort/web/account/register").toURL(),
                HttpMethod.PUT,
            )
            webClient.getPage<Page>(registerRequest)

            val cookieValue = webClient.cookieManager.getCookie(OidcUtils.SESSION_COOKIE_NAME).value

            OidcCookieCallCredentials(cookieValue)
        }
    }

    fun publishApp(submitter: String, reviewer: String, publisher: String, apkSetName: String) {
        val token = getCredentials(submitter)
        val organizationService = getOrganizationServiceStub(token)
        val appDraftService = getAppDraftServiceStub(token)
        val operationsService = getOperationsServiceStub(token)

        val reviewerToken = getCredentials(reviewer)
        val reviewService = getReviewServiceStub(reviewerToken)

        val publisherToken = getCredentials(publisher)
        val publisherAppDraftService = getAppDraftServiceStub(publisherToken)

        val organizationId = organizationService
            .listOrganizations(listOrganizationsRequest {})
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
        val uploadIconRequest = createAppDraftListingIconUploadOperationRequest {
            this.appDraftId = appDraftId
            language = "en-US"
        }
        val iconUploadResponse = appDraftService
            .createAppDraftListingIconUploadOperation(uploadIconRequest)
        given()
            .header("Host", "storage.googleapis.com")
            .body(Base64.decode(ENCODED_PNG))
            .put(iconUploadResponse.uploadUrl)
            .then()
            .statusCode(200)

        // Upload the APK set
        val uploadDraftRequest = createAppDraftUploadOperationRequest { this.appDraftId = appDraftId }
        val apkSetUploadUrl = appDraftService
            .createAppDraftUploadOperation(uploadDraftRequest)
            .apkSetUploadUrl
        val apkSetPath = System.getProperty("testdata.apkset.$apkSetName.path")
        given()
            .header("Host", "storage.googleapis.com")
            .body(File(apkSetPath))
            .put(apkSetUploadUrl)
            .then()
            .statusCode(200)

        // Wait for the icon to be processed
        val getIconUploadOpRequest = GetOperationRequest
            .newBuilder()
            .setName(iconUploadResponse.processingOperation.name)
            .build()
        var iconUploadOp = Operation.getDefaultInstance()
        await().until {
            iconUploadOp = operationsService.getOperation(getIconUploadOpRequest)
            iconUploadOp.done
        }
        assertTrue(iconUploadOp.hasResponse())

        // Wait for the package to be processed
        val getAppDraftRequest = getAppDraftRequest { this.appDraftId = appDraftId }
        await().until { appDraftService.getAppDraft(getAppDraftRequest).draft.hasAppPackage() }

        // Submit the app draft
        val submitAppDraftRequest = submitAppDraftRequest { this.appDraftId = appDraftId }
        appDraftService.submitAppDraft(submitAppDraftRequest)

        // Approve the app draft
        val reviewRequest = createAppDraftReviewRequest {
            this.appDraftId = appDraftId
            approved = true
        }
        reviewService.createAppDraftReview(reviewRequest)

        // Publish the app draft
        val publishRequest = publishAppDraftRequest { this.appDraftId = appDraftId }
        val publishResponse = publisherAppDraftService.publishAppDraft(publishRequest)

        // Wait for the app draft to be published
        val getOperationRequest = GetOperationRequest
            .newBuilder()
            .setName(publishResponse.operation.name)
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
}
