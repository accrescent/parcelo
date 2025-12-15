// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api

import app.accrescent.appstore.publish.v1alpha1.AppDraftServiceGrpc
import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.server.parcelo.testutil.BearerToken
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.quarkus.test.junit.QuarkusTest
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TEST_ORGANIZATION_APP_DRAFT_LIMIT = 3
private const val TEST_ORGANIZATION_ID = "f2b9003d-c415-47e6-b2aa-bbdd43a2bba1"
private const val TEST_USER_API_KEY = "accu_13RsnqHxMwSMT1ZfOdSoWQ"

@QuarkusTest
class AppDraftServiceImplTest {
    companion object {
        lateinit var channel: ManagedChannel
        lateinit var appDraftService: AppDraftServiceGrpc.AppDraftServiceBlockingV2Stub

        @BeforeAll
        @JvmStatic
        fun setup() {
            val config = ConfigProvider.getConfig()
            val serverPort = config.getValue("quarkus.http.test-port", Int::class.java)
            channel = ManagedChannelBuilder
                .forAddress("localhost", serverPort)
                .usePlaintext()
                .build()
            val stub = AppDraftServiceGrpc
                .newBlockingV2Stub(channel)
                .withCallCredentials(BearerToken(TEST_USER_API_KEY))

            appDraftService = stub
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            channel.shutdown()
        }
    }

    @Test
    fun organizationDraftLimitIsEnforced() {
        val request = createAppDraftRequest { organizationId = TEST_ORGANIZATION_ID }

        repeat(TEST_ORGANIZATION_APP_DRAFT_LIMIT) {
            appDraftService.createAppDraft(request)
        }

        val exception = assertThrows<StatusException> { appDraftService.createAppDraft(request) }
        assertEquals(exception.status.code, Status.Code.RESOURCE_EXHAUSTED)
    }
}
