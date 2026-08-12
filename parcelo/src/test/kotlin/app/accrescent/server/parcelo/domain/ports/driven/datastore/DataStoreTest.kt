// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore
import app.accrescent.server.parcelo.adapters.driven.randomsource.DeterministicRandomSource
import app.accrescent.server.parcelo.appDraftListing
import app.accrescent.server.parcelo.appPackage
import app.accrescent.server.parcelo.committedExternalBlob
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrap2
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.organization
import app.accrescent.server.parcelo.pendingExternalBlob
import app.accrescent.server.parcelo.unsubmittedAppDraft
import app.accrescent.server.parcelo.user
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

class DataStoreTest {
    @Test
    fun `runTxWithRetry retries up to 5 times on SerializationFailure`() {
        var callCount = 0
        val store = object : DataStore(DeterministicRandomSource()) {
            override fun migrateToHead() = Unit.right()

            override fun <T, E> runInTransaction(
                block: Raise<E>.(Transaction) -> T,
            ): DataStoreResult<Either<E, T>> {
                callCount++
                return DataStoreError.SerializationFailure.left()
            }
        }

        store.runTxWithRetry<_, Nothing> { "anything" }.unwrapErr()

        assertEquals(6, callCount)
    }

    @Test
    fun `runTxWithRetry returns SerializationFailure when retries are exhausted`() {
        val store = object : DataStore(DeterministicRandomSource()) {
            override fun migrateToHead(): DataStoreResult<Unit> = Unit.right()

            override fun <T, E> runInTransaction(
                block: Raise<E>.(Transaction) -> T,
            ): DataStoreResult<Either<E, T>> =
                DataStoreError.SerializationFailure.left()
        }

        val result = store.runTxWithRetry<_, Nothing> { "anything" }

        assertInstanceOf<DataStoreError.SerializationFailure>(result.unwrapErr())
    }

    @Test
    fun `appDrafts requireById returns EntityNotFound if app draft does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.requireById("appDraft1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts requireById returns app draft persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()
            val originalAppDraft = unsubmittedAppDraft()

            dataStore.runTxWithRetry { tx -> tx.appDrafts.save(originalAppDraft).bind() }.unwrap2()
            val foundAppDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.requireById("appDraft1").bind() }
                .unwrap2()

            assertEquals(originalAppDraft, foundAppDraft)
        }
    }

    @Test
    fun `appDrafts requireListingById returns EntityNotFound if listing does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.requireListingById("appDraftListing1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts requireListingById returns app draft listing persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore
                .runTxWithRetry { tx ->
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
                .unwrap2()
            val originalListing = appDraftListing()

            dataStore.runTxWithRetry { tx -> tx.appDrafts.saveListing(originalListing).bind() }.unwrap2()
            val foundListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.requireListingById("appDraftListing1").bind() }
                .unwrap2()

            assertEquals(originalListing, foundListing)
        }
    }

    @Test
    fun `appPackages requireById returns EntityNotFound if app package does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.appPackages.requireById("appPackage1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appPackages requireById returns app package persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val originalPackage = appPackage()

            dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(originalPackage).bind()
            }.unwrap2()
            val foundPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.requireById("appPackage1").bind() }
                .unwrap2()

            assertEquals(originalPackage, foundPackage)
        }
    }

    @Test
    fun `apps requireById returns EntityNotFound if app does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.apps.requireById("app1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `apps requireById returns app persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val originalApp = App("app1", "org1", "appListing1", false)
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()

            val defaultListing = AppListing("appListing1", "app1", ListingLanguage.EN_US)
            dataStore.runTxWithRetry { tx ->
                tx.apps.saveWithDefaultListing(originalApp, defaultListing).bind()
            }
                .unwrap2()
            val foundApp = dataStore
                .runTxWithRetry { tx -> tx.apps.requireById("app1").bind() }
                .unwrap2()

            assertEquals(originalApp, foundApp)
        }
    }

    @Test
    fun `externalBlobs requireById returns EntityNotFound if blob does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById("blob1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs requireById returns blob persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val originalBlob = committedExternalBlob()

            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(originalBlob).bind() }.unwrap2()
            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById(originalBlob.id).bind() }
                .unwrap2()

            assertEquals(originalBlob, foundBlob)
        }
    }

    @Test
    fun `externalBlobs requireCommittedById returns EntityNotFound if blob does not exist`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()

            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireCommittedById("blob1").bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs requireCommittedById returns EntityNotFound if blob is not committed`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val pendingBlob = pendingExternalBlob()

            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(pendingBlob).bind() }.unwrap2()
            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireCommittedById(pendingBlob.id).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs requireCommittedById returns committed blob persisted with save`() {
        InMemoryDataStore(DeterministicRandomSource()).use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val originalBlob = committedExternalBlob()

            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(originalBlob).bind() }.unwrap2()
            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireCommittedById(originalBlob.id).bind() }
                .unwrap2()

            assertEquals(originalBlob, foundBlob)
        }
    }
}
