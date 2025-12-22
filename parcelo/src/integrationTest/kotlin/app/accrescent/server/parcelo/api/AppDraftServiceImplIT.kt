// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.OrganizationServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsRequest
import app.accrescent.server.parcelo.testutil.BearerToken
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.quarkus.test.junit.QuarkusIntegrationTest
import jakarta.json.Json
import org.eclipse.microprofile.config.ConfigProvider
import org.htmlunit.HttpMethod
import org.htmlunit.Page
import org.htmlunit.WebClient
import org.htmlunit.WebRequest
import org.htmlunit.html.HtmlInput
import org.htmlunit.html.HtmlPage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringReader
import java.net.URI

private const val DEFAULT_SERVER_HOST = "localhost"
private const val SERVER_HOST_CONFIG_PROPERTY = "quarkus.http.test-host"
private const val SERVER_PORT_CONFIG_PROPERTY = "quarkus.http.test-port"

private const val ORGANIZATION_APP_DRAFT_LIMIT = 3

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
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            channel.shutdown()
        }

        private fun generateSessionToken(): BearerToken {
            return WebClient().use { webClient ->
                // Get rid of spurious warnings in test output
                webClient.options.isCssEnabled = false

                // Log in to Keycloak
                val loginPage: HtmlPage = webClient
                    .getPage("http://$serverHost:$serverPort/web/session/login")
                val loginForm = loginPage.forms[0]
                loginForm.getInputByName<HtmlInput>("username").type("alice")
                loginForm.getInputByName<HtmlInput>("password").type("alice")
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
    }

    @Test
    fun organizationDraftLimitIsEnforced() {
        val token = generateSessionToken()
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
}
