// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.OidcProvider
import app.accrescent.server.parcelo.data.User
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

@QuarkusTest
class UserRegistrationServiceTest {
    @Inject
    lateinit var userRegistrationService: UserRegistrationService

    @Test
    @TestTransaction
    fun allowedWhenUnderLimit() {
        repeat(4) { i ->
            createUser("under-limit-$i", OffsetDateTime.now())
        }

        assertTrue(userRegistrationService.registrationsAvailable())
    }

    @Test
    @TestTransaction
    fun deniedWhenAtLimit() {
        repeat(5) { i ->
            createUser("at-limit-$i", OffsetDateTime.now())
        }

        assertFalse(userRegistrationService.registrationsAvailable())
    }

    @Test
    @TestTransaction
    fun allowedWhenOldRegistrationsOutsideWindow() {
        repeat(5) { i ->
            createUser("old-$i", OffsetDateTime.now().minusHours(2))
        }

        assertTrue(userRegistrationService.registrationsAvailable())
    }

    private fun createUser(suffix: String, registeredAt: OffsetDateTime) {
        User(
            id = Identifier.generateNew(IdType.USER),
            oidcProvider = OidcProvider.UNKNOWN,
            oidcIssuer = "http://localhost",
            oidcSubject = "test-$suffix",
            email = "example@example.com",
            reviewer = false,
            publisher = false,
            registeredAt = registeredAt,
        )
            .persist()
    }
}
