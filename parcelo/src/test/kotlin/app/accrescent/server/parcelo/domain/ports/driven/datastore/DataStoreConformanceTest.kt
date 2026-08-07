// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.UNIX_EPOCH
import app.accrescent.server.parcelo.appDraftListing
import app.accrescent.server.parcelo.appPackage
import app.accrescent.server.parcelo.appPackagePermission
import app.accrescent.server.parcelo.committedExternalBlob
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrap2
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.deletedExternalBlob
import app.accrescent.server.parcelo.domain.android.AndroidManifest
import app.accrescent.server.parcelo.domain.android.ApkParseError
import app.accrescent.server.parcelo.domain.android.ApkSetParseError
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.NameAttribute
import app.accrescent.server.parcelo.domain.android.VersionName
import app.accrescent.server.parcelo.organization
import app.accrescent.server.parcelo.pendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.pendingAppDraftUpload
import app.accrescent.server.parcelo.pendingExternalBlob
import app.accrescent.server.parcelo.unsubmittedAppDraft
import app.accrescent.server.parcelo.user
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.left
import arrow.core.raise.context.bind
import arrow.core.raise.context.either
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Conformance test suite for [DataStore] implementations.
 */
abstract class DataStoreConformanceTest {
    /**
     * Runs a lambda with a new [DataStore] instance.
     *
     * Each call creates a new data store instance which shares no state with any other instance.
     * Attempts to call any methods on the [DataStore] outside of [block]'s scope result in
     * undefined behavior.
     *
     * @param block the lambda to run with access to a new [DataStore] instance.
     * @return the return value of [block].
     */
    protected abstract fun <T> withDataStore(block: (DataStore) -> T): T

    /**
     * Convenience method for running a lambda with a new, migrated [DataStore] instance.
     *
     * This method has almost the same behavior as [withDataStore], but runs
     * [DataStore.migrateToHead] before running [block]. If migrating fails, this method will throw.
     *
     * @param block the lambda to run with access to a new, migrated [DataStore] instance.
     * @return the return value of [block].
     * @throws Throwable if [DataStore.migrateToHead] returns an error.
     */
    fun <T> withMigratedDataStore(block: (DataStore) -> T): T {
        return withDataStore { dataStore ->
            dataStore.migrateToHead().unwrap()
            block(dataStore)
        }
    }

    @Test
    fun `migrateToHead returns successfully if migrations are up-to-date`() {
        withDataStore { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.migrateToHead().unwrap()
        }
    }

    @Test
    fun `runTxWithRetry returns the value returned by block`() {
        withMigratedDataStore { dataStore ->

            val result = dataStore.runTxWithRetry<_, Nothing> { "result" }.unwrap2()

            assertEquals("result", result)
        }
    }

    @Test
    fun `runTxWithRetry propagates exception thrown by block`() {
        withMigratedDataStore { dataStore ->
            class CustomException : Exception()

            assertThrows<CustomException> {
                dataStore.runTxWithRetry<_, Nothing> { throw CustomException() }.unwrap2()
            }
        }
    }

    @Test
    fun `runTxWithRetry commits write when block completes successfully`() {
        withMigratedDataStore { dataStore ->
            val originalAppDraft = unsubmittedAppDraft()

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(originalAppDraft).bind()
            }.unwrap2()
            val foundAppDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById(originalAppDraft.id).bind() }
                .unwrap2()

            assertEquals(Some(originalAppDraft), foundAppDraft)
        }
    }

    @Test
    fun `appDrafts countActiveInOrganization returns accurate count`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations
                    .saveWithOwner(
                        organization("org1", ownerUserId = "user1"),
                        user("user1", organizationId = "org1"),
                    )
                    .bind()
                tx.organizations
                    .saveWithOwner(
                        organization("org2", ownerUserId = "user2"),
                        user("user2", organizationId = "org2"),
                    )
                    .bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft3", "org2")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft4")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft5")).bind()
            }.unwrap2()

            val count = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.countActiveInOrganization("org1").bind() }
                .unwrap2()

            assertEquals(4uL, count)
        }
    }

    @Test
    fun `appDrafts deleteById returns EntityNotFound for non-existent app draft`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteById("appDraft1").bind() }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts deleteById makes findById return None`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteById("appDraft1").bind() }
                .unwrap2()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()

            assertTrue(appDraft.isNone())
        }
    }

    @Test
    fun `appDrafts deleteById deletes listings for app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteById("appDraft1").bind() }
                .unwrap2()
            val listingExists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts
                        .listingExistsByIdForAppDraft("appDraftListing1", "appDraft1")
                        .bind()
                }
                .unwrap2()

            assertFalse(listingExists)
        }
    }

    @Test
    fun `appDrafts deleteById deletes pending uploads for app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
            }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteById("appDraft1").bind() }
                .unwrap2()
            val pendingUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertTrue(pendingUpload.isNone())
        }
    }

    @Test
    fun `appDrafts deleteListingById returns EntityNotFound for non-existent app draft listing`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteListingById("appDraftListing1").bind() }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts deleteListingById makes findListingById return None`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appDrafts.deleteListingById("appDraftListing1").bind() }
                .unwrap2()
            val listing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById("appDraftListing1").bind() }
                .unwrap2()

            assertTrue(listing.isNone())
        }
    }

    @Test
    fun `appDrafts deletePendingListingIconUploadByListingId returns EntityNotFound when no pending icon upload exists`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.deletePendingListingIconUploadByListingId("appDraftListing1").bind()
                }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts deletePendingListingIconUploadByListingId makes findPendingListingIconUploadByObjectKey return None`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
            }.unwrap2()

            dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.deletePendingListingIconUploadByListingId("appDraftListing1").bind()
                }
                .unwrap2()
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertTrue(foundUpload.isNone())
        }
    }

    @Test
    fun `appDrafts deletePendingUploadByAppDraftId returns EntityNotFound when no pending upload exists`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.deletePendingUploadByAppDraftId("appDraft1").bind()
                }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts deletePendingUploadByAppDraftId makes findPendingUploadByObjectKey return None`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
            }.unwrap2()

            dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.deletePendingUploadByAppDraftId("appDraft1").bind()
                }
                .unwrap2()
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertTrue(foundUpload.isNone())
        }
    }

    @Test
    fun `appDrafts existsById returns false when no app draft with the given ID exists`() {
        withMigratedDataStore { dataStore ->

            val exists = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.existsById("appDraft1").bind() }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `appDrafts existsById returns true when app draft with given ID exists`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }.unwrap2()

            val exists = dataStore.runTxWithRetry { tx -> tx.appDrafts.existsById("appDraft1").bind() }.unwrap2()

            assertTrue(exists)
        }
    }

    @Test
    fun `appDrafts existsSubmittedForAppId returns false when no app draft exists for given app ID`() {
        withMigratedDataStore { dataStore ->
            val appId = ApplicationId.fromString("com.example.app").unwrap()

            val exists = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.existsSubmittedForAppId(appId).bind() }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `appDrafts existsSubmittedForAppId returns true when submitted app draft exists for given app ID`() {
        withMigratedDataStore { dataStore ->
            val originalAppPackage = appPackage()

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(originalAppPackage).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
            }
                .unwrap2()

            val exists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.existsSubmittedForAppId(originalAppPackage.appId).bind()
                }
                .unwrap2()

            assertTrue(exists)
        }
    }

    @Test
    fun `appDrafts findById returns None when no app draft with the given ID exists`() {
        withMigratedDataStore { dataStore ->

            val foundAppDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()

            assertTrue(foundAppDraft.isNone())
        }
    }

    @Test
    fun `appDrafts findForOrganizationAndUserByQuery returns app drafts from only requested organization`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations
                    .saveWithOwner(
                        organization("org1", ownerUserId = "user1"),
                        user("user1", organizationId = "org1"),
                    )
                    .bind()
                tx.organizations
                    .saveWithOwner(
                        organization("org2", ownerUserId = "user2"),
                        user("user2", organizationId = "org2"),
                    )
                    .bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2", organizationId = "org2")).bind()
            }.unwrap2()

            val appDrafts = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findForOrganizationAndUserByQuery("org1", "user1", 2u, null).bind()
                }
                .unwrap2()

            assertEquals(listOf(unsubmittedAppDraft(id = "appDraft1")), appDrafts)
        }
    }

    @Test
    fun `appDrafts findListingById returns None when no listing with the given ID exists`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById("appDraftListing1").bind() }
                .unwrap2()

            assertTrue(result.isNone())
        }
    }

    @Test
    fun `appDrafts findPendingListingIconUploadByObjectKey returns None when no upload for the given object exists`() {
        withMigratedDataStore { dataStore ->
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertTrue(foundUpload.isNone())
        }
    }

    @Test
    fun `appDrafts findPendingUploadByObjectKey returns None when no upload for the given object exists`() {
        withMigratedDataStore { dataStore ->
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertTrue(foundUpload.isNone())
        }
    }

    @Test
    fun `appDrafts findForOrganizationAndUserByQuery returns only authorized app drafts`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.organizations
                    .saveWithOwner(
                        organization("org2", ownerUserId = "user2"),
                        user("user2", organizationId = "org2"),
                    )
                    .bind()
            }.unwrap2()

            val appDrafts = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findForOrganizationAndUserByQuery("org1", "user2", 1u, null).bind()
                }
                .unwrap2()

            assertEquals(emptyList<AppDraft>(), appDrafts)
        }
    }

    @Test
    fun `appDrafts findForOrganizationAndUserByQuery respects maxResults`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
            }.unwrap2()

            val appDrafts = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findForOrganizationAndUserByQuery("org1", "user1", 1u, null).bind()
                }
                .unwrap2()

            assertEquals(1, appDrafts.size)
        }
    }

    @Test
    fun `appDrafts findForOrganizationAndUserByQuery returns only items after afterAppDraftId`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
            }.unwrap2()

            val appDrafts = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findForOrganizationAndUserByQuery("org1", "user1", 2u, "appDraft1").bind()
                }
                .unwrap2()

            assertEquals(listOf(unsubmittedAppDraft(id = "appDraft2")), appDrafts)
        }
    }

    @Test
    fun `appDrafts findListingsForAppDraftAndUserByQuery returns listings for only requested app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing1", appDraftId = "appDraft1"))
                    .bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing2", appDraftId = "appDraft2"))
                    .bind()
            }.unwrap2()

            val listings = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts
                        .findListingsForAppDraftAndUserByQuery("appDraft1", "user1", 2u, null)
                        .bind()
                }
                .unwrap2()

            assertEquals(
                listOf(appDraftListing(id = "appDraftListing1", appDraftId = "appDraft1")),
                listings,
            )
        }
    }

    @Test
    fun `appDrafts findListingsForAppDraftAndUserByQuery returns only authorized listings`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.organizations
                    .saveWithOwner(
                        organization("org2", ownerUserId = "user2"),
                        user("user2", organizationId = "org2"),
                    )
                    .bind()
            }.unwrap2()

            val listings = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts
                        .findListingsForAppDraftAndUserByQuery("appDraft1", "user2", 1u, null)
                        .bind()
                }
                .unwrap2()

            assertEquals(emptyList<AppDraftListing>(), listings)
        }
    }

    @Test
    fun `appDrafts listingExistsByIdForAppDraft returns false when no listing with the given ID exists`() {
        withMigratedDataStore { dataStore ->
            val exists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.listingExistsByIdForAppDraft("appDraftListing1", "appDraft1").bind()
                }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `appDrafts listingExistsByIdForAppDraft returns false when listing exists with ID for different app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.appDrafts.saveListing(appDraftListing("appDraftListing1", "appDraft1")).bind()
            }.unwrap2()

            val exists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.listingExistsByIdForAppDraft("appDraftListing1", "appDraft2").bind()
                }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `appDrafts listingExistsByIdForAppDraft returns true when listing exists with given ID and app draft ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }.unwrap2()

            val exists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.listingExistsByIdForAppDraft("appDraftListing1", "appDraft1").bind()
                }
                .unwrap2()

            assertTrue(exists)
        }
    }

    @Test
    fun `appDraft listingExistsByIdForAppDraft returns false when listing exists with app draft ID but not listing ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }.unwrap2()

            val exists = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.listingExistsByIdForAppDraft("appDraftListing2", "appDraft1").bind()
                }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `appDraft listingExistsByLanguageForAppDraft returns false when no listing exists`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts
                    .listingExistsByLanguageForAppDraft("appDraft1", ListingLanguage.EN_US)
                    .bind()
            }
                .unwrap2()

            assertFalse(result)
        }
    }

    @Test
    fun `appDraft listingExistsByLanguageForAppDraft returns true when listing exists with app draft ID and language`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts
                    .listingExistsByLanguageForAppDraft("appDraft1", ListingLanguage.EN_US)
                    .bind()
            }
                .unwrap2()

            assertTrue(result)
        }
    }

    @Test
    fun `appDrafts pendingListingIconUploadExistsByListingId returns false when no pending icon upload exists`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.pendingListingIconUploadExistsByListingId("appDraftListing1").bind()
                }
                .unwrap2()

            assertFalse(result)
        }
    }

    @Test
    fun `appDrafts pendingListingIconUploadExistsByListingId returns true when pending icon upload exists`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.pendingListingIconUploadExistsByListingId("appDraftListing1").bind()
                }
                .unwrap2()

            assertTrue(result)
        }
    }

    @Test
    fun `appDrafts pendingUploadExistsByAppDraftId returns false when no pending upload exists`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.pendingUploadExistsByAppDraftId("appDraft1").bind() }
                .unwrap2()

            assertFalse(result)
        }
    }

    @Test
    fun `appDrafts pendingUploadExistsByAppDraftId returns true when pending upload exists`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.pendingUploadExistsByAppDraftId("appDraft1").bind() }
                .unwrap2()

            assertTrue(result)
        }
    }

    @Test
    fun `appDrafts save and findById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalAppDraft = unsubmittedAppDraft()

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(originalAppDraft).bind()
            }
                .unwrap2()
            val foundAppDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById(originalAppDraft.id).bind() }
                .unwrap2()

            assertEquals(Some(originalAppDraft), foundAppDraft)
        }
    }

    @Test
    fun `appDrafts save returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            val appDraft = unsubmittedAppDraft(id = "draft1")
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(appDraft).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.save(appDraft).bind() }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts save returns ForeignKeyViolation when organization does not exist`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.save(unsubmittedAppDraft(id = "draft1", organizationId = "org1")).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts save returns ForeignKeyViolation when app package does not exist`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListing returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.saveListing(appDraftListing()).bind() }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListing returns UniqueConstraintViolation for duplicate (appDraftId, language) pair`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts
                    .saveListing(appDraftListing("appDraftListing1", "appDraft1", ListingLanguage.EN_US))
                    .bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts
                    .saveListing(appDraftListing("appDraftListing2", "appDraft1", ListingLanguage.EN_US))
                    .bind()
            }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListing returns ForeignKeyViolation when app draft does not exist`() {
        withMigratedDataStore { dataStore ->

            val result = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.saveListing(appDraftListing()).bind() }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListing and findListingById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalListing = appDraftListing()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appDrafts.saveListing(originalListing).bind() }
                .unwrap2()
            val foundListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById(originalListing.id).bind() }
                .unwrap2()

            assertEquals(Some(originalListing), foundListing)
        }
    }

    @Test
    fun `appDrafts saveListingIconUpload returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing1", appDraftId = "appDraft1"))
                    .bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing2", appDraftId = "appDraft2"))
                    .bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(
                    pendingAppDraftListingIconUpload(
                        appDraftListingId = "appDraftListing1",
                        objectKey = "object1",
                    )
                ).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveListingIconUpload(
                        pendingAppDraftListingIconUpload(
                            appDraftListingId = "appDraftListing2",
                            objectKey = "object2",
                        )
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListingIconUpload returns UniqueConstraintViolation for duplicate app draft listing ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveListingIconUpload(
                        pendingAppDraftListingIconUpload(id = "adliu2", objectKey = "object2")
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListingIconUpload returns UniqueConstraintViolation for duplicate object key`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing1", appDraftId = "appDraft1"))
                    .bind()
                tx.appDrafts
                    .saveListing(appDraftListing(id = "appDraftListing2", appDraftId = "appDraft2"))
                    .bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts
                    .saveListingIconUpload(pendingAppDraftListingIconUpload(objectKey = "object1"))
                    .bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveListingIconUpload(
                        pendingAppDraftListingIconUpload(
                            id = "adliu2",
                            appDraftListingId = "appDraftListing2",
                            objectKey = "object1",
                        )
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListingIconUpload returns ForeignKeyViolation when app draft listing does not exist`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveListingIconUpload and findPendingListingIconUploadByObjectKey round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalUpload = pendingAppDraftListingIconUpload()

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(originalUpload).bind()
            }.unwrap2()
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertEquals(Some(originalUpload), foundUpload)
        }
    }

    @ParameterizedTest
    @MethodSource("appDraftListingIconUploadProcessingResults")
    fun `appDrafts saveListingIconUpload and findPendingListingIconUploadByObjectKey round-trip processing result`(
        result: AppDraftListingIconUploadProcessingResult,
    ) {
        withMigratedDataStore { dataStore ->
            val originalUpload = pendingAppDraftListingIconUpload(result = Some(result))
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(originalUpload).bind()
            }.unwrap2()

            val foundUpload = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
            }.unwrap2()

            assertEquals(Some(originalUpload), foundUpload)
        }
    }

    @Test
    fun `appDrafts saveUpload returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts
                    .saveUpload(pendingAppDraftUpload(appDraftId = "appDraft1", objectKey = "object1"))
                    .bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveUpload(
                        pendingAppDraftUpload(appDraftId = "appDraft2", objectKey = "object2")
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveUpload returns UniqueConstraintViolation for duplicate app draft ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(pendingAppDraftUpload(appDraftId = "appDraft1")).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveUpload(
                        pendingAppDraftUpload(
                            id = "appDraftUpload2",
                            appDraftId = "appDraft1",
                            objectKey = "object2",
                        )
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveUpload returns UniqueConstraintViolation for duplicate object key`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts
                    .saveUpload(pendingAppDraftUpload(objectKey = "object1"))
                    .bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.saveUpload(
                        pendingAppDraftUpload(
                            id = "appDraftUpload2",
                            appDraftId = "appDraft2",
                            objectKey = "object1",
                        )
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts saveUpload returns ForeignKeyViolation when app draft does not exist`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest
    @MethodSource("appDraftUploadResults")
    fun `appDrafts saveUpload and findPendingUploadByObjectKey round-trip data`(
        result: Option<Either<AppDraftUploadProcessingError, Unit>>,
    ) {
        withMigratedDataStore { dataStore ->
            val originalUpload = pendingAppDraftUpload(result = result)

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(originalUpload).bind()
            }.unwrap2()
            val foundUpload = dataStore
                .runTxWithRetry { tx ->
                    tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
                }
                .unwrap2()

            assertEquals(Some(originalUpload), foundUpload)
        }
    }

    @Test
    fun `appDrafts updateAppPackageId returns EntityNotFound if app draft does not exist`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateAppPackageId("appDraft1", "appPackage1").bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts updateAppPackageId returns ForeignKeyViolation if app package does not exist`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }
                .unwrap2()

            val error = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateAppPackageId("appDraft1", "appPackage1").bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.ForeignKeyViolation, error)
        }
    }

    @Test
    fun `appDrafts updateAppPackageId updates app package ID for existing app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateAppPackageId("appDraft1", "appPackage1").bind()
            }
                .unwrap2()
            val foundAppPackageId = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.requireById("appDraft1").bind() }
                .unwrap2()
                .optionalAppPackageId

            assertEquals(Some("appPackage1"), foundAppPackageId)
        }
    }

    @Test
    fun `appDrafts updateDefaultListing returns ForeignKeyViolation if listing does not exist`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateDefaultListing returns ForeignKeyViolation if listing belongs to different app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft(id = "appDraft2")).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateDefaultListing("appDraft2", Some("appDraftListing1")).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateDefaultListing returns EntityNotFound for non-existent app draft`() {
        withMigratedDataStore { dataStore ->

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateDefaultListing updates defaultAppDraftListingId for existing app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap2()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()
                .unwrap()

            assertEquals(Some("appDraftListing1"), appDraft.optionalDefaultAppDraftListingId)
        }
    }

    @Test
    fun `appDrafts updateDefaultListing unsets defaultAppDraftListingId when given None`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateDefaultListing("appDraft1", None).bind()
            }
                .unwrap2()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()
                .unwrap()

            assertEquals(None, appDraft.optionalDefaultAppDraftListingId)
        }
    }

    @Test
    fun `appDrafts updateListing returns EntityNotFound for non-existent app draft listing`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateListing("appDraftListing1", null, null).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateListing leaves null fields unchanged`() {
        withMigratedDataStore { dataStore ->
            val originalListing = appDraftListing()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(originalListing).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateListing("appDraftListing1", null, null).bind()
            }
                .unwrap2()
            val foundListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById(originalListing.id).bind() }
                .unwrap2()

            assertEquals(Some(originalListing), foundListing)
        }
    }

    @Test
    fun `appDrafts updateListing updates non-null fields`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts
                    .updateListing("appDraftListing1", "Updated App Name", "Updated App Description")
                    .bind()
            }
                .unwrap2()
            val foundListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById("appDraftListing1").bind() }
                .unwrap2()
                .unwrap()

            assertEquals("Updated App Name", foundListing.name)
            assertEquals("Updated App Description", foundListing.shortDescription)
        }
    }

    @Test
    fun `appDrafts updatePendingListingIconUploadResult returns EntityNotFound for non-existent upload`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingListingIconUploadResult(
                    "adliu1",
                    AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted,
                ).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts updatePendingListingIconUploadResult updates result for existing pending upload`() {
        withMigratedDataStore { dataStore ->
            val originalUpload = pendingAppDraftListingIconUpload()
            dataStore
                .runTxWithRetry { tx ->
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveListingIconUpload(originalUpload).bind()
                }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingListingIconUploadResult(
                    "adliu1",
                    AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted,
                ).bind()
            }
                .unwrap2()
            val foundUpload = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
            }
                .unwrap2()
                .unwrap()

            assertEquals(
                Some(AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted),
                foundUpload.result,
            )
        }
    }

    @ParameterizedTest
    @MethodSource("appDraftListingIconUploadProcessingResults")
    fun `appDrafts updatePendingListingIconUploadResult and findPendingListingIconUploadByObjectKey round-trip processing result`(
        result: AppDraftListingIconUploadProcessingResult,
    ) {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
            }.unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingListingIconUploadResult("adliu1", result).bind()
            }.unwrap2()
            val foundUpload = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.findPendingListingIconUploadByObjectKey("object1").bind()
            }.unwrap2()
                .unwrap()

            assertEquals(Some(result), foundUpload.result)
        }
    }

    @Test
    fun `appDrafts updatePendingUploadResult returns EntityNotFound for non-existent upload`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingUploadResult(
                    "upload1",
                    AppDraftUploadProcessingError.AppDraftSubmitted.left()
                ).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `appDrafts updatePendingUploadResult updates result for existing pending upload`() {
        withMigratedDataStore { dataStore ->
            val originalUpload = pendingAppDraftUpload()
            dataStore
                .runTxWithRetry { tx ->
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveUpload(originalUpload).bind()
                }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingUploadResult(
                    "appDraftUpload1",
                    AppDraftUploadProcessingError.AppDraftSubmitted.left(),
                ).bind()
            }
                .unwrap2()
            val foundUpload = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
            }
                .unwrap2()
                .unwrap()

            assertEquals(Some(AppDraftUploadProcessingError.AppDraftSubmitted.left()), foundUpload.result)
        }
    }

    @ParameterizedTest
    @MethodSource("appDraftUploadProcessingResults")
    fun `appDrafts updatePendingUploadResult and findPendingUploadByObjectKey round-trip processing result`(
        result: Either<AppDraftUploadProcessingError, Unit>,
    ) {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.externalBlobs.save(pendingExternalBlob()).bind()
                tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
            }.unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updatePendingUploadResult("appDraftUpload1", result).bind()
            }.unwrap2()
            val foundUpload = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.findPendingUploadByObjectKey("object1").bind()
            }.unwrap2()
                .unwrap()

            assertEquals(Some(result), foundUpload.result)
        }
    }

    @Test
    fun `appDrafts updateSubmitTime returns EntityNotFound for non-existent app draft`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateSubmitTime updates submitTime for existing app draft`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
            }
                .unwrap2()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()
                .unwrap()

            assertInstanceOf<AppDraft.Submitted>(appDraft)
            assertEquals(UNIX_EPOCH, appDraft.submitTime)
        }
    }

    @Test
    fun `appDrafts updateSubmitTime returns CheckConstraintViolation if app draft does not have package`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", Some("appDraftListing1")).bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appDrafts updateSubmitTime returns CheckConstraintViolation if app draft does not have default listing`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
            }
                .unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages deleteById returns EntityNotFound for non-existent package`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.appPackages.deleteById("appPackage1").bind() }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages deleteById returns ForeignKeyViolation when an app draft still references the package`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
            }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appPackages.deleteById("appPackage1").bind() }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages deleteById makes findById return None`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx ->
                    tx.externalBlobs.save(committedExternalBlob()).bind()
                    tx.appPackages.save(appPackage()).bind()
                }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.appPackages.deleteById("appPackage1").bind() }
                .unwrap2()
            val appPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findById("appPackage1").bind() }
                .unwrap2()

            assertTrue(appPackage.isNone())
        }
    }

    @Test
    fun `appPackages findByAppDraftId returns None when no package exists for app draft`() {
        withMigratedDataStore { dataStore ->
            val appPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findByAppDraftId("appDraft1").bind() }
                .unwrap2()

            assertTrue(appPackage.isNone())
        }
    }

    @Test
    fun `appPackages findById returns None when no package with the given ID exists`() {
        withMigratedDataStore { dataStore ->
            val appPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findById("appPackage1").bind() }
                .unwrap2()

            assertTrue(appPackage.isNone())
        }
    }

    @Test
    fun `appPackages save returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx ->
                    tx.externalBlobs.save(committedExternalBlob()).bind()
                    tx.appPackages.save(appPackage()).bind()
                }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.appPackages.save(appPackage()).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages save returns ForeignKeyViolation when external blob is deleted`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(deletedExternalBlob()).bind() }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appPackages.save(appPackage()).bind() }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages save returns ForeignKeyViolation when external blob is pending`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(pendingExternalBlob()).bind() }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx -> tx.appPackages.save(appPackage()).bind() }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `appPackages save and findByAppDraftId round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalAppPackage = appPackage()

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(originalAppPackage).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
            }
                .unwrap2()
            val foundAppPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findByAppDraftId("appDraft1").bind() }
                .unwrap2()

            assertEquals(Some(originalAppPackage), foundAppPackage)
        }
    }

    @Test
    fun `appPackages save and findById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalAppPackage = appPackage()

            dataStore
                .runTxWithRetry { tx ->
                    tx.externalBlobs.save(committedExternalBlob()).bind()
                    tx.appPackages.save(originalAppPackage).bind()
                }
                .unwrap2()
            val foundAppPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findById("appPackage1").bind() }
                .unwrap2()

            assertEquals(Some(originalAppPackage), foundAppPackage)
        }
    }

    @Test
    fun `appPackages savePermission returns UniqueConstraintViolation for permission with duplicate ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appPackages.savePermission(appPackagePermission()).bind()
            }
                .unwrap2()

            val perm2 = appPackagePermission(
                name = NameAttribute.fromString("android.permission.RECEIVE_BOOT_COMPLETED").unwrap(),
            )
            val error = dataStore
                .runTxWithRetry { tx -> tx.appPackages.savePermission(perm2).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.UniqueConstraintViolation, error)
        }
    }

    @Test
    fun `appPackages savePermission returns UniqueConstraintViolation for duplicate (appPackageId, name) pair`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appPackages.savePermission(appPackagePermission()).bind()
            }
                .unwrap2()

            val error = dataStore.runTxWithRetry { tx ->
                tx.appPackages.savePermission(appPackagePermission(id = "perm2")).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.UniqueConstraintViolation, error)
        }
    }

    @Test
    fun `appPackages savePermission returns ForeignKeyViolation when app package does not exist`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore
                .runTxWithRetry { tx -> tx.appPackages.savePermission(appPackagePermission()).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.ForeignKeyViolation, error)
        }
    }

    @Test
    fun `apps countInOrganization returns accurate count`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations
                    .saveWithOwner(
                        organization("org1", ownerUserId = "user1"),
                        user("user1", organizationId = "org1"),
                    )
                    .bind()
                tx.organizations
                    .saveWithOwner(
                        organization("org2", ownerUserId = "user2"),
                        user("user2", organizationId = "org2"),
                    )
                    .bind()
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US),
                ).bind()
                tx.apps.saveWithDefaultListing(
                    App("app2", "org1", "appListing2", false),
                    AppListing("appListing2", "app2", ListingLanguage.EN_US),
                ).bind()
                tx.apps.saveWithDefaultListing(
                    App("app3", "org2", "appListing3", false),
                    AppListing("appListing3", "app3", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()

            val count = dataStore
                .runTxWithRetry { tx -> tx.apps.countInOrganization("org1").bind() }
                .unwrap2()

            assertEquals(2uL, count)
        }
    }

    @Test
    fun `apps existsById returns false when no app with the given ID exists`() {
        withMigratedDataStore { dataStore ->

            val exists = dataStore
                .runTxWithRetry { tx -> tx.apps.existsById("app1").bind() }
                .unwrap2()

            assertFalse(exists)
        }
    }

    @Test
    fun `apps existsById returns true when app with given ID exists`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()

            val exists = dataStore
                .runTxWithRetry { tx -> tx.apps.existsById("app1").bind() }
                .unwrap2()

            assertTrue(exists)
        }
    }

    @Test
    fun `apps findById returns None when no app with the given ID exists`() {
        withMigratedDataStore { dataStore ->

            val foundApp = dataStore
                .runTxWithRetry { tx -> tx.apps.findById("app1").bind() }
                .unwrap2()

            assertTrue(foundApp.isNone())
        }
    }

    @Test
    fun `apps saveWithDefaultListing and findById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalApp = App(
                id = "app1",
                organizationId = "org1",
                defaultAppListingId = "appListing1",
                publiclyListed = false,
            )

            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(
                    originalApp,
                    AppListing("appListing1", "app1", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()
            val foundApp = dataStore
                .runTxWithRetry { tx -> tx.apps.findById(originalApp.id).bind() }
                .unwrap2()
            dataStore
                .runTxWithRetry { tx -> tx.apps.updatePubliclyListed("app1", true).bind() }
                .unwrap2()

            assertEquals(Some(originalApp), foundApp)
        }
    }

    @Test
    fun `apps saveWithDefaultListing returns UniqueConstraintViolation for duplicate app ID`() {
        withMigratedDataStore { dataStore ->
            val app = App("app1", "org1", "appListing1", false)
            val listing = AppListing("appListing1", "app1", ListingLanguage.EN_US)
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(app, listing).bind()
            }.unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.apps.saveWithDefaultListing(
                        app.copy(defaultAppListingId = "appListing2"),
                        listing.copy(id = "appListing2"),
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `apps saveWithDefaultListing returns ForeignKeyViolation when organization does not exist`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.apps.saveWithDefaultListing(
                        App("app1", "nonexistent-org", "appListing1", false),
                        AppListing("appListing1", "app1", ListingLanguage.EN_US),
                    ).bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `apps saveWithDefaultListing returns ForeignKeyViolation when app default listing ID does not match listing ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.apps.saveWithDefaultListing(
                    App("app2", "org1", "appListing1", false),
                    AppListing("appListing2", "app2", ListingLanguage.EN_US),
                ).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `apps saveWithDefaultListing returns ForeignKeyViolation when listing app ID does not match app ID`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(
                    App("app2", "org1", "appListing2", false),
                    AppListing("appListing2", "app2", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()

            val result = dataStore.runTxWithRetry { tx ->
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app2", ListingLanguage.EN_US),
                ).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `apps updatePubliclyListed returns EntityNotFound for non-existent app`() {
        withMigratedDataStore { dataStore ->

            val result = dataStore.runTxWithRetry { tx ->
                tx.apps.updatePubliclyListed("app1", true).bind()
            }
                .unwrap()

            assertEquals(DataStoreError.EntityNotFound, result.unwrapErr())
        }
    }

    @Test
    fun `apps updatePubliclyListed updates publiclyListed for existing app`() {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                tx.organizations.saveWithOwner(organization(), user()).bind()
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app1", ListingLanguage.EN_US),
                ).bind()
            }.unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.apps.updatePubliclyListed("app1", true).bind() }
                .unwrap2()
            val updatedApp = dataStore
                .runTxWithRetry { tx -> tx.apps.findById("app1").bind() }
                .unwrap2()
                .unwrap()

            assertEquals(true, updatedApp.publiclyListed)
        }
    }

    @ParameterizedTest
    @MethodSource("authzHasPermissionReturnsTrueWithMinimalRelationshipsTestCases")
    fun `authz hasPermission returns true with minimal relationships`(
        testCase: AuthzHasPermissionReturnsTrueWithMinimalRelationships,
    ) {
        withMigratedDataStore { dataStore ->
            dataStore.runTxWithRetry { tx ->
                testCase.saveRelationships(tx).bind()

                val result = tx.authz.hasPermission(testCase.hasPermissionRequest).bind()

                assertTrue(result)
            }.unwrap2()
        }
    }

    @Test
    fun `externalBlobs commitPending returns EntityNotFound if blob with given ID does not exist`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.commitPending("blob1", ExternalBlob.LocalBlobVersion(1)).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs commitPending returns EntityNotFound if blob exists but is not pending`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(committedExternalBlob()).bind() }
                .unwrap2()

            val error = dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.commitPending("blob1", ExternalBlob.LocalBlobVersion(1)).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs commitPending returns EntityNotFound if pending blob exists but has different service`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(pendingExternalBlob()).bind() }
                .unwrap2()

            val error = dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.commitPending("blob1", ExternalBlob.GcsBlobVersion(1, 1)).bind()
            }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs commitPending commits pending blob`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(pendingExternalBlob()).bind() }
                .unwrap2()

            dataStore.runTxWithRetry { tx ->
                tx.externalBlobs.commitPending("blob1", ExternalBlob.LocalBlobVersion(1)).bind()
            }
                .unwrap2()
            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById("blob1").bind() }
                .unwrap2()

            assertEquals(
                ExternalBlob.Status.Committed(ExternalBlob.LocalBlobVersion(1)),
                foundBlob.status,
            )
        }
    }

    @Test
    fun `externalBlobs findById returns None when no blob with the given ID exists`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.findById("blob1").bind() }
                .unwrap2()

            assertTrue(result.isNone())
        }
    }

    @Test
    fun `externalBlobs markDeleted returns EntityNotFound if blob does not exist`() {
        withMigratedDataStore { dataStore ->
            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.markDeleted("blob1", UNIX_EPOCH).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.EntityNotFound, error)
        }
    }

    @Test
    fun `externalBlobs markDeleted marks existing blob as deleted`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(committedExternalBlob()).bind() }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.markDeleted("blob1", UNIX_EPOCH).bind() }
                .unwrap2()
            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById("blob1").bind() }
                .unwrap2()

            assertEquals(
                ExternalBlob.Status.Deleted(Some(ExternalBlob.LocalBlobVersion(1)), UNIX_EPOCH),
                foundBlob.status,
            )
        }
    }

    @Test
    fun `externalBlobs markDeleted marks pending blob as deleted without a version`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(pendingExternalBlob()).bind() }
                .unwrap2()

            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.markDeleted("blob1", UNIX_EPOCH).bind() }
                .unwrap2()
            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById("blob1").bind() }
                .unwrap2()

            assertEquals(ExternalBlob.Status.Deleted(None, UNIX_EPOCH), foundBlob.status)
        }
    }

    @Test
    fun `externalBlobs save returns UniqueConstraintViolation for duplicate ID`() {
        withMigratedDataStore { dataStore ->
            val blob1 = committedExternalBlob(bucketName = "bucket1", objectKey = "object1")
            val blob2 = committedExternalBlob(bucketName = "bucket2", objectKey = "bucket2")
            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(blob1).bind() }.unwrap2()

            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(blob2).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.UniqueConstraintViolation, error)
        }
    }

    @Test
    fun `externalBlobs save returns UniqueConstraintViolation for duplicate (service, bucketName, objectKey)`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(committedExternalBlob("blob1")).bind() }
                .unwrap2()

            val error = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(committedExternalBlob("blob2")).bind() }
                .unwrap()
                .unwrapErr()

            assertEquals(DataStoreError.UniqueConstraintViolation, error)
        }
    }

    @Test
    fun `externalBlobs save returns success for duplicate (bucketName, objectKey) with different service`() {
        withMigratedDataStore { dataStore ->
            val blob1 = ExternalBlob.Local(
                id = "blob1",
                createTime = UNIX_EPOCH,
                bucketName = "bucket1",
                objectKey = "object1",
                status = ExternalBlob.Status.Committed(ExternalBlob.LocalBlobVersion(1)),
            )
            val blob2 = ExternalBlob.Gcs(
                id = "blob2",
                createTime = UNIX_EPOCH,
                bucketName = "bucket1",
                objectKey = "object1",
                status = ExternalBlob.Status.Committed(ExternalBlob.GcsBlobVersion(1, 1)),
            )
            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(blob1).bind() }.unwrap2()

            dataStore.runTxWithRetry { tx -> tx.externalBlobs.save(blob2).bind() }.unwrap2()
        }
    }

    @Test
    fun `externalBlobs save and findById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalBlob = committedExternalBlob()
            dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.save(originalBlob).bind() }
                .unwrap2()

            val foundBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.findById(originalBlob.id).bind() }
                .unwrap2()

            assertEquals(Some(originalBlob), foundBlob)
        }
    }

    @Test
    fun `organizations findById returns None when no org with the given ID exists`() {
        withMigratedDataStore { dataStore ->
            val foundOrg = dataStore
                .runTxWithRetry { tx -> tx.organizations.findById("org1").bind() }
                .unwrap2()

            assertTrue(foundOrg.isNone())
        }
    }

    @Test
    fun `organizations saveWithOwner and findById round-trip data`() {
        withMigratedDataStore { dataStore ->
            val originalOrg = organization()

            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(originalOrg, user()).bind() }
                .unwrap2()
            val foundOrg = dataStore
                .runTxWithRetry { tx -> tx.organizations.findById(originalOrg.id).bind() }
                .unwrap2()

            assertEquals(Some(originalOrg), foundOrg)
        }
    }

    @Test
    fun `organizations saveWithOwner returns UniqueConstraintViolation for duplicate organization ID`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.organizations
                        .saveWithOwner(
                            organization(ownerUserId = "user2"),
                            user("user2", organizationId = "org1"),
                        )
                        .bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `organizations saveWithOwner returns UniqueConstraintViolation for duplicate user ID`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()

            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.organizations
                        .saveWithOwner(
                            organization("org2", ownerUserId = "user1"),
                            user("user1", organizationId = "org2"),
                        )
                        .bind()
                }
                .unwrap()

            assertEquals(DataStoreError.UniqueConstraintViolation, result.unwrapErr())
        }
    }

    @Test
    fun `organizations saveWithOwner returns ForeignKeyViolation when owner user ID does not match owner`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.organizations
                        .saveWithOwner(organization(ownerUserId = "user2"), user("user1"))
                        .bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @Test
    fun `organizations saveWithOwner returns ForeignKeyViolation when owner organization ID does not match organization`() {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx ->
                    tx.organizations
                        .saveWithOwner(
                            organization("org1"),
                            user("user1", organizationId = "org2"),
                        )
                        .bind()
                }
                .unwrap()

            assertEquals(DataStoreError.ForeignKeyViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest(name = "{0} save returns CheckConstraintViolation for {1}")
    @MethodSource("textColumnConstraintTestCases")
    fun `save returns CheckConstraintViolation for invalid text column`(
        case: TextColumnConstraintTestCase,
        invalidInput: InvalidCanonicalText,
    ) {
        withMigratedDataStore { dataStore ->
            val result = dataStore
                .runTxWithRetry { tx -> case.save(tx, invalidInput.value).bind() }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest(name = "{0} save returns CheckConstraintViolation for an ID longer than 64 characters")
    @MethodSource("idColumnConstraintTestCases")
    fun `save returns CheckConstraintViolation for ID longer than 64 characters`(
        case: TextColumnConstraintTestCase,
    ) {
        withMigratedDataStore { dataStore ->
            val tooLongId = "a".repeat(65)

            val result = dataStore
                .runTxWithRetry { tx -> case.save(tx, tooLongId).bind() }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest(name = "{0} save returns CheckConstraintViolation for an ID with a disallowed character")
    @MethodSource("idColumnConstraintTestCases")
    fun `save returns CheckConstraintViolation for ID with disallowed character`(
        case: TextColumnConstraintTestCase,
    ) {
        withMigratedDataStore { dataStore ->
            // IDs are restricted to ASCII letters, digits, and underscores. This is otherwise valid
            // canonical text whose only violation is the disallowed hyphen.
            val disallowedId = "id-1"

            val result = dataStore
                .runTxWithRetry { tx -> case.save(tx, disallowedId).bind() }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest(name = "{0} save returns CheckConstraintViolation for text longer than its maximum length")
    @MethodSource("textLengthConstraintTestCases")
    fun `save returns CheckConstraintViolation for text longer than maximum length`(
        case: TextLengthConstraintTestCase,
    ) {
        withMigratedDataStore { dataStore ->
            val tooLongText = "a".repeat(case.maxCodePoints + 1)

            val result = dataStore
                .runTxWithRetry { tx -> case.save(tx, tooLongText).bind() }
                .unwrap()

            assertEquals(DataStoreError.CheckConstraintViolation, result.unwrapErr())
        }
    }

    @ParameterizedTest(name = "{0} save returns success for text at its maximum length")
    @MethodSource("textLengthConstraintTestCases")
    fun `save returns success for text at maximum length`(
        case: TextLengthConstraintTestCase,
    ) {
        withMigratedDataStore { dataStore ->
            val maxLengthText = "a".repeat(case.maxCodePoints)

            val result = dataStore
                .runTxWithRetry { tx -> case.save(tx, maxLengthText).bind() }
                .unwrap()

            assertEquals(Unit.right(), result)
        }
    }

    @Test
    fun `runTxWithRetry isolation prevents G2 anomaly`() {
        withMigratedDataStore { dataStore ->
            dataStore
                .runTxWithRetry { tx -> tx.organizations.saveWithOwner(organization(), user()).bind() }
                .unwrap2()

            // Classic predicate-based write skew. Both transactions read the same
            // predicate ("count of active drafts in org1") and observe zero, then each
            // inserts a row satisfying that predicate. Under PL-3, the resulting
            // anti-dependency cycle must be broken, so exactly one insert can take
            // effect. Under snapshot isolation, both reads see the original snapshot
            // and both inserts can commit.
            //
            // Latches (not a CyclicBarrier) are used so that the synchronization is
            // idempotent across runTxWithRetry retries: under a serializable
            // implementation one attempt aborts with a SerializationFailure and is
            // re-executed, and a re-entry into the block must not block on a
            // synchronization primitive that the other thread has already passed.
            val t1Read = CountDownLatch(1)
            val t2Read = CountDownLatch(1)

            fun runTransaction(
                draftId: String,
                thisRead: CountDownLatch,
                otherRead: CountDownLatch,
            ) {
                dataStore.runTxWithRetry { tx ->
                    val count = tx.appDrafts.countActiveInOrganization("org1").bind()
                    thisRead.countDown()
                    otherRead.await(1, TimeUnit.SECONDS)
                    if (count == 0uL) {
                        tx.appDrafts.save(unsubmittedAppDraft(id = draftId)).bind()
                    }
                }
                    .unwrap2()
            }

            val t1 = Thread { runTransaction("draft1", t1Read, t2Read) }
            val t2 = Thread { runTransaction("draft2", t2Read, t1Read) }
            t1.start()
            t2.start()
            t1.join(30_000)
            t2.join(30_000)
            assertFalse(t1.isAlive, "Thread 1 timed out")
            assertFalse(t2.isAlive, "Thread 2 timed out")

            val count = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.countActiveInOrganization("org1").bind() }
                .unwrap2()

            assertEquals(1uL, count)
        }
    }

    companion object {
        sealed class InvalidCanonicalText(val value: String) {
            data object Empty : InvalidCanonicalText("") {
                override fun toString() = "an empty string"
            }

            // U+FDD0 is a noncharacter according to the Unicode standard
            // (https://www.unicode.org/versions/Unicode17.0.0/core-spec/chapter-23/#G19653), so we
            // can permanently rely on it being unassigned
            data object ContainsUnassignedChar : InvalidCanonicalText("\ufdd0") {
                override fun toString() = "a string with an unassigned Unicode character"
            }

            // U+212B is an explicit example of non-NFC source text given in the Unicode standard
            // (https://www.unicode.org/reports/tr15/tr15-57.html#Canon_Compat_Equivalence), so we
            // can permanently rely on it being non-NFC
            data object NonNfc : InvalidCanonicalText("\u212b") {
                override fun toString() = "non-NFC text"
            }
        }

        class TextColumnConstraintTestCase(
            val column: String,
            val save: (DataStore.Transaction, String) -> DataStoreResult<Unit>,
        ) {
            override fun toString(): String = column
        }

        class TextLengthConstraintTestCase(
            val column: String,
            val maxCodePoints: Int,
            val save: (DataStore.Transaction, String) -> DataStoreResult<Unit>,
        ) {
            override fun toString(): String = column
        }

        @JvmStatic
        private fun idColumnConstraintTestCases(): List<TextColumnConstraintTestCase> = listOf(
            TextColumnConstraintTestCase("apps.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    // app.id and the listing's appId must match, so both carry the invalid value
                    tx.apps.saveWithDefaultListing(
                        App(invalid, "org1", "appListing1", false),
                        AppListing("appListing1", invalid, ListingLanguage.EN_US),
                    ).bind()
                }
            },
            TextColumnConstraintTestCase("appDrafts.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft(id = invalid)).bind()
                }
            },
            TextColumnConstraintTestCase("appDraftListings.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing(id = invalid)).bind()
                }
            },
            TextColumnConstraintTestCase("appListings.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.apps.saveWithDefaultListing(
                        App("app1", "org1", "appListing1", false),
                        AppListing(invalid, "app1", ListingLanguage.EN_US),
                    ).bind()
                }
            },
            TextColumnConstraintTestCase("appPackages.id") { tx, invalid ->
                either {
                    tx.externalBlobs.save(committedExternalBlob()).bind()
                    tx.appPackages.save(appPackage(id = invalid)).bind()
                }
            },
            TextColumnConstraintTestCase("appPackagePermissions.id") { tx, invalid ->
                either {
                    tx.externalBlobs.save(committedExternalBlob()).bind()
                    tx.appPackages.save(appPackage()).bind()
                    tx.appPackages.savePermission(appPackagePermission(id = invalid)).bind()
                }
            },
            TextColumnConstraintTestCase("organizations.id") { tx, invalid ->
                either {
                    // The organization's ID and the owner's organization ID must match, so both
                    // carry the invalid value
                    tx.organizations
                        .saveWithOwner(
                            organization(id = invalid),
                            user(organizationId = invalid),
                        )
                        .bind()
                }
            },
            TextColumnConstraintTestCase("pendingAppDraftUploads.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts
                        .saveUpload(pendingAppDraftUpload(id = invalid))
                        .bind()
                }
            },
            TextColumnConstraintTestCase("pendingAppDraftListingIconUploads.id") { tx, invalid ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts
                        .saveListingIconUpload(pendingAppDraftListingIconUpload(id = invalid))
                        .bind()
                }
            },
            TextColumnConstraintTestCase("users.id") { tx, invalid ->
                either {
                    // The owner's ID and the organization's owner ID must match, so both carry the
                    // invalid value
                    tx.organizations
                        .saveWithOwner(organization(ownerUserId = invalid), user(id = invalid))
                        .bind()
                }
            },
        )

        @JvmStatic
        private fun textLengthConstraintTestCases(): List<TextLengthConstraintTestCase> = listOf(
            TextLengthConstraintTestCase("appDraftListings.name", maxCodePoints = 30) { tx, tooLong ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing(name = tooLong)).bind()
                }
            },
            TextLengthConstraintTestCase(
                "appDraftListings.shortDescription",
                maxCodePoints = 80,
            ) { tx, tooLong ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing(shortDescription = tooLong)).bind()
                }
            },
        )

        @JvmStatic
        private fun textColumnConstraintTestCases(): List<Arguments> {
            val invalidInputs = listOf(
                InvalidCanonicalText.Empty,
                InvalidCanonicalText.ContainsUnassignedChar,
                InvalidCanonicalText.NonNfc,
            )

            // Foreign key columns are intentionally omitted here since their domains are already
            // limited as desired at the target column
            val cases = idColumnConstraintTestCases() + listOf(
                TextColumnConstraintTestCase("appDraftListings.name") { tx, invalid ->
                    either {
                        tx.organizations.saveWithOwner(organization(), user()).bind()
                        tx.appDrafts.save(unsubmittedAppDraft()).bind()
                        tx.appDrafts.saveListing(appDraftListing(name = invalid)).bind()
                    }
                },
                TextColumnConstraintTestCase("appDraftListings.shortDescription") { tx, invalid ->
                    either {
                        tx.organizations.saveWithOwner(organization(), user()).bind()
                        tx.appDrafts.save(unsubmittedAppDraft()).bind()
                        tx.appDrafts.saveListing(appDraftListing(shortDescription = invalid)).bind()
                    }
                },
                TextColumnConstraintTestCase("appPackages.versionName") { tx, invalid ->
                    either {
                        tx.externalBlobs.save(committedExternalBlob()).bind()
                        tx.appPackages
                            .save(appPackage(versionName = VersionName.fromString(invalid).unwrap()))
                            .bind()
                    }
                },
                TextColumnConstraintTestCase("appPackagePermissions.name") { tx, invalid ->
                    either {
                        tx.externalBlobs.save(committedExternalBlob()).bind()
                        tx.appPackages.save(appPackage()).bind()
                        tx.appPackages
                            .savePermission(
                                appPackagePermission(name = NameAttribute.fromString(invalid).unwrap()),
                            )
                            .bind()
                    }
                },
                TextColumnConstraintTestCase("externalBlobs.bucketName") { tx, invalid ->
                    either {
                        tx.externalBlobs.save(committedExternalBlob(bucketName = invalid)).bind()
                    }
                },
                TextColumnConstraintTestCase("externalBlobs.objectKey") { tx, invalid ->
                    either {
                        tx.externalBlobs.save(committedExternalBlob(objectKey = invalid)).bind()
                    }
                },
                TextColumnConstraintTestCase("pendingAppDraftUploads.objectKey") { tx, invalid ->
                    either {
                        tx.externalBlobs.save(pendingExternalBlob()).bind()
                        tx.appDrafts.saveUpload(pendingAppDraftUpload(objectKey = invalid)).bind()
                    }
                },
            )

            return cases.flatMap { case ->
                invalidInputs.map { invalidInput -> Arguments.of(case, invalidInput) }
            }
        }

        @JvmStatic
        private fun appDraftUploadProcessingResults(): List<Either<AppDraftUploadProcessingError, Unit>> = listOf(
            Unit.right(),
            AppDraftUploadProcessingError.AppDraftSubmitted.left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(ApkSetParseError.InvalidFormat).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(ApkSetParseError.Io).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Missing64BitCode,
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.LowTargetSdk,
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(ApkParseError.Policy.NoModernSignature),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(ApkParseError.Policy.SignedWithDebugCert),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(ApkParseError.Policy.SignedWithMultipleCerts),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(ApkParseError.Policy.Unverified),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(AndroidManifest.FromXmlError.Policy.DebuggableTrue),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(AndroidManifest.FromXmlError.Policy.TestOnlyTrue),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.MultipleApplicationElements,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.MultipleUsesSdkElements,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(AndroidManifest.FromXmlError.Policy.NoVersionCode),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.DuplicatePermission,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.InvalidApplicationId,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.PermissionMaxSdkOutOfRange,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.PermissionNameTooLong,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.VersionCodeOutOfRange,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.VersionCodeMajorNonZero,
                    ),
                ),
            ).left(),
            AppDraftUploadProcessingError.ApkSetParseFailed(
                ApkSetParseError.Policy.Apk(
                    ApkParseError.Policy.Manifest(
                        AndroidManifest.FromXmlError.Policy.VersionNameTooLong,
                    ),
                ),
            ).left(),
        )

        @JvmStatic
        private fun appDraftUploadResults(): List<Option<Either<AppDraftUploadProcessingError, Unit>>> =
            listOf(None) + appDraftUploadProcessingResults().map { Some(it) }

        @JvmStatic
        private fun appDraftListingIconUploadProcessingResults()
                : List<AppDraftListingIconUploadProcessingResult> = listOf(
            AppDraftListingIconUploadProcessingResult.Success,
            AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted,
            AppDraftListingIconUploadProcessingResult.Error.InvalidImage,
            AppDraftListingIconUploadProcessingResult.Error.IncorrectImageDimensions,
        )

        data class AuthzHasPermissionReturnsTrueWithMinimalRelationships(
            val hasPermissionRequest: HasPermissionRequest,
            val saveRelationships: (DataStore.Transaction) -> DataStoreResult<Unit>,
        )

        @JvmStatic
        private fun authzHasPermissionReturnsTrueWithMinimalRelationshipsTestCases()
                : List<AuthzHasPermissionReturnsTrueWithMinimalRelationships> = listOf(
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.CreateAppDraft("org1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.CreateAppDraftListing("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.DeleteAppDraft("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.DeleteAppDraftListing("appDraftListing1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.DownloadAppDraft("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.ReplaceAppDraftPackage("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.SubmitAppDraft("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.UpdateApp("app1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.apps.saveWithDefaultListing(
                        App("app1", "org1", "appListing1", false),
                        AppListing("appListing1", "app1", ListingLanguage.EN_US),
                    ).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.UpdateAppDraft("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.UpdateAppDraftListing("appDraftListing1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.UploadAppDraftListingIcon("appDraftListing1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.ViewApp("app1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.apps.saveWithDefaultListing(
                        App("app1", "org1", "appListing1", false),
                        AppListing("appListing1", "app1", ListingLanguage.EN_US),
                    ).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.ViewAppDraft("appDraft1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                }
            },
            AuthzHasPermissionReturnsTrueWithMinimalRelationships(
                HasPermissionRequest.ViewAppDraftListing("appDraftListing1", "user1"),
            ) { tx ->
                either {
                    tx.organizations.saveWithOwner(organization(), user()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                }
            },
        )
    }
}
