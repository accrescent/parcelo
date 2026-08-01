// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.api.console

import app.accrescent.server.parcelo.UNIX_EPOCH
import app.accrescent.server.parcelo.adapters.driven.blobstorage.LocalBlobStorage
import app.accrescent.server.parcelo.adapters.driven.blobstorage.LocalOnlyBlobStorage
import app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore
import app.accrescent.server.parcelo.adapters.driven.randomsource.DeterministicRandomSource
import app.accrescent.server.parcelo.adapters.driven.timestampsource.ConstantTimestampSource
import app.accrescent.server.parcelo.appDraftListing
import app.accrescent.server.parcelo.appPackage
import app.accrescent.server.parcelo.committedExternalBlob
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.core.unwrap2
import app.accrescent.server.parcelo.core.unwrapErr
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.SdkVersion
import app.accrescent.server.parcelo.domain.android.VersionCode
import app.accrescent.server.parcelo.domain.android.VersionName
import app.accrescent.server.parcelo.domain.ports.driven.datastore.App
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ExternalBlob
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftUpload
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSource
import app.accrescent.server.parcelo.domain.ports.driven.timestampsource.TimestampSource
import app.accrescent.server.parcelo.domain.ports.driving.console.ActiveAppDraftLimitExceededError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftApi
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftHasNoDefaultListingError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftHasNoPackageError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListingAlreadyExistsError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListingNotFoundError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftPackageNotFoundError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftSubmittedError
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftSubmittedForAppIdError
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.CreateAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DeleteAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.DownloadAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftListingResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.GetAppDraftResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.InsufficientPermissionError
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftListingsRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftsRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.ListAppDraftsResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.Operation
import app.accrescent.server.parcelo.domain.ports.driving.console.PublishedAppLimitExceededError
import app.accrescent.server.parcelo.domain.ports.driving.console.SubmitAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftListingRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UpdateAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftListingIconRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftListingIconResponse
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftRequest
import app.accrescent.server.parcelo.domain.ports.driving.console.UploadAppDraftResponse
import app.accrescent.server.parcelo.domain.uri.HttpUri
import app.accrescent.server.parcelo.organization
import app.accrescent.server.parcelo.organizationOwnerRelationship
import app.accrescent.server.parcelo.pendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.pendingAppDraftUpload
import app.accrescent.server.parcelo.pendingExternalBlob
import app.accrescent.server.parcelo.unsubmittedAppDraft
import app.accrescent.server.parcelo.user
import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraft as DataAppDraft
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ListingLanguage as DataListingLanguage
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraft as ApiAppDraft
import app.accrescent.server.parcelo.domain.ports.driving.console.AppDraftListing as ApiAppDraftListing
import app.accrescent.server.parcelo.domain.ports.driving.console.AppPackage as ApiAppPackage
import app.accrescent.server.parcelo.domain.ports.driving.console.ListingLanguage as ApiListingLanguage

class AppDraftApiImplTest {
    @Test
    fun `createAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.createAppDraft("user1", CreateAppDraftRequest("org1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `createAppDraft returns persisted app draft ID for authorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .createAppDraft("user1", CreateAppDraftRequest("org1"))
                .unwrap()
            val persistedAppDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById(response.appDraftId).bind() }
                .unwrap2()
                .unwrap()

            assertEquals(response.appDraftId, persistedAppDraft.id)
        }
    }

    @Test
    fun `createAppDraft returns ActiveAppDraftLimitExceeded when attempting to exceed org active app draft limit`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft3")).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.createAppDraft("user1", CreateAppDraftRequest("org1"))

            assertEquals(ActiveAppDraftLimitExceededError(3uL), response.unwrapErr())
        }
    }

    @Test
    fun `downloadAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.downloadAppDraft("user1", DownloadAppDraftRequest("appDraft1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `downloadAppDraft returns AppDraftPackageNotFound when app draft exists without package`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.downloadAppDraft("user1", DownloadAppDraftRequest("appDraft1"))

            assertEquals(AppDraftPackageNotFoundError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `downloadAppDraft returns successfully when permitted`() {
        val randomSource = DeterministicRandomSource()
        InMemoryDataStore.create(randomSource).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
            }
                .unwrap2()
            val blobStorage = LocalBlobStorage(randomSource)
            val appDraftApi = makeAppDraftApi(dataStore, blobStorage = blobStorage)

            val response = appDraftApi.downloadAppDraft("user1", DownloadAppDraftRequest("appDraft1"))

            assertEquals(
                DownloadAppDraftResponse(
                    HttpUri
                        .fromString(
                            "http://localhost:${blobStorage.port}/download/" +
                                    "bb20b45f-d4d9-5138-3d93-cb799b3970be"
                        )
                        .unwrap()
                )
                    .right(),
                response,
            )
        }
    }

    @Test
    fun `getAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.getAppDraft("user1", GetAppDraftRequest("appDraft1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `getAppDraft returns entity created by createAppDraft`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val createResponse =
                appDraftApi.createAppDraft("user1", CreateAppDraftRequest("org1")).unwrap()
            val getResponse = appDraftApi.getAppDraft(
                "user1",
                GetAppDraftRequest(createResponse.appDraftId),
            )

            assertEquals(
                GetAppDraftResponse(
                    ApiAppDraft.Unsubmitted(
                        id = "ad_2wTa5P82Lwqd50UvNyQRad",
                        createTime = UNIX_EPOCH,
                        defaultAppDraftListingId = None,
                        appPackage = None,
                    )
                ).right(),
                getResponse,
            )
        }
    }

    @Test
    fun `getAppDraft returns correct fully populated app draft`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.getAppDraft("user1", GetAppDraftRequest("appDraft1"))

            assertEquals(
                GetAppDraftResponse(
                    ApiAppDraft.Submitted(
                        id = "appDraft1",
                        createTime = UNIX_EPOCH,
                        defaultAppDraftListingId = "appDraftListing1",
                        appPackage = ApiAppPackage(
                            androidApplicationId = ApplicationId.fromString("com.example.app").unwrap(),
                            versionCode = VersionCode.fromInt(1).unwrap(),
                            versionName = VersionName.fromString("1.0").unwrap(),
                            targetSdk = SdkVersion.fromInt(37).unwrap(),
                        ),
                        submitTime = UNIX_EPOCH,
                    )
                ).right(),
                response,
            )
        }
    }

    @Test
    fun `listAppDrafts returns app drafts from only requested organization`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val timestampSource = ConstantTimestampSource()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization("org1")).bind()
                tx.organizations.save(organization("org2")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1", "org1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2", "org2")).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship("org1", "user1")).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship("org2", "user1")).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .listAppDrafts("user1", ListAppDraftsRequest("org1", 2u, null))
                .map { it.appDrafts }

            assertEquals(
                listOf(
                    ApiAppDraft.Unsubmitted(
                        id = "appDraft1",
                        createTime = timestampSource.now(),
                        defaultAppDraftListingId = None,
                        appPackage = None,
                    )
                ).right(),
                response,
            )
        }
    }

    @Test
    fun `listAppDrafts returns correct fully populated app drafts`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .listAppDrafts("user1", ListAppDraftsRequest("org1", 1u, null))
                .map { it.appDrafts }

            assertEquals(
                listOf(
                    ApiAppDraft.Submitted(
                        id = "appDraft1",
                        createTime = UNIX_EPOCH,
                        defaultAppDraftListingId = "appDraftListing1",
                        appPackage = ApiAppPackage(
                            androidApplicationId = ApplicationId.fromString("com.example.app").unwrap(),
                            versionCode = VersionCode.fromInt(1).unwrap(),
                            versionName = VersionName.fromString("1.0").unwrap(),
                            targetSdk = SdkVersion.fromInt(37).unwrap(),
                        ),
                        submitTime = UNIX_EPOCH,
                    )
                ).right(),
                response,
            )
        }
    }

    @Test
    fun `listAppDrafts returns only authorized app drafts`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .listAppDrafts("user1", ListAppDraftsRequest("org1", 1u, null))
                .map { it.appDrafts }

            assertEquals(emptyList<ApiAppDraft>().right(), response)
        }
    }

    @Test
    fun `listAppDrafts respects page size as maximum`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.listAppDrafts("user1", ListAppDraftsRequest("org1", 1u, null))

            assertInstanceOf<Either.Right<ListAppDraftsResponse>>(response)
            assertEquals(1, response.value.appDrafts.size)
        }
    }

    @Test
    fun `listAppDrafts traverses all items when paginating`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val allDrafts = mutableListOf<ApiAppDraft>()
            var pageToken: String? = null
            do {
                val response = appDraftApi
                    .listAppDrafts("user1", ListAppDraftsRequest("org1", 1u, pageToken))
                    .unwrap()
                allDrafts.addAll(response.appDrafts)
                pageToken = response.nextPageToken
            } while (pageToken != null)

            assertEquals(setOf("appDraft1", "appDraft2"), allDrafts.map { it.id }.toSet())
        }
    }

    @Test
    fun `listAppDrafts returns page token if items remain`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.listAppDrafts("user1", ListAppDraftsRequest("org1", 1u, null))

            assertInstanceOf<Either.Right<ListAppDraftsResponse>>(response)
            assertNotNull(response.value.nextPageToken)
        }
    }

    @Test
    fun `submitAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft returns AppDraftSubmitted when app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft returns AppDraftHasNoPackage when app draft does not have a package`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(AppDraftHasNoPackageError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft returns AppDraftHasNoDefaultListing when app draft does not have a default listing`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(AppDraftHasNoDefaultListingError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft returns AppDraftListingsMissingIcons when not all app draft listings have an icon`() {
        // TODO
    }

    @Test
    fun `submitAppDraft returns AppDraftSubmittedForAppId when an app draft has already been submitted with the app ID`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob("blob1", objectKey = "object1")).bind()
                tx.externalBlobs.save(committedExternalBlob("blob2", objectKey = "object2")).bind()
                tx.appPackages.save(appPackage("appPackage1", externalBlobId = "blob1")).bind()
                tx.appPackages.save(appPackage("appPackage2", externalBlobId = "blob2")).bind()
                tx.appDrafts
                    .save(unsubmittedAppDraft("appDraft2", appPackageId = Some("appPackage1")))
                    .bind()
                tx.appDrafts.saveListing(appDraftListing("appDraftListing2", "appDraft2")).bind()
                tx.appDrafts.updateDefaultListing("appDraft2", "appDraftListing2").bind()
                tx.appDrafts.updateSubmitTime("appDraft2", UNIX_EPOCH).bind()
                tx.appDrafts
                    .save(unsubmittedAppDraft("appDraft1", appPackageId = Some("appPackage2")))
                    .bind()
                tx.appDrafts.saveListing(appDraftListing("appDraftListing1", "appDraft1")).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(AppDraftSubmittedForAppIdError("com.example.app"), response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft returns PublishedAppLimitExceeded when the organization's published app limit is already reached`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.apps.saveWithDefaultListing(
                    App("app1", "org1", "appListing1", false),
                    AppListing("appListing1", "app1", DataListingLanguage.EN_US),
                ).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1"))

            assertEquals(PublishedAppLimitExceededError(1uL), response.unwrapErr())
        }
    }

    @Test
    fun `submitAppDraft submits app draft for valid request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            appDraftApi.submitAppDraft("user1", SubmitAppDraftRequest("appDraft1")).unwrap()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()
                .unwrap()

            assertInstanceOf<DataAppDraft.Submitted>(appDraft)
            assertEquals(UNIX_EPOCH, appDraft.submitTime)
        }
    }

    @Test
    fun `uploadAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.uploadAppDraft("user1", UploadAppDraftRequest("appDraft1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `uploadAppDraft returns AppDraftSubmitted when app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.uploadAppDraft("user1", UploadAppDraftRequest("appDraft1"))

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `uploadAppDraft returns successfully when permitted`() {
        val randomSource = DeterministicRandomSource()
        LocalBlobStorage(randomSource).use { blobStorage ->
            InMemoryDataStore.create(randomSource).unwrap().use { dataStore ->
                dataStore.migrateToHead().unwrap()
                dataStore.runTxWithRetry { tx ->
                    tx.organizations.save(organization()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.users.save(user()).bind()
                    tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                }.unwrap2()
                val appDraftApi = makeAppDraftApi(dataStore, blobStorage = blobStorage)

                val response = appDraftApi.uploadAppDraft("user1", UploadAppDraftRequest("appDraft1"))

                assertEquals(
                    UploadAppDraftResponse(
                        apkSetUploadUri = HttpUri
                            .fromString(
                                "http://localhost:${blobStorage.port}/upload/" +
                                        "bb20b45f-d4d9-5138-3d93-cb799b3970be"
                            )
                            .unwrap(),
                        processingOperation = Operation.Incomplete("op_15TfMFK4XWQ8gt714e6cqx"),
                    )
                        .right(),
                    response,
                )
            }
        }
    }

    @Test
    fun `updateAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.updateAppDraft("user1", UpdateAppDraftRequest("appDraft1", "appDraftListing1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `updateAppDraft returns AppDraftSubmitted when app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .updateAppDraft("user1", UpdateAppDraftRequest("appDraft1", "appDraftListing1"))

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `updateAppDraft returns AppDraftListingNotFound when new default listing ID does not exist`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.updateAppDraft(
                "user1",
                UpdateAppDraftRequest("appDraft1", "appDraftListing1"),
            )

            assertEquals(AppDraftListingNotFoundError("appDraftListing1"), response.unwrapErr())
        }
    }

    @Test
    fun `uploadAppDraft replaces existing pending upload when one already exists for the app draft`() {
        val randomSource = DeterministicRandomSource()
        LocalBlobStorage(randomSource).use { blobStorage ->
            InMemoryDataStore.create(randomSource).unwrap().use { dataStore ->
                dataStore.migrateToHead().unwrap()
                dataStore.runTxWithRetry { tx ->
                    tx.organizations.save(organization()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveUpload(pendingAppDraftUpload()).bind()
                    tx.users.save(user()).bind()
                    tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                }.unwrap2()
                val appDraftApi = makeAppDraftApi(dataStore, blobStorage = blobStorage)

                appDraftApi.uploadAppDraft("user1", UploadAppDraftRequest("appDraft1")).unwrap()
                val replacedUpload = dataStore
                    .runTxWithRetry { tx ->
                        tx.appDrafts
                            .findPendingUploadByObjectKey("obj_2wTa5P82Lwqd50UvNyQRad")
                            .bind()
                    }
                    .unwrap2()

                assertEquals(
                    Some(
                        PendingAppDraftUpload(
                            id = "adu_1uMy9o9BqyoxomLjIEbctU",
                            appDraftId = "appDraft1",
                            externalBlobId = "blob_7Vg3AgHSuhCI6g3btbAESz",
                            objectKey = "obj_2wTa5P82Lwqd50UvNyQRad",
                            createTime = UNIX_EPOCH,
                            result = None,
                        )
                    ),
                    replacedUpload,
                )
            }
        }
    }

    @Test
    fun `updateAppDraft updates default listing ID for valid request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            appDraftApi.updateAppDraft(
                "user1",
                UpdateAppDraftRequest("appDraft1", "appDraftListing1"),
            )
                .unwrap()
            val response = appDraftApi.getAppDraft("user1", GetAppDraftRequest("appDraft1")).unwrap()

            assertEquals(
                Some("appDraftListing1"),
                response.appDraft.optionalDefaultAppDraftListingId,
            )
        }
    }

    @Test
    fun `deleteAppDraft returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.deleteAppDraft("user1", DeleteAppDraftRequest("appDraft1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `deleteAppDraft returns AppDraftSubmitted when app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi.deleteAppDraft("user1", DeleteAppDraftRequest("appDraft1"))

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `deleteAppDraft deletes app draft entity`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            appDraftApi.deleteAppDraft("user1", DeleteAppDraftRequest("appDraft1")).unwrap()
            val appDraft = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findById("appDraft1").bind() }
                .unwrap2()

            assertTrue(appDraft.isNone())
        }
    }

    @Test
    fun `deleteAppDraft deletes app draft's associated package`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            appDraftApi.deleteAppDraft("user1", DeleteAppDraftRequest("appDraft1")).unwrap()
            val appPackage = dataStore
                .runTxWithRetry { tx -> tx.appPackages.findById("appPackage1").bind() }
                .unwrap2()

            assertTrue(appPackage.isNone())
        }
    }

    @Test
    fun `deleteAppDraft marks app draft package's blob for deletion`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            appDraftApi.deleteAppDraft("user1", DeleteAppDraftRequest("appDraft1")).unwrap()
            val externalBlob = dataStore
                .runTxWithRetry { tx -> tx.externalBlobs.requireById("blob1").bind() }
                .unwrap2()

            assertInstanceOf<ExternalBlob.Status.Deleted<*>>(externalBlob.status)
        }
    }

    @Test
    fun `createAppDraftListing returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = CreateAppDraftListingRequest(
                appDraftId = "appDraft1",
                language = ApiListingLanguage.EN_US,
                name = "App Name",
                shortDescription = "App Short Description",
            )
            val response = appDraftApi.createAppDraftListing("user1", request)

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `createAppDraftListing returns AppDraftListingAlreadyExists if listing exists for app draft with same language`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = CreateAppDraftListingRequest(
                appDraftId = "appDraft1",
                language = ApiListingLanguage.EN_US,
                name = "App Name",
                shortDescription = "App Short Description",
            )
            val response = appDraftApi.createAppDraftListing("user1", request)

            assertEquals(
                AppDraftListingAlreadyExistsError("appDraft1", "en-US"),
                response.unwrapErr(),
            )
        }
    }

    @Test
    fun `createAppDraftListing returns AppDraftSubmitted if app draft has already been submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = CreateAppDraftListingRequest(
                appDraftId = "appDraft1",
                language = ApiListingLanguage.EN_US,
                name = "App Name",
                shortDescription = "App Short Description",
            )
            val response = appDraftApi.createAppDraftListing("user1", request)

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `createAppDraftListing returns persisted app draft listing ID for authorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = CreateAppDraftListingRequest(
                appDraftId = "appDraft1",
                language = ApiListingLanguage.EN_US,
                name = "App Name",
                shortDescription = "App Short Description",
            )
            val response = appDraftApi.createAppDraftListing("user1", request).unwrap()
            val persistedAppDraftListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById(response.appDraftListingId).bind() }
                .unwrap2()
                .unwrap()

            assertEquals(response.appDraftListingId, persistedAppDraftListing.id)
        }
    }

    @Test
    fun `getAppDraftListing returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .getAppDraftListing("user1", GetAppDraftListingRequest("appDraftListing1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `getAppDraftListing returns entity created by createAppDraftListing`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val createRequest = CreateAppDraftListingRequest("appDraft1", ApiListingLanguage.EN_US, "name", "desc")
            val createResponse = appDraftApi.createAppDraftListing("user1", createRequest).unwrap()
            val getResponse = appDraftApi
                .getAppDraftListing("user1", GetAppDraftListingRequest(createResponse.appDraftListingId))
                .unwrap()

            assertEquals(
                GetAppDraftListingResponse(
                    ApiAppDraftListing(
                        id = createResponse.appDraftListingId,
                        appDraftId = "appDraft1",
                        language = "en-US",
                        name = "name",
                        shortDescription = "desc",
                    )
                ),
                getResponse,
            )
        }
    }

    @Test
    fun `listAppDraftListings returns app draft listings from only requested app draft`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft1")).bind()
                tx.appDrafts.save(unsubmittedAppDraft("appDraft2")).bind()
                tx.appDrafts.saveListing(appDraftListing("appDraftListing1", "appDraft1")).bind()
                tx.appDrafts.saveListing(appDraftListing("appDraftListing2", "appDraft2")).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .listAppDraftListings("user1", ListAppDraftListingsRequest("appDraft1", 2u, null))
                .map { it.appDraftListings }

            assertEquals(
                listOf(
                    ApiAppDraftListing(
                        id = "appDraftListing1",
                        appDraftId = "appDraft1",
                        language = "en-US",
                        name = "Example App",
                        shortDescription = "Example Short Description",
                    )
                ).right(),
                response,
            )
        }
    }

    @Test
    fun `listAppDraftListings returns only authorized app draft listings`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.users.save(user()).bind()
            }.unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .listAppDraftListings("user1", ListAppDraftListingsRequest("appDraft1", 1u, null))
                .map { it.appDraftListings }

            assertEquals(emptyList<ApiAppDraftListing>().right(), response)
        }
    }

    @Test
    fun `updateAppDraftListing returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = UpdateAppDraftListingRequest("appDraftListing1", null, null)
            val response = appDraftApi.updateAppDraftListing("user1", request)

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `updateAppDraftListing returns AppDraftSubmitted if app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = UpdateAppDraftListingRequest("appDraftListing1", null, null)
            val response = appDraftApi.updateAppDraftListing("user1", request)

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `updateAppDraftListing updates all requested fields for authorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = UpdateAppDraftListingRequest(
                appDraftListingId = "appDraftListing1",
                name = "App Name",
                shortDescription = "App Short Description",
            )
            appDraftApi.updateAppDraftListing("user1", request).unwrap()
            val getResponse = appDraftApi
                .getAppDraftListing("user1", GetAppDraftListingRequest("appDraftListing1"))
                .unwrap()

            assertEquals(
                GetAppDraftListingResponse(
                    ApiAppDraftListing(
                        id = "appDraftListing1",
                        appDraftId = "appDraft1",
                        language = "en-US",
                        name = "App Name",
                        shortDescription = "App Short Description",
                    )
                ),
                getResponse,
            )
        }
    }

    @Test
    fun `uploadAppDraftListingIcon returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .uploadAppDraftListingIcon("user1", UploadAppDraftListingIconRequest("appDraftListing1"))

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `uploadAppDraftListingIcon returns AppDraftSubmitted when app draft is already submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", UNIX_EPOCH).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val response = appDraftApi
                .uploadAppDraftListingIcon("user1", UploadAppDraftListingIconRequest("appDraftListing1"))

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `uploadAppDraftListingIcon returns successfully when permitted`() {
        val randomSource = DeterministicRandomSource()
        LocalBlobStorage(randomSource).use { blobStorage ->
            InMemoryDataStore.create(randomSource).unwrap().use { dataStore ->
                dataStore.migrateToHead().unwrap()
                dataStore.runTxWithRetry { tx ->
                    tx.organizations.save(organization()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                    tx.users.save(user()).bind()
                    tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                }.unwrap2()
                val appDraftApi = makeAppDraftApi(dataStore, blobStorage = blobStorage)

                val response = appDraftApi
                    .uploadAppDraftListingIcon("user1", UploadAppDraftListingIconRequest("appDraftListing1"))

                assertEquals(
                    UploadAppDraftListingIconResponse(
                        uploadUri = HttpUri
                            .fromString(
                                "http://localhost:${blobStorage.port}/upload/" +
                                        "bb20b45f-d4d9-5138-3d93-cb799b3970be"
                            )
                            .unwrap(),
                        processingOperation = Operation.Incomplete("op_15TfMFK4XWQ8gt714e6cqx"),
                    )
                        .right(),
                    response,
                )
            }
        }
    }

    @Test
    fun `uploadAppDraftListingIcon replaces existing pending upload when one already exists for the listing`() {
        val randomSource = DeterministicRandomSource()
        LocalBlobStorage(randomSource).use { blobStorage ->
            InMemoryDataStore.create(randomSource).unwrap().use { dataStore ->
                dataStore.migrateToHead().unwrap()
                dataStore.runTxWithRetry { tx ->
                    tx.organizations.save(organization()).bind()
                    tx.appDrafts.save(unsubmittedAppDraft()).bind()
                    tx.appDrafts.saveListing(appDraftListing()).bind()
                    tx.externalBlobs.save(pendingExternalBlob()).bind()
                    tx.appDrafts.saveListingIconUpload(pendingAppDraftListingIconUpload()).bind()
                    tx.users.save(user()).bind()
                    tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
                }.unwrap2()
                val appDraftApi = makeAppDraftApi(dataStore, blobStorage = blobStorage)

                appDraftApi
                    .uploadAppDraftListingIcon("user1", UploadAppDraftListingIconRequest("appDraftListing1"))
                    .unwrap()
                val replacedUpload = dataStore
                    .runTxWithRetry { tx ->
                        tx.appDrafts
                            .findPendingListingIconUploadByObjectKey("obj_2wTa5P82Lwqd50UvNyQRad")
                            .bind()
                    }
                    .unwrap2()

                assertEquals(
                    Some(
                        PendingAppDraftListingIconUpload(
                            id = "adliu_1uMy9o9BqyoxomLjIEbctU",
                            appDraftListingId = "appDraftListing1",
                            externalBlobId = "blob_7Vg3AgHSuhCI6g3btbAESz",
                            objectKey = "obj_2wTa5P82Lwqd50UvNyQRad",
                            createTime = UNIX_EPOCH,
                            result = None,
                        )
                    ),
                    replacedUpload,
                )
            }
        }
    }

    @Test
    fun `downloadAppDraftListingIcon returns InsufficientPermission for unauthorized request`() {
        // TODO
    }

    @Test
    fun `downloadAppDraftListingIcon returns AppDraftListingIconNotFound when app draft listing exists without icon`() {
        // TODO
    }

    @Test
    fun `downloadAppDraftListingIcon returns successfully when permitted`() {
        // TODO
    }

    @Test
    fun `deleteAppDraftListing returns InsufficientPermission for unauthorized request`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = DeleteAppDraftListingRequest("appDraftListing1")
            val response = appDraftApi.deleteAppDraftListing("user1", request)

            assertEquals(InsufficientPermissionError, response.unwrapErr())
        }
    }

    @Test
    fun `deleteAppDraftListing returns AppDraftSubmitted if app draft has been submitted`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.externalBlobs.save(committedExternalBlob()).bind()
                tx.appPackages.save(appPackage()).bind()
                tx.appDrafts.save(unsubmittedAppDraft(appPackageId = Some("appPackage1"))).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.appDrafts.updateDefaultListing("appDraft1", "appDraftListing1").bind()
                tx.appDrafts.updateSubmitTime("appDraft1", ConstantTimestampSource().now()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = DeleteAppDraftListingRequest("appDraftListing1")
            val response = appDraftApi.deleteAppDraftListing("user1", request)

            assertEquals(AppDraftSubmittedError("appDraft1"), response.unwrapErr())
        }
    }

    @Test
    fun `deleteAppDraftListing deletes app draft listing entity`() {
        InMemoryDataStore.create(DeterministicRandomSource()).unwrap().use { dataStore ->
            dataStore.migrateToHead().unwrap()
            dataStore.runTxWithRetry { tx ->
                tx.organizations.save(organization()).bind()
                tx.appDrafts.save(unsubmittedAppDraft()).bind()
                tx.appDrafts.saveListing(appDraftListing()).bind()
                tx.users.save(user()).bind()
                tx.authz.saveRelationship(organizationOwnerRelationship()).bind()
            }
                .unwrap2()
            val appDraftApi = makeAppDraftApi(dataStore)

            val request = DeleteAppDraftListingRequest("appDraftListing1")
            appDraftApi.deleteAppDraftListing("user1", request).unwrap()
            val foundListing = dataStore
                .runTxWithRetry { tx -> tx.appDrafts.findListingById("appDraftListing1").bind() }
                .unwrap2()

            assertTrue(foundListing.isNone())
        }
    }

    @Test
    fun `publishAppDraft returns InsufficientPermission for unauthorized request`() {
        // TODO
    }

    @Test
    fun `publishAppDraft returns AppDraftAlreadyPublished if app draft is already published`() {
        // TODO
    }

    @Test
    fun `publishAppDraft returns AppDraftPublishing if app draft is currently publishing`() {
        // TODO
    }

    @Test
    fun `publishAppDraft returns AppWithSameIdAlreadyExists if a published app with the same ID already exists`() {
        // TODO
    }

    private fun makeAppDraftApi(
        dataStore: DataStore,
        randomSource: RandomSource = DeterministicRandomSource(),
        blobStorage: LocalBlobStorage = LocalBlobStorage(randomSource),
        timestampSource: TimestampSource = ConstantTimestampSource(),
        appDraftUploadBucketName: String = "app-draft-uploads",
        appDraftListingIconUploadBucketName: String = "app-draft-listing-icon-uploads",
    ): AppDraftApi {
        return AppDraftApiImpl(
            dataStore,
            LocalOnlyBlobStorage(blobStorage),
            randomSource,
            timestampSource,
            appDraftUploadBucketName,
            appDraftListingIconUploadBucketName,
        )
    }
}
