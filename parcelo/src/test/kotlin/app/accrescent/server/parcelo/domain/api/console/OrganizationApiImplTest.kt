// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.api.console

import app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore
import app.accrescent.server.parcelo.adapters.driven.randomsource.DeterministicRandomSource
import app.accrescent.server.parcelo.adapters.driven.timestampsource.FixedTimestampSource
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.domain.IdGenerator
import app.accrescent.server.parcelo.domain.authn.ExternalUserId
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.timestampsource.TimestampSource
import app.accrescent.server.parcelo.domain.ports.driving.console.CallContext
import app.accrescent.server.parcelo.domain.ports.driving.console.GetMyOrganizationResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.Organization
import app.accrescent.server.parcelo.domain.ports.driving.console.OrganizationApi
import app.accrescent.server.parcelo.domain.ports.driving.console.UnauthenticatedError
import arrow.core.None
import arrow.core.Some
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

class OrganizationApiImplTest {
    @Test
    fun `getMyOrganization returns Unauthenticated when not given a session ID`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val organizationApi = makeOrganizationApi(dataStore)

            val response = organizationApi.getMyOrganization(CallContext(None))

            assertEquals(UnauthenticatedError, response.unwrapErr())
        }
    }

    @Test
    fun `getMyOrganization returns Unauthenticated when session does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val organizationApi = makeOrganizationApi(dataStore)

            val response = organizationApi.getMyOrganization(CallContext(Some("session1")))

            assertEquals(UnauthenticatedError, response.unwrapErr())
        }
    }

    @Test
    fun `getMyOrganization returns Unauthenticated when session is expired`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val signInTime = FixedTimestampSource().now()
            val context = signIn(dataStore, timestampSource = FixedTimestampSource(signInTime))
            val organizationApi = makeOrganizationApi(
                dataStore = dataStore,
                timestampSource = FixedTimestampSource(signInTime.plusDays(2)),
            )

            val response = organizationApi.getMyOrganization(context)

            assertEquals(UnauthenticatedError, response.unwrapErr())
        }
    }

    @Test
    fun `getMyOrganization returns user's organization when authenticated`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val context = signIn(dataStore)
            val organizationApi = makeOrganizationApi(dataStore)

            val response = organizationApi.getMyOrganization(context)

            assertEquals(
                GetMyOrganizationResponse(Organization("org_1uMy9o9BqyoxomLjIEbctU")).right(),
                response,
            )
        }
    }

    /**
     * Signs a new user up, creates a login session, and returns the necessary context to make calls
     * as the new user.
     */
    private fun signIn(
        dataStore: DataStore,
        externalUserId: ExternalUserId = ExternalUserId.Github(1),
        timestampSource: TimestampSource = FixedTimestampSource(),
    ): CallContext {
        val sessionApi = SessionApiImpl(
            dataStore = dataStore,
            idGenerator = IdGenerator(DeterministicRandomSource()),
            sessionLifetime = 1.days,
            timestampSource = timestampSource,
        )
        val sessionId = sessionApi.createSession(externalUserId).unwrap().sessionId

        return CallContext(Some(sessionId))
    }

    private fun makeOrganizationApi(
        dataStore: DataStore,
        timestampSource: TimestampSource = FixedTimestampSource(),
    ): OrganizationApi {
        return OrganizationApiImpl(dataStore, timestampSource)
    }
}
