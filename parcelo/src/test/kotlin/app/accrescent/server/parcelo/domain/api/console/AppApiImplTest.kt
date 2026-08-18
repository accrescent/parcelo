// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.api.console

import app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore
import app.accrescent.server.parcelo.adapters.driven.randomsource.DeterministicRandomSource
import app.accrescent.server.parcelo.adapters.driven.timestampsource.ConstantTimestampSource
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrap2
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ListingLanguage
import app.accrescent.server.parcelo.domain.ports.driven.timestampsource.TimestampSource
import app.accrescent.server.parcelo.domain.ports.driving.console.AppApi
import app.accrescent.server.parcelo.domain.ports.driving.console.CallContext
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.InsufficientPermissionError
import app.accrescent.server.parcelo.domain.ports.driving.console.UnauthenticatedError
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppRequest
import app.accrescent.server.parcelo.saveExpiredSession
import app.accrescent.server.parcelo.signInNewUser
import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import app.accrescent.server.parcelo.domain.ports.driven.datastore.App as DataApp
import app.accrescent.server.parcelo.domain.ports.driving.console.App as ApiApp

class AppApiImplTest {
    @Test
    fun `getApp returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx -> signInNewUser(tx).bind() }.unwrap2()
            val appApi = makeAppApi(dataStore)

            val response = appApi.getApp(CallContext(Some("session1")), GetAppRequest("app1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `getApp returns app for authorized request for existing app`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val originalApp = makeApp()
            dataStore.runTxWithRetry { tx ->
                signInNewUser(tx).bind()
                tx.apps.saveWithDefaultListing(
                    originalApp,
                    AppListing("appListing1", "app1", ListingLanguage.EN_US)
                ).bind()
            }.unwrap2()
            val appApi = makeAppApi(dataStore)

            val response = appApi.getApp(CallContext(Some("session1")), GetAppRequest("app1"))

            assertEquals(
                GetAppResponse(
                    ApiApp(
                        id = originalApp.id,
                        defaultAppListingId = originalApp.defaultAppListingId,
                        publiclyListed = originalApp.publiclyListed,
                    )
                )
                    .right(),
                response,
            )
        }
    }

    @Test
    fun `updateApp returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx -> signInNewUser(tx).bind() }.unwrap2()
            val appApi = makeAppApi(dataStore)

            val response = appApi.updateApp(CallContext(Some("session1")), UpdateAppRequest("app1", false))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `updateApp returns successfully for authorized request to existing app`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                signInNewUser(tx).bind()
                tx.apps.saveWithDefaultListing(
                    makeApp(),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US)
                ).bind()
            }.unwrap2()
            val appApi = makeAppApi(dataStore)

            val response = appApi.updateApp(CallContext(Some("session1")), UpdateAppRequest("app1", false))

            assertEquals(Unit.right(), response)
        }
    }

    @Test
    fun `updateApp modifies app's publiclyListed attribute when masked`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                signInNewUser(tx).bind()
                tx.apps.saveWithDefaultListing(
                    makeApp(),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US)
                ).bind()
            }.unwrap2()

            val appApi = makeAppApi(dataStore)

            appApi.updateApp(
                CallContext(Some("session1")),
                UpdateAppRequest(appId = "app1", publiclyListed = true),
            ).unwrap()
            val dataStoreApp = dataStore
                .runTxWithRetry { tx -> tx.apps.findById("app1").bind() }
                .unwrap2()
                .unwrap()

            assertTrue(dataStoreApp.publiclyListed)
        }
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedCallTestCases")
    fun `API methods return Unauthenticated for calls without an active session`(
        testCase: UnauthenticatedCallTestCase,
    ) {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                signInNewUser(tx).bind()
                saveExpiredSession(tx).bind()
            }.unwrap2()
            val appApi = makeAppApi(dataStore)

            val response = testCase.call(appApi, testCase.context)

            assertEquals(UnauthenticatedError, response.unwrapErr())
        }
    }

    private fun makeAppApi(
        dataStore: DataStore,
        timestampSource: TimestampSource = ConstantTimestampSource(),
    ): AppApi {
        return AppApiImpl(dataStore, timestampSource)
    }

    private fun makeApp(
        id: String = "app1",
        organizationId: String = "org1",
        defaultAppListingId: String = "appListing1",
        publiclyListed: Boolean = false,
    ) = DataApp(id, organizationId, defaultAppListingId, publiclyListed)

    companion object {
        data class UnauthenticatedCallTestCase(
            val method: String,
            val context: CallContext,
            val call: (AppApi, CallContext) -> Either<*, *>,
        ) {
            override fun toString(): String = "$method, $context"
        }

        @JvmStatic
        private fun unauthenticatedCallTestCases(): List<UnauthenticatedCallTestCase> {
            val calls: List<Pair<String, (AppApi, CallContext) -> Either<*, *>>> = listOf(
                "getApp" to { api, context -> api.getApp(context, GetAppRequest("app1")) },
                "updateApp" to { api, context ->
                    api.updateApp(context, UpdateAppRequest("app1", false))
                },
            )
            val contexts = listOf(CallContext(None), CallContext(Some("expiredSession1")))

            return calls.flatMap { (method, call) ->
                contexts.map { context -> UnauthenticatedCallTestCase(method, context, call) }
            }
        }
    }
}
