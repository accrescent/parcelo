// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.api.publishing

import app.accrescent.appstore.publish.v1alpha1.createAppDraftRequest
import app.accrescent.appstore.publish.v1alpha1.listMyOrganizationsRequest
import app.accrescent.server.parcelo.testutil.ApiUtils
import io.grpc.Status
import io.grpc.StatusException
import io.quarkus.test.junit.QuarkusIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val ORGANIZATION_APP_DRAFT_LIMIT = 3

@QuarkusIntegrationTest
class AppDraftServiceImplIT {
    @Test
    fun userTriesToCreateAppDraftsBeyondOrgLimit() {
        val token = ApiUtils.generateSessionToken("user1")
        val organizationService = ApiUtils.getOrganizationServiceStub(token)
        val appDraftService = ApiUtils.getAppDraftServiceStub(token)
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
