-- User-defined functions

CREATE ALIAS code_point_length
FOR "app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore.codePointLength";
CREATE ALIAS is_nfc_normalized
FOR "app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore.isNfcNormalized";
CREATE ALIAS is_unicode_assigned
FOR "app.accrescent.server.parcelo.adapters.driven.datastore.jdbc.InMemoryDataStore.isUnicodeAssigned";

-- Reusable domain types
CREATE DOMAIN nonempty_can_text
AS varchar
CHECK (VALUE != '' AND is_unicode_assigned(VALUE) AND is_nfc_normalized(VALUE));

CREATE DOMAIN id_text
AS nonempty_can_text
CHECK (code_point_length(VALUE) <= 64 AND REGEXP_LIKE(VALUE, '^[A-Za-z0-9_]*$'));

-- External blobs
CREATE TABLE external_blobs (
    id id_text PRIMARY KEY,
    create_time timestamp with time zone NOT NULL,
    service varchar NOT NULL
        CHECK (ARRAY_CONTAINS(ARRAY['local', 'gcs'], service)),
    status varchar NOT NULL
        CHECK (ARRAY_CONTAINS(
            ARRAY['pending', 'committed', 'deleted'],
            status
        )),
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
);

-- Organizations and users
--
-- organizations.owner_user_id should theoretically be NOT NULL, but an organization and its owner
-- reference each other, and H2 supports neither deferrable foreign key constraints nor INSERT
-- queries in common table expressions, so no statement order can create both rows with the cycle
-- intact. As with apps.default_app_listing_id, this column is therefore kept non-null in practice
-- through careful handling in the DataStore application code. Since this DataStore isn't meant to
-- be used in production, there shouldn't be any significant consequences of this implementation.
CREATE TABLE organizations (
    id id_text PRIMARY KEY,
    owner_user_id varchar,
    create_time timestamp with time zone NOT NULL
);
-- Every user belongs to the single organization they own. The unique constraint on organization_id
-- caps an organization at one member, and the composite foreign key added to organizations below
-- forces that member to be the organization's owner, so the two references are always reciprocal.
CREATE TABLE users (
    id id_text PRIMARY KEY,
    organization_id varchar NOT NULL UNIQUE REFERENCES organizations(id),
    create_time timestamp with time zone NOT NULL,
    UNIQUE (id, organization_id)
);
ALTER TABLE organizations
ADD CONSTRAINT fk_organizations_owner
FOREIGN KEY (owner_user_id, id) REFERENCES users(id, organization_id);

-- App packages and their permissions
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
);
CREATE TABLE app_package_permissions (
    id id_text PRIMARY KEY,
    app_package_id varchar NOT NULL
        REFERENCES app_packages(id) ON DELETE CASCADE,
    name nonempty_can_text NOT NULL,
    max_sdk_version int,
    UNIQUE (app_package_id, name)
);

-- App drafts and their listings
CREATE TABLE app_drafts (
    id id_text PRIMARY KEY,
    organization_id varchar NOT NULL REFERENCES organizations(id),
    create_time timestamp with time zone NOT NULL,
    default_app_draft_listing_id varchar,
    app_package_id varchar REFERENCES app_packages(id),
    submit_time timestamp with time zone,
    CHECK (submit_time IS NULL OR app_package_id IS NOT NULL),
    CHECK (submit_time IS NULL OR default_app_draft_listing_id IS NOT NULL)
);
CREATE TABLE app_draft_listings (
    id id_text PRIMARY KEY,
    app_draft_id varchar NOT NULL
        REFERENCES app_drafts(id) ON DELETE CASCADE,
    language varchar NOT NULL
        CHECK (ARRAY_CONTAINS(ARRAY['en-US'], language)),
    name nonempty_can_text NOT NULL CHECK (code_point_length(name) <= 30),
    short_description nonempty_can_text NOT NULL
        CHECK (code_point_length(short_description) <= 80),
    UNIQUE (app_draft_id, language),
    UNIQUE (app_draft_id, id)
);
ALTER TABLE app_drafts
ADD CONSTRAINT fk_app_drafts_default_listing
FOREIGN KEY (id, default_app_draft_listing_id)
REFERENCES app_draft_listings(app_draft_id, id);

-- Published apps and their listings
--
-- apps.default_app_listing_id should theoretically be NOT NULL. However, H2 does not have either of
-- the features we need to enforce circular references at the schema level, i.e., deferrable foreign
-- key constraints or INSERT queries in common table expressions. Thus, we must keep this column
-- non-null in practice through careful handling in the DataStore application code. Since this
-- DataStore isn't meant to be used in production, there shouldn't be any significant consequences
-- of this implementation.
CREATE TABLE apps (
    id id_text PRIMARY KEY,
    organization_id varchar NOT NULL REFERENCES organizations(id),
    default_app_listing_id varchar,
    publicly_listed boolean NOT NULL
);
CREATE TABLE app_listings (
    id id_text PRIMARY KEY,
    app_id varchar NOT NULL REFERENCES apps(id),
    language varchar NOT NULL
        CHECK (ARRAY_CONTAINS(ARRAY['en-US'], language)),
    UNIQUE (app_id, language),
    UNIQUE (id, app_id)
);
ALTER TABLE apps
ADD CONSTRAINT fk_apps_default_listing
FOREIGN KEY (id, default_app_listing_id) REFERENCES app_listings(app_id, id);

-- Pending uploads
CREATE TABLE pending_app_draft_uploads (
    id id_text PRIMARY KEY,
    app_draft_id varchar NOT NULL UNIQUE
        REFERENCES app_drafts(id) ON DELETE CASCADE,
    external_blob_id varchar NOT NULL REFERENCES external_blobs(id),
    object_key nonempty_can_text NOT NULL UNIQUE,
    create_time timestamp with time zone NOT NULL,
    processing_result varchar
        CHECK (processing_result IS NULL OR ARRAY_CONTAINS(ARRAY[
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
        ], processing_result))
);
CREATE TABLE pending_app_draft_listing_icon_uploads (
    id id_text PRIMARY KEY,
    app_draft_listing_id varchar NOT NULL UNIQUE
        REFERENCES app_draft_listings(id) ON DELETE CASCADE,
    external_blob_id varchar NOT NULL REFERENCES external_blobs(id),
    object_key nonempty_can_text NOT NULL UNIQUE,
    create_time timestamp with time zone NOT NULL,
    processing_result varchar
        CHECK (processing_result IS NULL OR ARRAY_CONTAINS(ARRAY[
            'success',
            'app_draft_submitted',
            'invalid_image',
            'incorrect_image_dimensions'
        ], processing_result))
);
