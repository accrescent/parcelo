// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.adapters.driven.datastore.jdbc

import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.toEitherBind
import app.accrescent.server.parcelo.domain.android.AndroidManifest
import app.accrescent.server.parcelo.domain.android.ApkParseError
import app.accrescent.server.parcelo.domain.android.ApkSetParseError
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.NameAttribute
import app.accrescent.server.parcelo.domain.android.SdkVersion
import app.accrescent.server.parcelo.domain.android.VersionCode
import app.accrescent.server.parcelo.domain.android.VersionName
import app.accrescent.server.parcelo.domain.ports.driven.datastore.App
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraft
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftListingIconUploadProcessingResult
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppDraftUploadProcessingError
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppListing
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.AppPackagePermission
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.AppDraftRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.AppPackageRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.AppRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.AuthorizationRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.ExternalBlobRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.OperationRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.OrganizationRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.Transaction
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStore.UserRepository
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStoreError
import app.accrescent.server.parcelo.domain.ports.driven.datastore.DataStoreResult
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ExternalBlob
import app.accrescent.server.parcelo.domain.ports.driven.datastore.HasPermissionRequest
import app.accrescent.server.parcelo.domain.ports.driven.datastore.ListingLanguage
import app.accrescent.server.parcelo.domain.ports.driven.datastore.Operation
import app.accrescent.server.parcelo.domain.ports.driven.datastore.OperationType
import app.accrescent.server.parcelo.domain.ports.driven.datastore.Organization
import app.accrescent.server.parcelo.domain.ports.driven.datastore.OrganizationOwnerRelationship
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftListingIconUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.PendingAppDraftUpload
import app.accrescent.server.parcelo.domain.ports.driven.datastore.User
import app.accrescent.server.parcelo.domain.ports.driven.randomsource.RandomSource
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import arrow.core.some
import org.h2.api.ErrorCode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.text.Normalizer
import java.time.OffsetDateTime

/**
 * Runs the given lambda of JDBC code, converting [SQLException]s from this [DataStore]
 * implementation into appropriate typed errors.
 *
 * @param block the lambda to run in the catching context.
 * @return the return value of [block], or a [DataStoreError] if [block] throws a [SQLException].
 */
private inline fun <T> runCatchingSql(block: Raise<DataStoreError>.() -> T): DataStoreResult<T> {
    return either {
        try {
            block()
        } catch (e: SQLException) {
            raise(e.toDataStoreError())
        }
    }
}

private fun SQLException.toDataStoreError(): DataStoreError {
    return when (sqlState) {
        ErrorCode.CHECK_CONSTRAINT_VIOLATED_1.toString() -> DataStoreError.CheckConstraintViolation
        ErrorCode.DUPLICATE_KEY_1.toString() -> DataStoreError.UniqueConstraintViolation
        ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1.toString(),
        ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1.toString() ->
            DataStoreError.ForeignKeyViolation

        ErrorCode.DEADLOCK_1.toString() -> DataStoreError.SerializationFailure
        else -> DataStoreError.Unknown
    }
}

class InMemoryDataStore private constructor(
    randomSource: RandomSource,
    private val connection: Connection,
) : DataStore(randomSource), AutoCloseable {
    // According to https://www.h2database.com/html/advanced.html#transaction_isolation regarding
    // ANSI SERIALIZABLE transactions:
    //
    // > Note that this isolation level in H2 currently doesn't ensure equivalence of concurrent
    // > and serializable execution of transactions that perform write operations.
    //
    // More specifically, H2's SERIALIZABLE isolation level doesn't detect certain classes of
    // serialization anomalies that a true serializable isolation level would, e.g., G2. Thus, to
    // achieve true serializable transactions, this DataStore implementation holds an exclusive lock
    // to run database transactions. Obviously this approach has huge performance overhead, but
    // since InMemoryDataStore is intended only for testing and development, that's fine.
    private val dbLock = Any()

    // A connection with a failed rollback must be considered broken and cannot be reused because
    // the database is in an unknown state. The typical approach to this scenario is to tell the
    // database connection pool to terminate the connection and replace it later. However, since
    // this DataStore's state is tied to the lifetime of one connection, there is no recovery path
    // for a failed rollback. Thus, we maintain a poisoned flag which is set when any rollback
    // fails, causing all subsequent calls to this DataStore to return an error.
    private var poisoned = false
    private var migrated = false

    // The repositories are stateless apart from the connection, which is fixed for this DataStore's
    // lifetime, so one Transaction instance serves every runInTransaction call. Callers may not use
    // a Transaction outside the scope of the block it was passed to, so sharing it is unobservable.
    private val transaction = InMemoryTransaction(connection)

    override fun migrateToHead(): DataStoreResult<Unit> = synchronized(dbLock) {
        if (poisoned) {
            return@synchronized DataStoreError.IllegalState.left()
        }
        if (migrated) {
            return@synchronized Unit.right()
        }

        // From https://www.h2database.com/html/advanced.html#transaction_isolation:
        //
        // > Please note that most data definition language (DDL) statements, such as "create
        // > table", commit the current transaction.
        //
        // Thus, don't bother running the following statements within a transaction.
        val aliasTarget = InMemoryDataStore::class.java.name
        try {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE ALIAS code_point_length
                    FOR "$aliasTarget.codePointLength"
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE ALIAS is_nfc_normalized
                    FOR "$aliasTarget.isNfcNormalized"
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE ALIAS is_unicode_assigned
                    FOR "$aliasTarget.isUnicodeAssigned"
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE DOMAIN nonempty_can_text
                    AS varchar
                    CHECK (VALUE != '' AND is_unicode_assigned(VALUE) AND is_nfc_normalized(VALUE))
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE DOMAIN id_text
                    AS nonempty_can_text
                    CHECK (code_point_length(VALUE) <= 64 AND REGEXP_LIKE(VALUE, '^[A-Za-z0-9_]*$'))
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE external_blobs (
                        id id_text PRIMARY KEY,
                        create_time timestamp with time zone NOT NULL,
                        service varchar NOT NULL CHECK (service IN ('local', 'gcs')),
                        status varchar NOT NULL
                            CHECK (status IN ('pending', 'committed', 'deleted')),
                        bucket_name nonempty_can_text NOT NULL,
                        object_key nonempty_can_text NOT NULL,
                        generation bigint,
                        meta_generation bigint,
                        delete_time timestamp with time zone,
                        UNIQUE (id, status),
                        UNIQUE (service, bucket_name, object_key),
                        CHECK (status != 'pending' OR generation IS NULL),
                        CHECK (status != 'committed' OR generation IS NOT NULL),
                        CHECK ((service = 'gcs' AND generation IS NOT NULL) = (meta_generation IS NOT NULL)),
                        CHECK ((status = 'deleted') = (delete_time IS NOT NULL))
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE organizations (
                        id id_text PRIMARY KEY,
                        create_time timestamp with time zone NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE app_packages (
                        id id_text PRIMARY KEY,
                        external_blob_id varchar NOT NULL,
                        blob_status varchar NOT NULL GENERATED ALWAYS AS ('committed'),
                        upload_event_time timestamp with time zone NOT NULL,
                        app_id nonempty_can_text NOT NULL,
                        version_code bigint NOT NULL,
                        version_name nonempty_can_text NOT NULL,
                        target_sdk int NOT NULL CHECK (target_sdk > 0),
                        signer_certificate varbinary NOT NULL,
                        build_apks_result varbinary NOT NULL,
                        FOREIGN KEY (external_blob_id, blob_status)
                            REFERENCES external_blobs(id, status)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE app_package_permissions (
                        id id_text PRIMARY KEY,
                        app_package_id varchar NOT NULL
                            REFERENCES app_packages(id) ON DELETE CASCADE,
                        name nonempty_can_text NOT NULL,
                        max_sdk_version int,
                        UNIQUE (app_package_id, name)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE app_drafts (
                        id id_text PRIMARY KEY,
                        organization_id varchar NOT NULL REFERENCES organizations(id),
                        create_time timestamp with time zone NOT NULL,
                        default_app_draft_listing_id varchar,
                        app_package_id varchar REFERENCES app_packages(id),
                        submit_time timestamp with time zone,
                        CHECK (submit_time IS NULL OR app_package_id IS NOT NULL),
                        CHECK (submit_time IS NULL OR default_app_draft_listing_id IS NOT NULL)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE app_draft_listings (
                        id id_text PRIMARY KEY,
                        app_draft_id varchar NOT NULL
                            REFERENCES app_drafts(id) ON DELETE CASCADE,
                        language varchar NOT NULL CHECK (language IN ('en-US')),
                        name nonempty_can_text NOT NULL CHECK (code_point_length(name) <= 30),
                        short_description nonempty_can_text NOT NULL
                            CHECK (code_point_length(short_description) <= 80),
                        UNIQUE (app_draft_id, language),
                        UNIQUE (app_draft_id, id)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    ALTER TABLE app_drafts
                    ADD CONSTRAINT fk_app_drafts_default_listing
                    FOREIGN KEY (id, default_app_draft_listing_id)
                    REFERENCES app_draft_listings(app_draft_id, id)
                    """.trimIndent()
                )
                // apps.default_app_listing_id should theoretically be NOT NULL. However, H2
                // does not have either of the features we need to enforce circular references
                // at the schema level, i.e., deferrable foreign key constraints or INSERT
                // queries in common table expressions. Thus, we must keep this column non-null
                // in practice through careful handling in the DataStore application code. Since
                // this DataStore isn't meant to be used in production, there shouldn't be any
                // significant consequences of this implementation.
                statement.execute(
                    """
                    CREATE TABLE apps (
                        id id_text PRIMARY KEY,
                        organization_id varchar NOT NULL REFERENCES organizations(id),
                        default_app_listing_id varchar,
                        publicly_listed boolean NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE app_listings (
                        id id_text PRIMARY KEY,
                        app_id varchar NOT NULL REFERENCES apps(id),
                        language varchar NOT NULL CHECK (language IN ('en-US')),
                        UNIQUE (app_id, language),
                        UNIQUE (id, app_id)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    ALTER TABLE apps
                    ADD CONSTRAINT fk_apps_default_listing
                    FOREIGN KEY (id, default_app_listing_id) REFERENCES app_listings(app_id, id)
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE pending_app_draft_uploads (
                        id id_text PRIMARY KEY,
                        app_draft_id varchar NOT NULL UNIQUE
                            REFERENCES app_drafts(id) ON DELETE CASCADE,
                        external_blob_id varchar NOT NULL REFERENCES external_blobs(id),
                        object_key nonempty_can_text NOT NULL UNIQUE,
                        create_time timestamp with time zone NOT NULL,
                        processing_result varchar
                            CHECK (processing_result IN (
                                'success',
                                'app_draft_submitted',
                                'apk_set_invalid_format',
                                'apk_set_io_error',
                                'apk_set_no_modern_signature',
                                'apk_set_signed_with_debug_cert',
                                'apk_set_signed_with_multiple_certs',
                                'apk_set_unverified',
                                'apk_set_test_only',
                                'apk_set_debuggable',
                                'apk_set_missing_64_bit_code',
                                'apk_set_low_target_sdk',
                                'apk_set_duplicate_permission',
                                'apk_set_invalid_application_id',
                                'apk_set_multiple_application_elements',
                                'apk_set_multiple_uses_sdk_elements',
                                'apk_set_no_version_code',
                                'apk_set_permission_max_sdk_out_of_range',
                                'apk_set_permission_name_too_long',
                                'apk_set_version_code_out_of_range',
                                'apk_set_version_code_major_non_zero',
                                'apk_set_version_name_too_long'
                            ))
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE pending_app_draft_listing_icon_uploads (
                        id id_text PRIMARY KEY,
                        app_draft_listing_id varchar NOT NULL UNIQUE
                            REFERENCES app_draft_listings(id) ON DELETE CASCADE,
                        external_blob_id varchar NOT NULL REFERENCES external_blobs(id),
                        object_key nonempty_can_text NOT NULL UNIQUE,
                        create_time timestamp with time zone NOT NULL,
                        processing_result varchar
                            CHECK (processing_result IN (
                                'success',
                                'app_draft_submitted',
                                'invalid_image',
                                'incorrect_image_dimensions'
                            ))
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE users (
                        id id_text PRIMARY KEY
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE operations (
                        id id_text PRIMARY KEY,
                        type varchar CHECK (type IN (
                            'app_draft_upload',
                            'app_draft_listing_icon_upload'
                        ))
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE organization_owners (
                        id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        organization_id varchar NOT NULL
                            REFERENCES organizations(id) ON DELETE CASCADE,
                        user_id varchar NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        UNIQUE (organization_id, user_id)
                    )
                    """.trimIndent()
                )
            }
            migrated = true
            Unit.right()
        } catch (e: SQLException) {
            e.toDataStoreError().left()
        }
    }

    override fun <T, E> runInTransaction(
        block: Raise<E>.(Transaction) -> T,
    ): DataStoreResult<Either<E, T>> = synchronized(dbLock) {
        if (poisoned) {
            return@synchronized DataStoreError.IllegalState.left()
        }
        val result = try {
            either { block(transaction) }.right()
        } catch (t: Throwable) {
            t.left()
        }

        when (result) {
            // Block threw, so attempt to roll back, poisoning the connection (which for this
            // DataStore is equivalent to the whole database) if rollback fails since there's no
            // reasonable way to recover. Rethrow the original throwable unconditionally so the
            // caller can handle it.
            is Either.Left -> {
                try {
                    connection.rollback()
                } catch (e: SQLException) {
                    poisoned = true
                    throw result.value.apply { addSuppressed(e) }
                }
                throw result.value
            }

            // Block returned
            is Either.Right -> when (val blockResult = result.value) {
                // Block returned error, so attempt to roll back, poisoning the connection and
                // returning a rollback error if rollback fails
                is Either.Left -> {
                    try {
                        connection.rollback()
                        blockResult.right()
                    } catch (e: SQLException) {
                        poisoned = true
                        e.toDataStoreError().left()
                    }
                }

                // Block succeeded, so attempt to commit, returning the block result if successful.
                // If there's a commit error, attempt to roll back and return the commit error. If
                // rollback fails, poison the connection and return both the commit error and the
                // rollback error.
                is Either.Right -> {
                    try {
                        connection.commit()
                        blockResult.right()
                    } catch (commitException: SQLException) {
                        try {
                            connection.rollback()
                            commitException.toDataStoreError().left()
                        } catch (rollbackException: SQLException) {
                            poisoned = true
                            DataStoreError
                                .RollbackErrorOnCommit(
                                    rollbackError = rollbackException.toDataStoreError(),
                                    commitError = commitException.toDataStoreError(),
                                )
                                .left()
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        synchronized(dbLock) {
            connection.close()
        }
    }

    companion object {
        fun create(randomSource: RandomSource): DataStoreResult<InMemoryDataStore> {
            val connection = try {
                DriverManager.getConnection("jdbc:h2:mem:")
            } catch (e: SQLException) {
                return e.toDataStoreError().left()
            }

            return InMemoryDataStore(randomSource, connection).right()
        }

        // Used by nonempty_can_text definition
        @Suppress("UNUSED")
        @JvmStatic
        fun codePointLength(s: String?): Int? {
            return s?.codePointCount(0, s.length)
        }

        // Used by nonempty_can_text definition
        @Suppress("UNUSED")
        @JvmStatic
        fun isNfcNormalized(s: String?): Boolean? {
            return s?.let { Normalizer.isNormalized(it, Normalizer.Form.NFC) }
        }

        // Used by nonempty_can_text definition
        @Suppress("UNUSED")
        @JvmStatic
        fun isUnicodeAssigned(s: String?): Boolean? {
            return s?.codePoints()?.allMatch { Character.getType(it) != Character.UNASSIGNED.toInt() }
        }
    }
}

private class InMemoryTransaction(connection: Connection) : Transaction {
    override val appDrafts = InMemoryAppDraftRepository(connection)
    override val appPackages = InMemoryAppPackageRepository(connection)
    override val apps = InMemoryAppRepository(connection)
    override val authz = InMemoryAuthorizationRepository(connection)
    override val externalBlobs = InMemoryExternalBlobRepository(connection)
    override val operations = InMemoryOperationRepository(connection)
    override val organizations = InMemoryOrganizationRepository(connection)
    override val users = InMemoryUserRepository(connection)
}

private class InMemoryAppDraftRepository(
    private val connection: Connection,
) : AppDraftRepository() {
    override fun countActiveInOrganization(organizationId: String): DataStoreResult<ULong> =
        runCatchingSql {
            val sql = "SELECT COUNT(1) FROM app_drafts WHERE app_drafts.organization_id = ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, organizationId)
                stmt.executeQuery().use { rs -> rs.getSelectCountResult().bind() }
            }
        }

    override fun deleteById(id: String): DataStoreResult<Unit> = runCatchingSql {
        // Null out the circular FK to app_draft_listings before deleting so that the cascade
        // on app_draft_listings.app_draft_id can proceed without a FK conflict.
        val updateSql = "UPDATE app_drafts SET default_app_draft_listing_id = NULL WHERE id = ?"
        connection.prepareStatement(updateSql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeSingleUpdate().bind()
        }
        connection.prepareStatement("DELETE FROM app_drafts WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun deleteListingById(id: String): DataStoreResult<Unit> = runCatchingSql {
        val sql = "DELETE FROM app_draft_listings WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun deletePendingListingIconUploadByListingId(
        appDraftListingId: String,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "DELETE FROM pending_app_draft_listing_icon_uploads WHERE app_draft_listing_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftListingId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun deletePendingUploadByAppDraftId(
        appDraftId: String,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "DELETE FROM pending_app_draft_uploads WHERE app_draft_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun existsById(id: String): DataStoreResult<Boolean> = runCatchingSql {
        val sql = "SELECT EXISTS(SELECT 1 FROM app_drafts WHERE id = ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun existsSubmittedForAppId(appId: ApplicationId): DataStoreResult<Boolean> =
        runCatchingSql {
            val sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM app_drafts
                    JOIN app_packages
                    ON app_packages.id = app_drafts.app_package_id
                    WHERE app_drafts.submit_time IS NOT NULL
                    AND app_packages.app_id = ?
                )
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, appId.intoInner())
                stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
            }
        }

    override fun findById(id: String): DataStoreResult<Option<AppDraft>> = runCatchingSql {
        val sql = """
            SELECT
                id,
                organization_id,
                create_time,
                default_app_draft_listing_id,
                app_package_id,
                submit_time
            FROM app_drafts
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Some(rs.readAppDraft().bind())
            }
        }
    }

    override fun findForOrganizationAndUserByQuery(
        organizationId: String,
        userId: String,
        maxResults: UInt,
        afterAppDraftId: String?,
    ): DataStoreResult<List<AppDraft>> = runCatchingSql {
        val sql = """
            SELECT
                app_drafts.id,
                app_drafts.organization_id,
                app_drafts.create_time,
                app_drafts.default_app_draft_listing_id,
                app_drafts.app_package_id,
                app_drafts.submit_time
            FROM app_drafts
            JOIN organization_owners
            ON organization_owners.organization_id = app_drafts.organization_id
            WHERE app_drafts.organization_id = ?
            AND organization_owners.user_id = ?
            AND (? IS NULL OR app_drafts.id > ?)
            ORDER BY app_drafts.id
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, organizationId)
            stmt.setString(2, userId)
            stmt.setString(3, afterAppDraftId)
            stmt.setString(4, afterAppDraftId)
            stmt.setLong(5, maxResults.toLong())
            stmt.executeQuery().use { rs ->
                val appDrafts = mutableListOf<AppDraft>()
                while (rs.next()) {
                    appDrafts.add(rs.readAppDraft().bind())
                }
                appDrafts
            }
        }
    }

    override fun findListingById(
        id: String,
    ): DataStoreResult<Option<AppDraftListing>> = runCatchingSql {
        val sql = """
            SELECT id, app_draft_id, language, name, short_description
            FROM app_draft_listings
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Some(rs.readAppDraftListing().bind())
            }
        }
    }

    override fun findListingsForAppDraftAndUserByQuery(
        appDraftId: String,
        userId: String,
        maxResults: UInt,
        afterLanguage: ListingLanguage?,
    ): DataStoreResult<List<AppDraftListing>> = runCatchingSql {
        val sql = """
            SELECT
                app_draft_listings.id,
                app_draft_listings.app_draft_id,
                app_draft_listings.language,
                app_draft_listings.name,
                app_draft_listings.short_description
            FROM app_draft_listings
            JOIN app_drafts
            ON app_drafts.id = app_draft_listings.app_draft_id
            JOIN organization_owners
            ON organization_owners.organization_id = app_drafts.organization_id
            WHERE app_draft_listings.app_draft_id = ?
            AND organization_owners.user_id = ?
            AND (? IS NULL OR app_draft_listings.language > ?)
            ORDER BY app_draft_listings.language
            LIMIT ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftId)
            stmt.setString(2, userId)
            stmt.setString(3, afterLanguage?.toString())
            stmt.setString(4, afterLanguage?.toString())
            stmt.setLong(5, maxResults.toLong())
            stmt.executeQuery().use { rs ->
                val listings = mutableListOf<AppDraftListing>()
                while (rs.next()) {
                    listings.add(rs.readAppDraftListing().bind())
                }
                listings
            }
        }
    }

    override fun findPendingListingIconUploadByObjectKey(
        objectKey: String,
    ): DataStoreResult<Option<PendingAppDraftListingIconUpload>> = runCatchingSql {
        val sql = """
            SELECT
                id,
                app_draft_listing_id,
                external_blob_id,
                object_key,
                create_time,
                processing_result
            FROM pending_app_draft_listing_icon_uploads
            WHERE object_key = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, objectKey)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                PendingAppDraftListingIconUpload(
                    id = rs.requireString("id").bind(),
                    appDraftListingId = rs.requireString("app_draft_listing_id").bind(),
                    externalBlobId = rs.requireString("external_blob_id").bind(),
                    objectKey = rs.requireString("object_key").bind(),
                    createTime = rs.requireObject<OffsetDateTime>("create_time").bind(),
                    result = rs.getSafeString("processing_result")
                        .map {
                            iconProcessingResultFromColumnValue(it)
                                .toEitherBind { DataStoreError.IllegalState }
                        },
                )
                    .some()
            }
        }
    }

    override fun findPendingUploadByObjectKey(
        objectKey: String,
    ): DataStoreResult<Option<PendingAppDraftUpload>> = runCatchingSql {
        val sql = """
            SELECT id, app_draft_id, external_blob_id, object_key, create_time, processing_result
            FROM pending_app_draft_uploads
            WHERE object_key = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, objectKey)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                PendingAppDraftUpload(
                    id = rs.requireString("id").bind(),
                    appDraftId = rs.requireString("app_draft_id").bind(),
                    externalBlobId = rs.requireString("external_blob_id").bind(),
                    objectKey = rs.requireString("object_key").bind(),
                    createTime = rs.requireObject<OffsetDateTime>("create_time").bind(),
                    result = rs.getSafeString("processing_result")
                        .map {
                            processingResultFromColumnValue(it)
                                .toEitherBind { DataStoreError.IllegalState }
                        },
                )
                    .some()
            }
        }
    }

    override fun listingExistsByLanguageForAppDraft(
        appDraftId: String,
        language: ListingLanguage,
    ): DataStoreResult<Boolean> = runCatchingSql {
        val sql = """
            SELECT EXISTS(
                SELECT 1 FROM app_draft_listings WHERE app_draft_id = ? AND language = ?
            )
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftId)
            stmt.setString(2, language.toString())
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun listingExistsByIdForAppDraft(
        listingId: String,
        appDraftId: String,
    ): DataStoreResult<Boolean> = runCatchingSql {
        val sql = "SELECT EXISTS(SELECT 1 FROM app_draft_listings WHERE id = ? AND app_draft_id = ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listingId)
            stmt.setString(2, appDraftId)
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun pendingListingIconUploadExistsByListingId(
        appDraftListingId: String,
    ): DataStoreResult<Boolean> = runCatchingSql {
        val sql = """
            SELECT EXISTS(
                SELECT 1 FROM pending_app_draft_listing_icon_uploads WHERE app_draft_listing_id = ?
            )
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftListingId)
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun pendingUploadExistsByAppDraftId(
        appDraftId: String,
    ): DataStoreResult<Boolean> = runCatchingSql {
        val sql = "SELECT EXISTS(SELECT 1 FROM pending_app_draft_uploads WHERE app_draft_id = ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftId)
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun save(appDraft: AppDraft): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO app_drafts (
                id,
                organization_id,
                create_time,
                default_app_draft_listing_id,
                app_package_id,
                submit_time
            )
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraft.id)
            stmt.setString(2, appDraft.organizationId)
            stmt.setObject(3, appDraft.createTime)
            stmt.setString(4, appDraft.optionalDefaultAppDraftListingId.getOrNull())
            stmt.setString(5, appDraft.optionalAppPackageId.getOrNull())
            stmt.setObject(6, (appDraft as? AppDraft.Submitted)?.submitTime)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun saveListing(listing: AppDraftListing): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO app_draft_listings (id, app_draft_id, language, name, short_description)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, listing.id)
            stmt.setString(2, listing.appDraftId)
            stmt.setString(3, listing.language.toString())
            stmt.setString(4, listing.name)
            stmt.setString(5, listing.shortDescription)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun saveListingIconUpload(
        upload: PendingAppDraftListingIconUpload,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO pending_app_draft_listing_icon_uploads
                (id, app_draft_listing_id, external_blob_id, object_key, create_time, processing_result)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, upload.id)
            stmt.setString(2, upload.appDraftListingId)
            stmt.setString(3, upload.externalBlobId)
            stmt.setString(4, upload.objectKey)
            stmt.setObject(5, upload.createTime)
            stmt.setString(6, upload.result.map { it.toColumnValue() }.getOrNull())
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun saveUpload(upload: PendingAppDraftUpload): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO pending_app_draft_uploads
                (id, app_draft_id, external_blob_id, object_key, create_time, processing_result)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, upload.id)
            stmt.setString(2, upload.appDraftId)
            stmt.setString(3, upload.externalBlobId)
            stmt.setString(4, upload.objectKey)
            stmt.setObject(5, upload.createTime)
            stmt.setString(6, upload.result.map { it.toColumnValue() }.getOrNull())
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updateListing(
        listingId: String,
        name: String?,
        shortDescription: String?,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            UPDATE app_draft_listings
            SET name = COALESCE(?, name), short_description = COALESCE(?, short_description)
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, name, Types.VARCHAR)
            stmt.setObject(2, shortDescription, Types.VARCHAR)
            stmt.setString(3, listingId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updatePendingListingIconUploadResult(
        pendingUploadId: String,
        result: AppDraftListingIconUploadProcessingResult,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql =
            "UPDATE pending_app_draft_listing_icon_uploads SET processing_result = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, result.toColumnValue())
            stmt.setString(2, pendingUploadId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updatePendingUploadResult(
        pendingUploadId: String,
        result: Either<AppDraftUploadProcessingError, Unit>,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE pending_app_draft_uploads SET processing_result = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, result.toColumnValue())
            stmt.setString(2, pendingUploadId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updateAppPackageId(
        appDraftId: String,
        appPackageId: String,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE app_drafts SET app_package_id = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appPackageId)
            stmt.setString(2, appDraftId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updateDefaultListing(
        appDraftId: String,
        defaultAppDraftListingId: Option<String>,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE app_drafts SET default_app_draft_listing_id = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, defaultAppDraftListingId.getOrNull())
            stmt.setString(2, appDraftId)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updateSubmitTime(
        appDraftId: String,
        submitTime: OffsetDateTime,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE app_drafts SET submit_time = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, submitTime)
            stmt.setString(2, appDraftId)
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryAppPackageRepository(
    private val connection: Connection,
) : AppPackageRepository() {
    override fun deleteById(id: String): DataStoreResult<Unit> = runCatchingSql {
        val sql = "DELETE FROM app_packages WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun findById(id: String): DataStoreResult<Option<AppPackage>> = runCatchingSql {
        val sql = """
            SELECT
                id,
                external_blob_id,
                upload_event_time,
                app_id,
                version_code,
                version_name,
                target_sdk,
                signer_certificate,
                build_apks_result
            FROM app_packages
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Some(rs.readAppPackage().bind())
            }
        }
    }

    override fun findByAppDraftId(
        appDraftId: String,
    ): DataStoreResult<Option<AppPackage>> = runCatchingSql {
        val sql = """
            SELECT
                app_packages.id,
                app_packages.external_blob_id,
                app_packages.upload_event_time,
                app_packages.app_id,
                app_packages.version_code,
                app_packages.version_name,
                app_packages.target_sdk,
                app_packages.signer_certificate,
                app_packages.build_apks_result
            FROM app_packages
            JOIN app_drafts
            ON app_drafts.app_package_id = app_packages.id
            WHERE app_drafts.id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appDraftId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Some(rs.readAppPackage().bind())
            }
        }
    }

    override fun findPermissionsForAppPackage(
        appPackageId: String,
    ): DataStoreResult<List<AppPackagePermission>> = runCatchingSql {
        val sql = """
            SELECT id, app_package_id, name, max_sdk_version
            FROM app_package_permissions
            WHERE app_package_id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appPackageId)
            stmt.executeQuery().use { rs ->
                val permissions = mutableListOf<AppPackagePermission>()
                while (rs.next()) {
                    val permission = AppPackagePermission(
                        id = rs.requireString("id").bind(),
                        appPackageId = rs.requireString("app_package_id").bind(),
                        name = rs.requireString("name")
                            .bind()
                            .let(NameAttribute::fromString)
                            .toEitherBind { DataStoreError.IllegalState },
                        maxSdkVersion = rs.getSafeInt("max_sdk_version")
                            .map {
                                SdkVersion
                                    .fromInt(it)
                                    .toEitherBind { DataStoreError.IllegalState }
                            },
                    )
                    permissions.add(permission)
                }
                permissions
            }
        }
    }

    override fun save(appPackage: AppPackage): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO app_packages (
                id,
                external_blob_id,
                upload_event_time,
                app_id,
                version_code,
                version_name,
                target_sdk,
                signer_certificate,
                build_apks_result
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, appPackage.id)
            stmt.setString(2, appPackage.externalBlobId)
            stmt.setObject(3, appPackage.uploadEventTime)
            stmt.setString(4, appPackage.appId.intoInner())
            stmt.setInt(5, appPackage.versionCode.intoInner())
            stmt.setString(6, appPackage.versionName.intoInner())
            stmt.setInt(7, appPackage.targetSdk.intoInner())
            stmt.setBytes(8, appPackage.signerCertificate.value)
            stmt.setBytes(9, appPackage.buildApksResult.value)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun savePermission(
        permission: AppPackagePermission,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO app_package_permissions (id, app_package_id, name, max_sdk_version)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, permission.id)
            stmt.setString(2, permission.appPackageId)
            stmt.setString(3, permission.name.intoInner())
            stmt.setObject(
                4,
                permission.maxSdkVersion.map(SdkVersion::intoInner).getOrNull(),
                Types.INTEGER,
            )
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryAppRepository(private val connection: Connection) : AppRepository() {
    override fun countInOrganization(organizationId: String): DataStoreResult<ULong> =
        runCatchingSql {
            val sql = "SELECT COUNT(1) FROM apps WHERE apps.organization_id = ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, organizationId)
                stmt.executeQuery().use { rs -> rs.getSelectCountResult().bind() }
            }
        }

    override fun existsById(id: String): DataStoreResult<Boolean> = runCatchingSql {
        val sql = "SELECT EXISTS(SELECT 1 FROM apps WHERE id = ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
        }
    }

    override fun findById(id: String): DataStoreResult<Option<App>> = runCatchingSql {
        val sql = """
            SELECT id, organization_id, default_app_listing_id, publicly_listed
            FROM apps
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                App(
                    id = rs.requireString("id").bind(),
                    organizationId = rs.requireString("organization_id").bind(),
                    defaultAppListingId = rs.requireString("default_app_listing_id").bind(),
                    publiclyListed = rs.requireBoolean("publicly_listed").bind()
                )
                    .some()
            }
        }
    }

    override fun saveWithDefaultListing(
        app: App,
        defaultListing: AppListing,
    ): DataStoreResult<Unit> = runCatchingSql {
        if (defaultListing.appId != app.id) {
            raise(DataStoreError.ForeignKeyViolation)
        }

        val appInsertSql = """
            INSERT INTO apps (id, organization_id, default_app_listing_id, publicly_listed)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        val appUpdateSql = "UPDATE apps SET default_app_listing_id = ? WHERE id = ?"
        val listingInsertSql = "INSERT INTO app_listings (id, app_id, language) VALUES (?, ?, ?)"

        connection.prepareStatement(appInsertSql).use { stmt ->
            stmt.setString(1, app.id)
            stmt.setString(2, app.organizationId)
            stmt.setNull(3, Types.VARCHAR)
            stmt.setBoolean(4, app.publiclyListed)
            stmt.executeSingleUpdate().bind()
        }
        connection.prepareStatement(listingInsertSql).use { stmt ->
            stmt.setString(1, defaultListing.id)
            stmt.setString(2, defaultListing.appId)
            stmt.setString(3, defaultListing.language.toString())
            stmt.executeSingleUpdate().bind()
        }
        connection.prepareStatement(appUpdateSql).use { stmt ->
            stmt.setString(1, app.defaultAppListingId)
            stmt.setString(2, app.id)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun updatePubliclyListed(
        appId: String,
        publiclyListed: Boolean,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE apps SET publicly_listed = ? WHERE apps.id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setBoolean(1, publiclyListed)
            stmt.setString(2, appId)
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryAuthorizationRepository(
    private val connection: Connection,
) : AuthorizationRepository() {
    override fun hasPermission(request: HasPermissionRequest): DataStoreResult<Boolean> {
        // Every permission is granted by the same rule: the subject owns the organization the
        // resource belongs to. Only the path from the resource to organization_owners varies, so
        // the request kind selects a query and one binding site serves them all.
        val sql = when (request) {
            is HasPermissionRequest.CreateAppDraft -> """
                SELECT EXISTS(
                    SELECT 1 FROM organizations
                    JOIN organization_owners
                    ON organization_owners.organization_id = organizations.id
                    WHERE organizations.id = ?
                    AND organization_owners.user_id = ?
                )
            """.trimIndent()

            is HasPermissionRequest.UpdateApp,
            is HasPermissionRequest.ViewApp -> """
                SELECT EXISTS(
                    SELECT 1 FROM apps
                    JOIN organization_owners
                    ON organization_owners.organization_id = apps.organization_id
                    WHERE apps.id = ?
                    AND organization_owners.user_id = ?
                )
            """.trimIndent()

            is HasPermissionRequest.CreateAppDraftListing,
            is HasPermissionRequest.DeleteAppDraft,
            is HasPermissionRequest.DownloadAppDraft,
            is HasPermissionRequest.ReplaceAppDraftPackage,
            is HasPermissionRequest.SubmitAppDraft,
            is HasPermissionRequest.UpdateAppDraft,
            is HasPermissionRequest.ViewAppDraft -> """
                SELECT EXISTS(
                    SELECT 1 FROM app_drafts
                    JOIN organization_owners
                    ON organization_owners.organization_id = app_drafts.organization_id
                    WHERE app_drafts.id = ?
                    AND organization_owners.user_id = ?
                )
            """.trimIndent()

            is HasPermissionRequest.DeleteAppDraftListing,
            is HasPermissionRequest.DownloadAppDraftListingIcon,
            is HasPermissionRequest.UpdateAppDraftListing,
            is HasPermissionRequest.UploadAppDraftListingIcon,
            is HasPermissionRequest.ViewAppDraftListing -> """
                SELECT EXISTS(
                    SELECT 1 FROM app_draft_listings
                    JOIN app_drafts
                    ON app_drafts.id = app_draft_listings.app_draft_id
                    JOIN organization_owners
                    ON organization_owners.organization_id = app_drafts.organization_id
                    WHERE app_draft_listings.id = ?
                    AND organization_owners.user_id = ?
                )
            """.trimIndent()
        }

        return runCatchingSql {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, request.resourceId)
                stmt.setString(2, request.subjectId)
                stmt.executeQuery().use { rs -> rs.getSelectExistsResult().bind() }
            }
        }
    }

    override fun saveRelationship(
        relationship: OrganizationOwnerRelationship,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            MERGE INTO organization_owners (organization_id, user_id)
            KEY (organization_id, user_id)
            VALUES (?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, relationship.organizationId)
            stmt.setString(2, relationship.userId)
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryExternalBlobRepository(
    private val connection: Connection,
) : ExternalBlobRepository() {
    override fun findById(id: String): DataStoreResult<Option<ExternalBlob<*>>> = runCatchingSql {
        val sql = """
            SELECT
                id,
                create_time,
                service,
                status,
                bucket_name,
                object_key,
                generation,
                meta_generation,
                delete_time
            FROM external_blobs
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                val blobId = rs.requireString("id").bind()
                val createTime = rs.requireObject<OffsetDateTime>("create_time").bind()
                val service = rs.requireString("service").bind()
                val status = rs.requireString("status").bind()
                val bucketName = rs.requireString("bucket_name").bind()
                val objectKey = rs.requireString("object_key").bind()

                when (service) {
                    "local" -> ExternalBlob.Local(
                        id = blobId,
                        createTime = createTime,
                        bucketName = bucketName,
                        objectKey = objectKey,
                        status = when (status) {
                            "pending" -> ExternalBlob.Status.Pending
                            "committed" -> ExternalBlob.Status.Committed(
                                ExternalBlob.LocalBlobVersion(
                                    rs.requireLong("generation").bind(),
                                ),
                            )

                            "deleted" -> ExternalBlob.Status.Deleted(
                                rs.getSafeLong("generation").map {
                                    ExternalBlob.LocalBlobVersion(it)
                                },
                                rs.requireObject<OffsetDateTime>("delete_time").bind(),
                            )

                            else -> raise(DataStoreError.IllegalState)
                        },
                    )

                    "gcs" -> ExternalBlob.Gcs(
                        id = blobId,
                        createTime = createTime,
                        bucketName = bucketName,
                        objectKey = objectKey,
                        status = when (status) {
                            "pending" -> ExternalBlob.Status.Pending
                            "committed" -> ExternalBlob.Status.Committed(
                                ExternalBlob.GcsBlobVersion(
                                    rs.requireLong("generation").bind(),
                                    rs.requireLong("meta_generation").bind(),
                                ),
                            )

                            "deleted" -> ExternalBlob.Status.Deleted(
                                rs.getSafeLong("generation").map { generation ->
                                    ExternalBlob.GcsBlobVersion(
                                        generation,
                                        rs.requireLong("meta_generation").bind(),
                                    )
                                },
                                rs.requireObject<OffsetDateTime>("delete_time").bind(),
                            )

                            else -> raise(DataStoreError.IllegalState)
                        },
                    )

                    else -> raise(DataStoreError.IllegalState)
                }
                    .some()
            }
        }
    }

    override fun save(blob: ExternalBlob<*>): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            INSERT INTO external_blobs (
                id,
                create_time,
                service,
                status,
                delete_time,
                bucket_name,
                object_key,
                generation,
                meta_generation
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, blob.id)
            stmt.setObject(2, blob.createTime)
            stmt.setString(
                3,
                when (blob) {
                    is ExternalBlob.Gcs -> "gcs"
                    is ExternalBlob.Local -> "local"
                }
            )
            stmt.setString(
                4,
                when (blob.status) {
                    ExternalBlob.Status.Pending -> "pending"
                    is ExternalBlob.Status.Committed -> "committed"
                    is ExternalBlob.Status.Deleted -> "deleted"
                }
            )
            when (val status = blob.status) {
                is ExternalBlob.Status.Deleted -> stmt.setObject(5, status.deleteTime)
                is ExternalBlob.Status.Pending,
                is ExternalBlob.Status.Committed ->
                    stmt.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE)
            }
            stmt.setString(6, blob.bucketName)
            stmt.setString(7, blob.objectKey)
            when (blob) {
                is ExternalBlob.Gcs -> {
                    val version = blob.status.optionalVersion.getOrNull()
                    stmt.setObject(8, version?.generation, Types.BIGINT)
                    stmt.setObject(9, version?.metaGeneration, Types.BIGINT)
                }

                is ExternalBlob.Local -> {
                    val version = blob.status.optionalVersion.getOrNull()
                    stmt.setObject(8, version?.generation, Types.BIGINT)
                    stmt.setNull(9, Types.BIGINT)
                }
            }
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun commitPending(
        id: String,
        version: ExternalBlob.BlobVersion,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = """
            UPDATE external_blobs
            SET status = 'committed', generation = ?, meta_generation = ?
            WHERE id = ? AND status = 'pending' AND service = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            val service = when (version) {
                is ExternalBlob.GcsBlobVersion -> {
                    stmt.setLong(1, version.generation)
                    stmt.setLong(2, version.metaGeneration)
                    "gcs"
                }

                is ExternalBlob.LocalBlobVersion -> {
                    stmt.setLong(1, version.generation)
                    stmt.setNull(2, Types.BIGINT)
                    "local"
                }
            }
            stmt.setString(3, id)
            stmt.setString(4, service)
            stmt.executeSingleUpdate().bind()
        }
    }

    override fun markDeleted(
        id: String,
        deleteTime: OffsetDateTime,
    ): DataStoreResult<Unit> = runCatchingSql {
        val sql = "UPDATE external_blobs SET status = 'deleted', delete_time = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, deleteTime)
            stmt.setString(2, id)
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryOperationRepository(
    private val connection: Connection,
) : OperationRepository() {
    override fun findById(id: String): DataStoreResult<Option<Operation>> = runCatchingSql {
        val sql = """
            SELECT id, type
            FROM operations
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Operation(
                    id = rs.requireString("id").bind(),
                    type = when (rs.requireString("type").bind()) {
                        "app_draft_upload" -> OperationType.APP_DRAFT_UPLOAD
                        "app_draft_listing_icon_upload" ->
                            OperationType.APP_DRAFT_LISTING_ICON_UPLOAD

                        else -> raise(DataStoreError.IllegalState)
                    },
                )
                    .some()
            }
        }
    }

    override fun save(operation: Operation): DataStoreResult<Unit> = runCatchingSql {
        val sql = "INSERT INTO operations (id, type) VALUES (?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, operation.id)
            stmt.setString(
                2,
                when (operation.type) {
                    OperationType.APP_DRAFT_UPLOAD -> "app_draft_upload"
                    OperationType.APP_DRAFT_LISTING_ICON_UPLOAD ->
                        "app_draft_listing_icon_upload"
                }
            )
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryOrganizationRepository(
    private val connection: Connection,
) : OrganizationRepository() {
    override fun findById(id: String): DataStoreResult<Option<Organization>> = runCatchingSql {
        val sql = "SELECT id, create_time FROM organizations WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use None

                Organization(
                    id = rs.requireString("id").bind(),
                    createTime = rs.requireObject<OffsetDateTime>("create_time").bind(),
                )
                    .some()
            }
        }
    }

    override fun save(organization: Organization): DataStoreResult<Unit> = runCatchingSql {
        val sql = "INSERT INTO organizations (id, create_time) VALUES (?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, organization.id)
            stmt.setObject(2, organization.createTime)
            stmt.executeSingleUpdate().bind()
        }
    }
}

private class InMemoryUserRepository(private val connection: Connection) : UserRepository() {
    override fun save(user: User): DataStoreResult<Unit> = runCatchingSql {
        val sql = "INSERT INTO users (id) VALUES (?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, user.id)
            stmt.executeSingleUpdate().bind()
        }
    }
}

/**
 * Reads an [AppDraft] from the current row, which must expose every `app_drafts` column selected by
 * this repository.
 */
private fun ResultSet.readAppDraft(): DataStoreResult<AppDraft> = either {
    val id = requireString("id").bind()
    val organizationId = requireString("organization_id").bind()
    val createTime = requireObject<OffsetDateTime>("create_time").bind()
    val defaultAppDraftListingId = getSafeString("default_app_draft_listing_id")
    val appPackageId = getSafeString("app_package_id")

    when (val submitTime = getSafeObject<OffsetDateTime>("submit_time")) {
        None -> AppDraft.Unsubmitted(
            id,
            organizationId,
            createTime,
            defaultAppDraftListingId,
            appPackageId,
        )

        is Some -> AppDraft.Submitted(
            id,
            organizationId,
            createTime,
            defaultAppDraftListingId.toEitherBind { DataStoreError.IllegalState },
            appPackageId.toEitherBind { DataStoreError.IllegalState },
            submitTime.value,
        )
    }
}

/**
 * Reads an [AppDraftListing] from the current row, which must expose every `app_draft_listings`
 * column selected by this repository.
 */
private fun ResultSet.readAppDraftListing(): DataStoreResult<AppDraftListing> = either {
    AppDraftListing(
        id = requireString("id").bind(),
        appDraftId = requireString("app_draft_id").bind(),
        language = ListingLanguage.fromString(requireString("language").bind())
            .toEitherBind { DataStoreError.IllegalState },
        name = requireString("name").bind(),
        shortDescription = requireString("short_description").bind(),
    )
}

/**
 * Reads an [AppPackage] from the current row, which must expose every `app_packages` column
 * selected by this repository.
 */
private fun ResultSet.readAppPackage(): DataStoreResult<AppPackage> = either {
    AppPackage(
        id = requireString("id").bind(),
        externalBlobId = requireString("external_blob_id").bind(),
        uploadEventTime = requireObject<OffsetDateTime>("upload_event_time").bind(),
        appId = ApplicationId.fromString(requireString("app_id").bind())
            .toEitherBind { DataStoreError.IllegalState },
        versionCode = VersionCode.fromInt(requireInt("version_code").bind())
            .toEitherBind { DataStoreError.IllegalState },
        versionName = VersionName.fromString(requireString("version_name").bind())
            .toEitherBind { DataStoreError.IllegalState },
        targetSdk = SdkVersion.fromInt(requireInt("target_sdk").bind())
            .toEitherBind { DataStoreError.IllegalState },
        signerCertificate = Bytes(requireBytes("signer_certificate").bind()),
        buildApksResult = Bytes(requireBytes("build_apks_result").bind()),
    )
}

private fun Either<AppDraftUploadProcessingError, Unit>.toColumnValue(): String = when (this) {
    is Either.Right -> "success"
    is Either.Left -> when (val error = value) {
        AppDraftUploadProcessingError.AppDraftSubmitted -> "app_draft_submitted"
        is AppDraftUploadProcessingError.ApkSetParseFailed -> error.error.toColumnValue()
    }
}

private fun ApkSetParseError.toColumnValue(): String = when (this) {
    ApkSetParseError.InvalidFormat -> "apk_set_invalid_format"
    ApkSetParseError.Io -> "apk_set_io_error"
    ApkSetParseError.Policy.Missing64BitCode -> "apk_set_missing_64_bit_code"
    ApkSetParseError.Policy.LowTargetSdk -> "apk_set_low_target_sdk"
    is ApkSetParseError.Policy.Apk -> error.toColumnValue()
}

private fun ApkParseError.Policy.toColumnValue(): String = when (this) {
    ApkParseError.Policy.NoModernSignature -> "apk_set_no_modern_signature"
    ApkParseError.Policy.SignedWithDebugCert -> "apk_set_signed_with_debug_cert"
    ApkParseError.Policy.SignedWithMultipleCerts -> "apk_set_signed_with_multiple_certs"
    ApkParseError.Policy.Unverified -> "apk_set_unverified"
    is ApkParseError.Policy.Manifest -> error.toColumnValue()
}

private fun AndroidManifest.FromXmlError.Policy.toColumnValue(): String = when (this) {
    AndroidManifest.FromXmlError.Policy.DuplicatePermission -> "apk_set_duplicate_permission"
    AndroidManifest.FromXmlError.Policy.InvalidApplicationId -> "apk_set_invalid_application_id"
    AndroidManifest.FromXmlError.Policy.DebuggableTrue -> "apk_set_debuggable"
    AndroidManifest.FromXmlError.Policy.TestOnlyTrue -> "apk_set_test_only"
    AndroidManifest.FromXmlError.Policy.MultipleApplicationElements ->
        "apk_set_multiple_application_elements"

    AndroidManifest.FromXmlError.Policy.MultipleUsesSdkElements ->
        "apk_set_multiple_uses_sdk_elements"

    AndroidManifest.FromXmlError.Policy.NoVersionCode -> "apk_set_no_version_code"
    AndroidManifest.FromXmlError.Policy.PermissionMaxSdkOutOfRange ->
        "apk_set_permission_max_sdk_out_of_range"

    AndroidManifest.FromXmlError.Policy.PermissionNameTooLong ->
        "apk_set_permission_name_too_long"

    AndroidManifest.FromXmlError.Policy.VersionCodeOutOfRange ->
        "apk_set_version_code_out_of_range"

    AndroidManifest.FromXmlError.Policy.VersionCodeMajorNonZero ->
        "apk_set_version_code_major_non_zero"

    AndroidManifest.FromXmlError.Policy.VersionNameTooLong -> "apk_set_version_name_too_long"
}

private fun processingResultFromColumnValue(
    value: String,
): Option<Either<AppDraftUploadProcessingError, Unit>> = when (value) {
    "success" -> Some(Unit.right())
    "app_draft_submitted" -> Some(AppDraftUploadProcessingError.AppDraftSubmitted.left())
    else -> apkSetParseErrorFromColumnValue(value)
        .map { AppDraftUploadProcessingError.ApkSetParseFailed(it).left() }
}

private fun apkSetParseErrorFromColumnValue(value: String): Option<ApkSetParseError> =
    when (value) {
        "apk_set_invalid_format" -> Some(ApkSetParseError.InvalidFormat)
        "apk_set_io_error" -> Some(ApkSetParseError.Io)
        "apk_set_missing_64_bit_code" -> Some(ApkSetParseError.Policy.Missing64BitCode)
        "apk_set_low_target_sdk" -> Some(ApkSetParseError.Policy.LowTargetSdk)
        else -> apkParseErrorFromColumnValue(value).map { ApkSetParseError.Policy.Apk(it) }
    }

private fun apkParseErrorFromColumnValue(value: String): Option<ApkParseError.Policy> =
    when (value) {
        "apk_set_no_modern_signature" -> Some(ApkParseError.Policy.NoModernSignature)
        "apk_set_signed_with_debug_cert" -> Some(ApkParseError.Policy.SignedWithDebugCert)
        "apk_set_signed_with_multiple_certs" -> Some(ApkParseError.Policy.SignedWithMultipleCerts)
        "apk_set_unverified" -> Some(ApkParseError.Policy.Unverified)
        else -> manifestParseErrorFromColumnValue(value).map { ApkParseError.Policy.Manifest(it) }
    }

private fun manifestParseErrorFromColumnValue(
    value: String,
): Option<AndroidManifest.FromXmlError.Policy> = when (value) {
    "apk_set_duplicate_permission" ->
        Some(AndroidManifest.FromXmlError.Policy.DuplicatePermission)

    "apk_set_invalid_application_id" ->
        Some(AndroidManifest.FromXmlError.Policy.InvalidApplicationId)

    "apk_set_debuggable" -> Some(AndroidManifest.FromXmlError.Policy.DebuggableTrue)
    "apk_set_test_only" -> Some(AndroidManifest.FromXmlError.Policy.TestOnlyTrue)
    "apk_set_multiple_application_elements" ->
        Some(AndroidManifest.FromXmlError.Policy.MultipleApplicationElements)

    "apk_set_multiple_uses_sdk_elements" ->
        Some(AndroidManifest.FromXmlError.Policy.MultipleUsesSdkElements)

    "apk_set_no_version_code" -> Some(AndroidManifest.FromXmlError.Policy.NoVersionCode)
    "apk_set_permission_max_sdk_out_of_range" ->
        Some(AndroidManifest.FromXmlError.Policy.PermissionMaxSdkOutOfRange)

    "apk_set_permission_name_too_long" ->
        Some(AndroidManifest.FromXmlError.Policy.PermissionNameTooLong)

    "apk_set_version_code_out_of_range" ->
        Some(AndroidManifest.FromXmlError.Policy.VersionCodeOutOfRange)

    "apk_set_version_code_major_non_zero" ->
        Some(AndroidManifest.FromXmlError.Policy.VersionCodeMajorNonZero)

    "apk_set_version_name_too_long" -> Some(AndroidManifest.FromXmlError.Policy.VersionNameTooLong)
    else -> None
}

private fun AppDraftListingIconUploadProcessingResult.toColumnValue(): String = when (this) {
    AppDraftListingIconUploadProcessingResult.Success -> "success"
    AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted -> "app_draft_submitted"
    AppDraftListingIconUploadProcessingResult.Error.InvalidImage -> "invalid_image"
    AppDraftListingIconUploadProcessingResult.Error.IncorrectImageDimensions ->
        "incorrect_image_dimensions"
}

private fun iconProcessingResultFromColumnValue(
    value: String,
): Option<AppDraftListingIconUploadProcessingResult> = when (value) {
    "success" -> Some(AppDraftListingIconUploadProcessingResult.Success)
    "app_draft_submitted" -> Some(AppDraftListingIconUploadProcessingResult.Error.AppDraftSubmitted)
    "invalid_image" -> Some(AppDraftListingIconUploadProcessingResult.Error.InvalidImage)
    "incorrect_image_dimensions" ->
        Some(AppDraftListingIconUploadProcessingResult.Error.IncorrectImageDimensions)

    else -> None
}
