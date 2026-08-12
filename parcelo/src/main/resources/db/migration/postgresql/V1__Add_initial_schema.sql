-- Reusable domain types

CREATE DOMAIN canon_text
AS text
-- We must use a stable collation for indexed text columns because otherwise collation upgrades can
-- result in corrupt indexes. As per
-- https://www.postgresql.org/docs/18/collation.html#COLLATION-MANAGING-STANDARD, ucs_basic gives
-- the strongest stability guarantee of "stable across all versions", so we use that. It is the
-- safest default in case we add an index to columns later.
COLLATE "ucs_basic"
-- We define the following constraints because:
--
-- * Empty strings are not usually useful.
-- * The W3C recommends NFC form for all content in multiple specifications and the web in
--   particular to avoid interoperability problems
--   (https://www.unicode.org/reports/tr15/tr15-57.html#Norm_Forms).
-- * NFC normalization is only forward-compatible with Unicode upgrades when the text contains only
--   assigned Unicode characters, so we need to forbid unassigned characters to ensure our NFC-only
--   constraint isn't violated after an upgrade. Note that this requirement means that data valid
--   on, e.g., a PostgreSQL 18 instance may not be valid on a PostgreSQL 17 instance because a
--   character in the data may not have been assigned in that past version.
CHECK (VALUE != '' AND VALUE IS NFC NORMALIZED AND unicode_assigned(VALUE));

CREATE DOMAIN id_text
AS canon_text
-- Requirements adapted from https://google.aip.dev/210#unique-identifiers with the hyphen
-- disallowed since it's a selection boundary when double-clicking
CHECK (char_length(VALUE) <= 64 AND VALUE ~ '^[a-zA-Z0-9_]*$');

-- External blobs
--
-- Columns referencing another table use canon_text rather than id_text since the length of a
-- foreign key's value is already restricted at the column it references.
CREATE TABLE external_blobs (
    id id_text PRIMARY KEY,
    create_time timestamp with time zone NOT NULL,
    service canon_text NOT NULL CHECK (service IN ('local', 'gcs')),
    status canon_text NOT NULL CHECK (status IN ('pending', 'committed', 'deleted')),
    bucket_name canon_text NOT NULL CHECK (char_length(bucket_name) <= 256),
    object_key canon_text NOT NULL CHECK (char_length(object_key) <= 256),
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
CREATE TABLE organizations (
    id id_text PRIMARY KEY,
    owner_user_id canon_text NOT NULL,
    create_time timestamp with time zone NOT NULL
);
CREATE TABLE users (
    id id_text PRIMARY KEY,
    organization_id canon_text NOT NULL UNIQUE REFERENCES organizations(id),
    UNIQUE (id, organization_id)
);
ALTER TABLE organizations
    ADD CONSTRAINT organizations_owner_user_fk
    FOREIGN KEY (owner_user_id, id) REFERENCES users(id, organization_id);

-- App packages and their permissions
CREATE TABLE app_packages (
    id id_text PRIMARY KEY,
    external_blob_id canon_text NOT NULL,
    blob_status canon_text NOT NULL GENERATED ALWAYS AS ('committed') STORED,
    upload_event_time timestamp with time zone NOT NULL,
    app_id canon_text NOT NULL CHECK (char_length(app_id) <= 128),
    version_code integer NOT NULL CHECK (version_code BETWEEN 1 AND 2100000000),
    version_name canon_text NOT NULL CHECK (char_length(version_name) <= 1024),
    target_sdk integer NOT NULL CHECK (target_sdk > 0),
    signer_certificate bytea NOT NULL,
    build_apks_result bytea NOT NULL,
    FOREIGN KEY (external_blob_id, blob_status) REFERENCES external_blobs(id, status)
);
CREATE TABLE app_package_permissions (
    id id_text PRIMARY KEY,
    app_package_id canon_text NOT NULL REFERENCES app_packages(id) ON DELETE CASCADE,
    name canon_text NOT NULL CHECK (char_length(name) <= 1024),
    max_sdk_version integer CHECK (max_sdk_version > 0),
    UNIQUE (app_package_id, name)
);

-- App drafts and their listings
CREATE TABLE app_drafts (
    id id_text PRIMARY KEY,
    organization_id canon_text NOT NULL REFERENCES organizations(id),
    create_time timestamp with time zone NOT NULL,
    default_app_draft_listing_id canon_text,
    app_package_id canon_text REFERENCES app_packages(id),
    submit_time timestamp with time zone,
    -- A submitted app draft must have both an app package and a default listing
    CHECK (submit_time IS NULL OR app_package_id IS NOT NULL),
    CHECK (submit_time IS NULL OR default_app_draft_listing_id IS NOT NULL)
);
CREATE TABLE app_draft_listings (
    id id_text PRIMARY KEY,
    app_draft_id canon_text NOT NULL REFERENCES app_drafts(id) ON DELETE CASCADE,
    language canon_text NOT NULL CHECK (language IN ('en-US')),
    name canon_text NOT NULL CHECK (char_length(name) <= 30),
    short_description canon_text NOT NULL CHECK (char_length(short_description) <= 80),
    -- Only one listing is allowed per language per app draft
    UNIQUE (app_draft_id, language),
    -- Required so app drafts can reference a listing's app draft alongside its ID
    UNIQUE (app_draft_id, id)
);
-- Ensure an app draft's default listing 1) points to an actual app draft listing and 2) points to
-- an app draft listing _for this app draft_.
ALTER TABLE app_drafts
    ADD CONSTRAINT app_drafts_default_listing_fk
    FOREIGN KEY (id, default_app_draft_listing_id)
    REFERENCES app_draft_listings(app_draft_id, id);

-- Published apps and their listings
CREATE TABLE apps (
    id id_text PRIMARY KEY,
    organization_id canon_text NOT NULL REFERENCES organizations(id),
    default_app_listing_id canon_text NOT NULL,
    publicly_listed boolean NOT NULL
);
CREATE TABLE app_listings (
    id id_text PRIMARY KEY,
    app_id canon_text NOT NULL REFERENCES apps(id),
    language canon_text NOT NULL CHECK (language IN ('en-US')),
    -- Only one listing is allowed per language per app
    UNIQUE (app_id, language),
    -- Required so apps can reference a listing's app alongside its ID
    UNIQUE (app_id, id)
);
-- Ensure an app's default listing 1) points to an actual app listing and 2) points to an app
-- listing _for this app_
ALTER TABLE apps
    ADD CONSTRAINT apps_default_listing_fk
    FOREIGN KEY (id, default_app_listing_id) REFERENCES app_listings(app_id, id);

-- Pending uploads
CREATE TABLE pending_app_draft_uploads (
    id id_text PRIMARY KEY,
    app_draft_id canon_text NOT NULL UNIQUE REFERENCES app_drafts(id) ON DELETE CASCADE,
    external_blob_id canon_text NOT NULL REFERENCES external_blobs(id),
    object_key canon_text NOT NULL UNIQUE CHECK (char_length(object_key) <= 256),
    create_time timestamp with time zone NOT NULL,
    processing_result canon_text
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
);
CREATE TABLE pending_app_draft_listing_icon_uploads (
    id id_text PRIMARY KEY,
    app_draft_listing_id canon_text NOT NULL UNIQUE
        REFERENCES app_draft_listings(id) ON DELETE CASCADE,
    external_blob_id canon_text NOT NULL REFERENCES external_blobs(id),
    object_key canon_text NOT NULL UNIQUE CHECK (char_length(object_key) <= 256),
    create_time timestamp with time zone NOT NULL,
    processing_result canon_text
        CHECK (processing_result IN (
            'success',
            'app_draft_submitted',
            'invalid_image',
            'incorrect_image_dimensions'
        ))
);
