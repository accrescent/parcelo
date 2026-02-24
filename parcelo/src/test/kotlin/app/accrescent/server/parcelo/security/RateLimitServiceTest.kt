// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class RateLimitServiceTest {
    @Inject
    lateinit var rateLimitService: RateLimitService

    @Test
    fun allowedWhenUnderLimit() {
        val principal = Principal.User(UUID.randomUUID().toString())

        val result = rateLimitService.tryRequest(principal)

        assertTrue(result)
    }

    @Test
    fun deniedWhenLimitExceeded() {
        val principal = Principal.User(UUID.randomUUID().toString())

        // Exhaust the short-term bucket (10 requests per second in test config)
        repeat(10) {
            assertTrue(rateLimitService.tryRequest(principal))
        }

        val result = rateLimitService.tryRequest(principal)

        assertFalse(result)
    }

    @Test
    fun apiCategoryCheckDeniesWhenCategoryLimitExceeded() {
        val principal = Principal.User(UUID.randomUUID().toString())

        // Exhaust the upload APIs bucket (5 requests per hour in test config)
        repeat(5) {
            assertTrue(rateLimitService.tryRequest(principal, ApiCategory.UPLOAD_APIS))
        }

        // Upload API request should be denied
        val result = rateLimitService.tryRequest(principal, ApiCategory.UPLOAD_APIS)

        assertFalse(result)
    }

    @Test
    fun tokenRefundedOnApiCategoryDenial() {
        val principal = Principal.User(UUID.randomUUID().toString())

        // Exhaust the upload APIs bucket
        repeat(5) {
            assertTrue(rateLimitService.tryRequest(principal, ApiCategory.UPLOAD_APIS))
        }

        // This should be denied by the upload API check, but refund the principal token
        assertFalse(rateLimitService.tryRequest(principal, ApiCategory.UPLOAD_APIS))

        // A non-upload request should still succeed since the token was refunded
        val result = rateLimitService.tryRequest(principal)

        assertTrue(result)
    }

    @Test
    fun differentPrincipalsHaveIndependentBuckets() {
        val principal1 = Principal.User(UUID.randomUUID().toString())
        val principal2 = Principal.User(UUID.randomUUID().toString())

        // Exhaust principal1's short-term bucket
        repeat(10) {
            assertTrue(rateLimitService.tryRequest(principal1))
        }
        assertFalse(rateLimitService.tryRequest(principal1))

        // principal2 should be unaffected
        val result = rateLimitService.tryRequest(principal2)

        assertTrue(result)
    }
}
