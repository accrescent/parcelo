// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.PositiveLong
import app.accrescent.server.parcelo.core.bindMapLeft
import app.accrescent.server.parcelo.core.unwrap
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSource
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.raise.Raise
import arrow.core.raise.either
import java.time.OffsetDateTime

sealed class DataStoreError {
    data object CheckConstraintViolation : DataStoreError()

    data object EntityNotFound : DataStoreError()
    data object ForeignKeyViolation : DataStoreError()
    data object IllegalState : DataStoreError()
    data class RollbackErrorOnCommit(
        val rollbackError: DataStoreError,
        val commitError: DataStoreError,
    ) : DataStoreError()

    data object SerializationFailure : DataStoreError()
    data object UniqueConstraintViolation : DataStoreError()

    data object Unknown : DataStoreError()
}

typealias DataStoreResult<T> = Either<DataStoreError, T>

/**
 * A data store for application data.
 *
 * Implementations are typically backed by a transactional RDBMS.
 */
abstract class DataStore(private val randomSource: RandomSource) {
    private companion object {
        private const val BASE_TX_RETRY_DELAY_MILLIS = 25L
        private const val MAX_TX_RETRIES = 5
    }

    /**
     * Applies all the data store's pending migrations.
     *
     * This method is idempotent and will return successfully if the data store is
     * known to have no remaining pending migrations. Concurrent calls for the same backing data
     * store will block until migration is complete.
     */
    abstract fun migrateToHead(): DataStoreResult<Unit>

    /**
     * Runs the given lambda in the context of a transaction.
     *
     * All methods accessible through the [Transaction] provided in [block] participate in the
     * transaction if they are called within [block]'s scope. Calling any such methods outside of
     * [block]'s scope results in undefined behavior.
     *
     * The transaction is committed if [block] raises an [Either.Right]. If [block] returns an
     * [Either.Left] or throws, the transaction is rolled back.
     *
     * The transaction's isolation level is equivalent to the PL-3 isolation level defined in
     * [Generalized Isolation Level Definitions](https://doi.org/10.1109/ICDE.2000.839388) by Adya
     * et al. Less formally, it provides true serializability, which is distinct from and stronger
     * than some database implementations of the SQL standard SERIALIZABLE isolation level in that
     * it guarantees that concurrent execution of a set of serializable transactions will produce
     * the same effect as running them one at a time in some order. Snapshot isolation on its own,
     * notably, does not meet this standard.
     *
     * @param block a function to run in the context of the data store transaction.
     * @return the value returned by [block] wrapped in [Either.Right], or the mapped error wrapped
     * in [Either.Left].
     */
    protected abstract fun <T, E> runInTransaction(
        block: Raise<E>.(Transaction) -> T,
    ): DataStoreResult<Either<E, T>>

    /**
     * Runs the given lambda in the context of a transaction, retrying up to 5 times total on
     * [DataStoreError.SerializationFailure].
     *
     * See [runInTransaction] for the transaction semantics applied to each attempt.
     *
     * @param block a function to run in the context of the data store transaction.
     * @return the value returned by [block] on success, or [DataStoreError.SerializationFailure]
     * if all attempts are exhausted, or another [DataStoreError] if a non-retryable error occurs.
     */
    fun <T, E> runTxWithRetry(
        block: Raise<E>.(Transaction) -> T,
    ): DataStoreResult<Either<E, T>> = either {
        repeat(MAX_TX_RETRIES) { attempt ->
            val result = runInTransaction(block)
            if (result !is Either.Left || result.value !is DataStoreError.SerializationFailure) {
                return result
            }
            val delayMillis = Math
                // All of these operations are guaranteed to not throw since, given:
                //
                // - BASE_TX_RETRY_DELAY_MILLIS = 25
                // - max(attempt) = 4
                //
                // then BASE_TX_RETRY_DELAY_MILLIS * 2^(max(attempt)) = 400, which does not overflow
                // a Long.
                .multiplyExact(BASE_TX_RETRY_DELAY_MILLIS, Math.powExact(2, attempt))
                .let(PositiveLong::new)
                .unwrap()
                .let(randomSource::randomNonNegativeLong)
                .bindMapLeft { DataStoreError.IllegalState }
            Thread.sleep(delayMillis.value)
        }
        return runInTransaction(block)
    }

    interface Transaction {
        val appDrafts: AppDraftRepository
        val appPackages: AppPackageRepository
        val apps: AppRepository
        val authz: AuthorizationRepository
        val externalBlobs: ExternalBlobRepository
        val organizations: OrganizationRepository
        val users: UserRepository
    }

    abstract class AppDraftRepository {
        /**
         * Counts the active app drafts in a given organization.
         *
         * An app draft is always considered active.
         *
         * @param organizationId the organization to count active app drafts in.
         * @return the number of active app drafts for the given organization.
         */
        abstract fun countActiveInOrganization(organizationId: String): DataStoreResult<ULong>

        /**
         * Deletes an app draft and its associated listings and pending uploads.
         *
         * @param id the ID of the app draft to delete.
         * @return [DataStoreError.EntityNotFound] if the app draft does not exist.
         */
        abstract fun deleteById(id: String): DataStoreResult<Unit>

        /**
         * Deletes an app draft listing.
         *
         * @param id the ID of the app draft listing to delete.
         * @return [DataStoreError.EntityNotFound] if the listing does not exist.
         */
        abstract fun deleteListingById(id: String): DataStoreResult<Unit>

        /**
         * Deletes a pending app draft listing icon upload for a given app draft listing.
         *
         * @param appDraftListingId the app draft listing ID whose pending icon upload should be
         * deleted.
         * @return [DataStoreError.EntityNotFound] if no pending icon upload exists for the given app
         * draft listing, including if no app draft listing exists with the given ID.
         */
        abstract fun deletePendingListingIconUploadByListingId(
            appDraftListingId: String,
        ): DataStoreResult<Unit>

        /**
         * Deletes a pending app draft upload for a given app draft.
         *
         * @param appDraftId the app draft ID whose pending upload should be deleted.
         * @return [DataStoreError.EntityNotFound] if no pending upload exists for the given app
         * draft, including if no app draft exists with the given ID.
         */
        abstract fun deletePendingUploadByAppDraftId(appDraftId: String): DataStoreResult<Unit>

        /**
         * Determines whether an app draft exists.
         *
         * @param id the ID of the app draft to check existence of.
         * @return whether an app draft with the given ID exists.
         */
        abstract fun existsById(id: String): DataStoreResult<Boolean>

        /**
         * Determines whether a submitted app draft exists with an app package whose app ID
         * matches the given app ID.
         *
         * @param appId the app ID to check the existence of a submitted app draft for.
         * @return whether a submitted app draft with an app package matching the given app ID
         * exists.
         */
        abstract fun existsSubmittedForAppId(appId: ApplicationId): DataStoreResult<Boolean>

        /**
         * Finds an existing app draft.
         *
         * @param id the ID of the app draft to find.
         * @return the app draft with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findById(id: String): DataStoreResult<Option<AppDraft>>

        /**
         * Finds a list of app drafts in a given organization which a given user is authorized to
         * view.
         *
         * @param organizationId the organization to list app drafts from.
         * @param userId the user ID to use for authorization.
         * @param maxResults the maximum number of app drafts to retrieve.
         * @param afterAppDraftId the app draft ID to start listing after.
         * @return the list of app drafts matching the query.
         */
        abstract fun findForOrganizationAndUserByQuery(
            organizationId: String,
            userId: String,
            maxResults: UInt,
            afterAppDraftId: String?,
        ): DataStoreResult<List<AppDraft>>

        /**
         * Finds an existing app draft listing.
         *
         * @param id the ID of the app draft listing to find.
         * @return the app draft listing with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findListingById(id: String): DataStoreResult<Option<AppDraftListing>>

        /**
         * Finds a list of app draft listings for a given app draft which a given user is authorized
         * to view.
         *
         * Results are ordered alphabetically by the listing language's BCP-47 code.
         *
         * @param appDraftId the app draft to list listings for.
         * @param userId the user ID to use for authorization.
         * @param maxResults the maximum number of app draft listings to retrieve.
         * @param afterLanguage the listing language to start listing after.
         * @return the list of app draft listings matching the query.
         */
        abstract fun findListingsForAppDraftAndUserByQuery(
            appDraftId: String,
            userId: String,
            maxResults: UInt,
            afterLanguage: ListingLanguage?,
        ): DataStoreResult<List<AppDraftListing>>

        /**
         * Finds a pending app draft listing icon upload by its target blob's object key.
         *
         * @param objectKey the object key of the blob the pending icon upload targets.
         * @return the pending app draft listing icon upload with the given object key, or [None] if
         * it doesn't exist.
         */
        abstract fun findPendingListingIconUploadByObjectKey(
            objectKey: String,
        ): DataStoreResult<Option<PendingAppDraftListingIconUpload>>

        /**
         * Finds a pending app draft upload by its target blob's object key.
         *
         * @param objectKey the object key of the blob the pending app draft upload targets.
         * @return the pending app draft upload with the given object key, or [None] if it doesn't
         * exist.
         */
        abstract fun findPendingUploadByObjectKey(
            objectKey: String,
        ): DataStoreResult<Option<PendingAppDraftUpload>>

        /**
         * Determines whether an app draft listing exists.
         *
         * @param listingId the ID of the app draft listing to check the existence of.
         * @param appDraftId the app draft ID expected of the listing to check the existence of.
         * @return true if an app draft listing with the given ID and app draft ID exists, false
         * otherwise.
         */
        abstract fun listingExistsByIdForAppDraft(
            listingId: String,
            appDraftId: String,
        ): DataStoreResult<Boolean>

        /**
         * Determines whether an app draft listing exists for a given app draft and language.
         *
         * @param appDraftId the app draft ID to check the existence of a listing for.
         * @param language the language to check the existence of a listing for.
         * @return whether an app draft listing with the given app draft ID and language exists.
         */
        abstract fun listingExistsByLanguageForAppDraft(
            appDraftId: String,
            language: ListingLanguage,
        ): DataStoreResult<Boolean>

        /**
         * Determines whether a pending app draft listing icon upload exists for a given app draft
         * listing.
         *
         * @param appDraftListingId the app draft listing ID to check the existence of a pending icon
         * upload for.
         * @return whether a pending app draft listing icon upload for the given app draft listing ID
         * exists.
         */
        abstract fun pendingListingIconUploadExistsByListingId(
            appDraftListingId: String,
        ): DataStoreResult<Boolean>

        /**
         * Determines whether a pending app draft upload exists for a given app draft.
         *
         * @param appDraftId the app draft ID to check the existence of a pending upload for.
         * @return whether a pending app draft upload for the given app draft ID exists.
         */
        abstract fun pendingUploadExistsByAppDraftId(appDraftId: String): DataStoreResult<Boolean>

        /**
         * Finds an existing app draft.
         *
         * @param id the ID of the app draft to find.
         * @return the app draft with the given ID, or [DataStoreError.EntityNotFound] if it
         * doesn't exist.
         */
        fun requireById(id: String): DataStoreResult<AppDraft> {
            return findById(id).flatMap { it.toEither { DataStoreError.EntityNotFound } }
        }

        /**
         * Finds an existing app draft listing.
         *
         * @param id the ID of the app draft listing to find.
         * @return the app draft listing with the given ID, or [DataStoreError.EntityNotFound] if it
         * doesn't exist.
         */
        fun requireListingById(id: String): DataStoreResult<AppDraftListing> {
            return findListingById(id).flatMap { it.toEither { DataStoreError.EntityNotFound } }
        }

        /**
         * Saves a new app draft.
         *
         * @param appDraft the app draft to save.
         * @return [DataStoreError.UniqueConstraintViolation] if an app draft with the same ID
         * already exists, or [DataStoreError.ForeignKeyViolation] if the organization or the
         * referenced app package does not exist.
         */
        abstract fun save(appDraft: AppDraft): DataStoreResult<Unit>

        /**
         * Saves a new app draft listing.
         *
         * @param listing the app draft listing to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a listing with the same ID or
         * (appDraftId, language) pair already exists or [DataStoreError.ForeignKeyViolation] if the
         * referenced app draft does not exist.
         */
        abstract fun saveListing(listing: AppDraftListing): DataStoreResult<Unit>

        /**
         * Saves a new pending app draft listing icon upload.
         *
         * @param upload the pending app draft listing icon upload to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a pending icon upload for the same
         * app draft listing or the same object key already exists, or
         * [DataStoreError.ForeignKeyViolation] if the app draft listing does not exist.
         */
        abstract fun saveListingIconUpload(
            upload: PendingAppDraftListingIconUpload,
        ): DataStoreResult<Unit>

        /**
         * Saves a new pending app draft upload.
         *
         * @param upload the pending app draft upload to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a pending upload for the same app
         * draft or the same object key already exists, or [DataStoreError.ForeignKeyViolation] if
         * the app draft does not exist.
         */
        abstract fun saveUpload(upload: PendingAppDraftUpload): DataStoreResult<Unit>

        /**
         * Updates the app package associated with an app draft.
         *
         * @param appDraftId the ID of the app draft to update.
         * @param appPackageId the ID of the app package to associate with the app draft.
         * @return [DataStoreError.EntityNotFound] if the app draft does not exist, or
         * [DataStoreError.ForeignKeyViolation] if an app package with the given ID does not exist.
         */
        abstract fun updateAppPackageId(
            appDraftId: String,
            appPackageId: String,
        ): DataStoreResult<Unit>

        /**
         * Updates the default listing of an app draft.
         *
         * @param appDraftId the ID of the app draft to update.
         * @param defaultAppDraftListingId the ID of app draft listing to set as the default, or
         * [None] to unset the app draft's default listing.
         * @return [DataStoreError.EntityNotFound] if the app draft does not exist, or
         * [DataStoreError.ForeignKeyViolation] if an app draft listing with the given ID does not
         * exist or if the referenced app draft listing is not associated with the given app draft.
         */
        abstract fun updateDefaultListing(
            appDraftId: String,
            defaultAppDraftListingId: Option<String>,
        ): DataStoreResult<Unit>

        /**
         * Updates the fields of an app draft listing.
         *
         * @param listingId the ID of the app draft listing to update.
         * @param name the new name for the listing, or null to leave unchanged.
         * @param shortDescription the new short description for the listing, or null to leave
         * unchanged.
         * @return [DataStoreError.EntityNotFound] if the listing does not exist.
         */
        abstract fun updateListing(
            listingId: String,
            name: String?,
            shortDescription: String?,
        ): DataStoreResult<Unit>

        /**
         * Updates the result of a pending app draft listing icon upload.
         *
         * @param pendingUploadId the ID of the pending app draft listing icon upload to update.
         * @param result the new result for the pending app draft listing icon upload.
         * @return [DataStoreError.EntityNotFound] if the upload does not exist.
         */
        abstract fun updatePendingListingIconUploadResult(
            pendingUploadId: String,
            result: AppDraftListingIconUploadProcessingResult,
        ): DataStoreResult<Unit>

        /**
         * Updates the result of a pending app draft upload.
         *
         * @param pendingUploadId the ID of the pending app draft upload to update.
         * @param result the new result for the pending app draft upload.
         * @return [DataStoreError.EntityNotFound] if the upload does not exist.
         */
        abstract fun updatePendingUploadResult(
            pendingUploadId: String,
            result: Either<AppDraftUploadProcessingError, Unit>,
        ): DataStoreResult<Unit>

        /**
         * Updates the submit time of an app draft.
         *
         * @param appDraftId the ID of the app draft to update.
         * @param submitTime the submit time to set for the app draft.
         * @return [DataStoreError.EntityNotFound] if the app draft does not exist, or
         * [DataStoreError.CheckConstraintViolation] if the app draft doesn't have an associated app
         * package and default listing.
         */
        abstract fun updateSubmitTime(
            appDraftId: String,
            submitTime: OffsetDateTime,
        ): DataStoreResult<Unit>
    }

    abstract class AppPackageRepository {
        /**
         * Deletes an app package.
         *
         * @param id the ID of the app package to delete.
         * @return [DataStoreError.EntityNotFound] if the app package does not exist, or
         * [DataStoreError.ForeignKeyViolation] if an app draft still references this package.
         */
        abstract fun deleteById(id: String): DataStoreResult<Unit>

        /**
         * Finds an app package by its ID.
         *
         * @param id the ID of the app package to find.
         * @return the app package with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findById(id: String): DataStoreResult<Option<AppPackage>>

        /**
         * Finds an app package by the ID of its corresponding app draft.
         *
         * @param appDraftId the app draft to find a package for.
         * @return the app package for the app draft with the given ID, or [None] if one does not
         * exist.
         */
        abstract fun findByAppDraftId(appDraftId: String): DataStoreResult<Option<AppPackage>>

        /**
         * Finds an app package by its ID.
         *
         * @param id the ID of the app package to find.
         * @return the app package with the given ID, or [DataStoreError.EntityNotFound] if it
         * doesn't exist.
         */
        fun requireById(id: String): DataStoreResult<AppPackage> {
            return findById(id).flatMap { it.toEither { DataStoreError.EntityNotFound } }
        }

        /**
         * Saves new app package.
         *
         * @param appPackage the app package to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a package with the same ID already
         * exists, or [DataStoreError.ForeignKeyViolation] if the external blob does not exist or is
         * not committed.
         */
        abstract fun save(appPackage: AppPackage): DataStoreResult<Unit>

        /**
         * Saves a new app package permission.
         *
         * @param permission the app package permission to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a permission with the same ID or
         * (appPackageId, name) pair already exists, or [DataStoreError.ForeignKeyViolation] if an
         * app package with the given app package ID does not exist.
         */
        abstract fun savePermission(permission: AppPackagePermission): DataStoreResult<Unit>
    }

    abstract class AppRepository {
        /**
         * Counts the apps in a given organization.
         *
         * @param organizationId the organization to count apps in.
         * @return the number of apps for the given organization.
         */
        abstract fun countInOrganization(organizationId: String): DataStoreResult<ULong>

        /**
         * Determines whether an app exists.
         *
         * @param id the ID of the app to check existence of.
         * @return whether the app with the given ID exists.
         */
        abstract fun existsById(id: String): DataStoreResult<Boolean>

        /**
         * Finds an existing app.
         *
         * @param id the ID of the app to find.
         * @return the app with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findById(id: String): DataStoreResult<Option<App>>

        /**
         * Finds an existing app.
         *
         * @param id the ID of the app to find.
         * @return the app with the given ID, or [DataStoreError.EntityNotFound] if it doesn't
         * exist.
         */
        fun requireById(id: String): DataStoreResult<App> {
            return findById(id).flatMap { it.toEither { DataStoreError.EntityNotFound } }
        }

        /**
         * Saves a new app along with its default listing.
         *
         * @param app the app to save.
         * @param defaultListing the listing to save as this app's default.
         * @return [DataStoreError.UniqueConstraintViolation] if an app with the same ID already
         * exists, or [DataStoreError.ForeignKeyViolation] if the organization does not exist, the
         * app's default app listing ID doesn't match the app listing's ID, or the app listing's app
         * ID doesn't match the app's ID.
         */
        abstract fun saveWithDefaultListing(
            app: App,
            defaultListing: AppListing,
        ): DataStoreResult<Unit>

        /**
         * Updates the publicly listed status of an app.
         *
         * @param appId the ID of the app to update.
         * @param publiclyListed whether the app should be publicly listed.
         * @return [DataStoreError.EntityNotFound] if the app does not exist.
         */
        abstract fun updatePubliclyListed(
            appId: String,
            publiclyListed: Boolean,
        ): DataStoreResult<Unit>
    }

    /**
     * A ReBAC-based authorization repository for application operations.
     *
     * Authorization repositories read relationships between application resources and determine
     * authorization results for application operations based on these relationships. This
     * authorization model is called relationship-based access control, or ReBAC.
     *
     * Because the authorization repository is tied to an associated [DataStore], it participates
     * in [DataStore.Transaction]s, eliminating the
     * [dual-write problem](https://authzed.com/blog/the-dual-write-problem) which would otherwise
     * need to be solved if using an external data store for resource relationships.
     */
    abstract class AuthorizationRepository {
        /**
         * Checks whether a subject has a given permission on a given resource.
         *
         * @return whether the permission is granted.
         */
        abstract fun hasPermission(request: HasPermissionRequest): DataStoreResult<Boolean>

        /**
         * Saves a new organization owner relationship.
         *
         * If the relationship already exists, this method is a no-op.
         *
         * @param relationship the relationship to save.
         */
        abstract fun saveRelationship(
            relationship: OrganizationOwnerRelationship,
        ): DataStoreResult<Unit>
    }

    abstract class ExternalBlobRepository {
        /**
         * Commits an existing pending external blob, marking it available in blob storage.
         *
         * @param id the ID of the external blob to commit.
         * @param version the version assigned to the blob by the blob storage service.
         * @return [DataStoreError.EntityNotFound] if a blob with the given ID doesn't exist, the
         * blob is not pending, or the provided version type doesn't match the blob's service.
         */
        abstract fun commitPending(
            id: String,
            version: ExternalBlob.BlobVersion,
        ): DataStoreResult<Unit>

        /**
         * Finds an existing external blob.
         *
         * @param id the ID of the external blob to find.
         * @return the blob with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findById(id: String): DataStoreResult<Option<ExternalBlob<*>>>

        /**
         * Marks an existing external blob as deleted so it may later be removed from blob storage.
         *
         * @param id the ID of the external blob to mark as deleted.
         * @param deleteTime the time at which the blob is marked as deleted.
         * @return [DataStoreError.EntityNotFound] if no blob exists with the given ID.
         */
        abstract fun markDeleted(
            id: String,
            deleteTime: OffsetDateTime,
        ): DataStoreResult<Unit>

        /**
         * Finds an existing external blob.
         *
         * @param id the ID of the external blob to find.
         * @return the blob with the given ID, or [DataStoreError.EntityNotFound] if it doesn't
         * exist.
         */
        fun requireById(id: String): DataStoreResult<ExternalBlob<*>> {
            return findById(id).flatMap { it.toEither { DataStoreError.EntityNotFound } }
        }

        /**
         * Finds an existing, committed external blob.
         *
         * @param id the ID of the external blob to find.
         * @return the committed blob with the given ID, or [DataStoreError.EntityNotFound] if it
         * doesn't exist or is not committed.
         */
        fun requireCommittedById(
            id: String,
        ): DataStoreResult<ExternalBlob<ExternalBlob.Status.Committed<*>>> = either {
            when (val blob = requireById(id).bind()) {
                is ExternalBlob.Local -> when (val status = blob.status) {
                    is ExternalBlob.Status.Committed -> ExternalBlob.Local(
                        id = blob.id,
                        createTime = blob.createTime,
                        bucketName = blob.bucketName,
                        objectKey = blob.objectKey,
                        status = status,
                    )

                    ExternalBlob.Status.Pending,
                    is ExternalBlob.Status.Deleted -> raise(DataStoreError.EntityNotFound)
                }

                is ExternalBlob.Gcs -> when (val status = blob.status) {
                    is ExternalBlob.Status.Committed -> ExternalBlob.Gcs(
                        id = blob.id,
                        createTime = blob.createTime,
                        bucketName = blob.bucketName,
                        objectKey = blob.objectKey,
                        status = status,
                    )

                    ExternalBlob.Status.Pending,
                    is ExternalBlob.Status.Deleted -> raise(DataStoreError.EntityNotFound)
                }
            }
        }

        /**
         * Saves a new external blob.
         *
         * @param blob the external blob to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a blob already exists for the
         * blob's ([ExternalBlob.bucketName], [ExternalBlob.objectKey]) pair.
         */
        abstract fun save(blob: ExternalBlob<*>): DataStoreResult<Unit>
    }

    abstract class OrganizationRepository {
        /**
         * Finds an existing organization.
         *
         * @param id the ID of the organization to find.
         * @return the organization with the given ID, or [None] if it doesn't exist.
         */
        abstract fun findById(id: String): DataStoreResult<Option<Organization>>

        /**
         * Saves a new organization.
         *
         * @param organization the organization to save.
         * @return [DataStoreError.UniqueConstraintViolation] if an organization with the same ID
         * already exists.
         */
        abstract fun save(organization: Organization): DataStoreResult<Unit>
    }

    abstract class UserRepository {
        /**
         * Saves a new user.
         *
         * @param user the user to save.
         * @return [DataStoreError.UniqueConstraintViolation] if a user with the same ID already
         * exists.
         */
        abstract fun save(user: User): DataStoreResult<Unit>
    }
}
