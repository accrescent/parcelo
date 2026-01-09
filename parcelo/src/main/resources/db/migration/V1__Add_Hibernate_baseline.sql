CREATE SEQUENCE api_keys_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_draft_acls_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_draft_listing_icon_upload_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_draft_upload_processing_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_listings_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_package_permissions_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE organization_acls_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE orphaned_blobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE published_apks_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE publishers_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE rejection_reasons_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE reviewers_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE api_keys (
    id bigint NOT NULL,
    user_id uuid NOT NULL,
    api_key_hash text NOT NULL UNIQUE,
    PRIMARY KEY (id)
);

CREATE TABLE app_draft_acls (
    can_delete boolean NOT NULL,
    can_edit_listings boolean NOT NULL,
    can_publish boolean NOT NULL,
    can_replace_package boolean NOT NULL,
    can_review boolean NOT NULL,
    can_submit boolean NOT NULL,
    can_view boolean NOT NULL,
    can_view_existence boolean NOT NULL,
    id bigint NOT NULL,
    app_draft_id uuid NOT NULL,
    user_id uuid NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_draft_id, user_id)
);

CREATE TABLE app_draft_listing_icon_upload_jobs (
    completed boolean NOT NULL,
    succeeded boolean NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    id bigint NOT NULL,
    app_draft_listing_id uuid NOT NULL,
    upload_key uuid NOT NULL UNIQUE,
    PRIMARY KEY (id)
);

CREATE TABLE app_draft_listings (
    app_draft_id uuid NOT NULL,
    icon_image_id uuid,
    id uuid NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_draft_id, language)
);

CREATE TABLE app_draft_upload_processing_jobs (
    completed boolean NOT NULL,
    succeeded boolean NOT NULL,
    id bigint NOT NULL,
    app_draft_id uuid NOT NULL UNIQUE,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE app_drafts (
    published boolean NOT NULL,
    submitted boolean NOT NULL,
    app_package_id uuid,
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    review_id uuid,
    default_listing_language text,
    PRIMARY KEY (id),
    CHECK (app_package_id IS NOT NULL OR submitted = false),
    CHECK (default_listing_language IS NOT NULL OR submitted = false),
    CHECK (submitted = true OR review_id IS NULL),
    CHECK (review_id IS NOT NULL OR published = false)
);

CREATE TABLE app_listings (
    id bigint NOT NULL,
    icon_published_image_id uuid NOT NULL,
    app_id text NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_id, language)
);

CREATE TABLE app_package_permissions (
    max_sdk_version integer,
    id bigint NOT NULL,
    app_package_id uuid NOT NULL,
    name text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE app_packages (
    target_sdk integer NOT NULL,
    version_code integer NOT NULL,
    upload_pub_sub_event_time timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    app_id text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    version_name text NOT NULL,
    build_apks_result bytea NOT NULL,
    signing_certificate bytea NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE apps (
    app_package_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    default_listing_language text NOT NULL,
    id text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE images (
    id uuid NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE organization_acls (
    can_create_app_drafts boolean NOT NULL,
    can_view_apps boolean NOT NULL,
    can_view_organization boolean NOT NULL,
    id bigint NOT NULL,
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE organizations (
    active_app_draft_limit integer NOT NULL,
    id uuid NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE orphaned_blobs (
    id bigint NOT NULL,
    orphaned_on timestamp(6) with time zone NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE published_apks (
    id bigint NOT NULL,
    size bigint NOT NULL,
    app_package_id uuid NOT NULL,
    apk_path text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_package_id, apk_path),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE published_images (
    id uuid NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE publishers (
    id bigint NOT NULL,
    user_id uuid NOT NULL UNIQUE,
    email text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE rejection_reasons (
    id bigint NOT NULL,
    review_id uuid NOT NULL,
    reason text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE reviewers (
    id bigint NOT NULL,
    user_id uuid NOT NULL UNIQUE,
    email text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE reviews (
    approved boolean NOT NULL,
    id uuid NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE users (
    id uuid NOT NULL,
    identity_provider text NOT NULL,
    scoped_user_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (identity_provider, scoped_user_id)
);

ALTER TABLE IF EXISTS api_keys
    ADD CONSTRAINT FK89d4ddye91twgmx31epv7ro7h
    FOREIGN KEY (user_id)
    REFERENCES users;

ALTER TABLE IF EXISTS app_draft_acls
    ADD CONSTRAINT FK3fwbegdr7onxcbw6psvtfec1i
    FOREIGN KEY (app_draft_id)
    REFERENCES app_drafts
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_draft_acls
    ADD CONSTRAINT FKnjtsi54lkhqtgv7x2hrijtcg5
    FOREIGN KEY (user_id)
    REFERENCES users
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_draft_listing_icon_upload_jobs
    ADD CONSTRAINT FKltf865psu66a39pff7h13nxwk
    FOREIGN KEY (app_draft_listing_id)
    REFERENCES app_draft_listings
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_draft_listings
    ADD CONSTRAINT FK4y08yv4wo0l36pb279jad6veu
    FOREIGN KEY (app_draft_id)
    REFERENCES app_drafts
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_draft_listings
    ADD CONSTRAINT FKdh4u9ypft1e56qcbxgohge32f
    FOREIGN KEY (icon_image_id)
    REFERENCES images;

ALTER TABLE IF EXISTS app_draft_upload_processing_jobs
    ADD CONSTRAINT FK94goh12bgsr2wh6d9k9tj43k9
    FOREIGN KEY (app_draft_id)
    REFERENCES app_drafts
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_drafts
    ADD CONSTRAINT FKewychoo24el8v947ntj9oq59k
    FOREIGN KEY (app_package_id)
    REFERENCES app_packages;

ALTER TABLE IF EXISTS app_drafts
    ADD CONSTRAINT FK1bdshek4dqp4lwrnhhjgmnwfq
    FOREIGN KEY (organization_id)
    REFERENCES organizations;

ALTER TABLE IF EXISTS app_drafts
    ADD CONSTRAINT FK70o7youassq7f5ek48br8nb3x
    FOREIGN KEY (review_id)
    REFERENCES reviews;

ALTER TABLE IF EXISTS app_listings
    ADD CONSTRAINT FK5c7or2co40sjmrmj2dlsj3ttt
    FOREIGN KEY (app_id)
    REFERENCES apps;

ALTER TABLE IF EXISTS app_listings
    ADD CONSTRAINT FKi5ryyusyong79scvwwlwmjhxa
    FOREIGN KEY (icon_published_image_id)
    REFERENCES published_images;

ALTER TABLE IF EXISTS app_package_permissions
    ADD CONSTRAINT FKf7nfokd5ar42vtvc5c4imyh0m
    FOREIGN KEY (app_package_id)
    REFERENCES app_packages
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS apps
    ADD CONSTRAINT FKbs2qx6tx51ytnuybmtk3pk3jx
    FOREIGN KEY (app_package_id)
    REFERENCES app_packages;

ALTER TABLE IF EXISTS apps
    ADD CONSTRAINT FKc61g0npgaeduq3kqa5i7vykc6
    FOREIGN KEY (organization_id)
    REFERENCES organizations;

ALTER TABLE IF EXISTS organization_acls
    ADD CONSTRAINT FK4leq9e6whp3gkko786aj4qcjx
    FOREIGN KEY (organization_id)
    REFERENCES organizations;

ALTER TABLE IF EXISTS organization_acls
    ADD CONSTRAINT FKapi10negj9lqjyeju4ufygpkb
    FOREIGN KEY (user_id)
    REFERENCES users;

ALTER TABLE IF EXISTS published_apks
    ADD CONSTRAINT FK300fv1f2yclf74qsap4dbm5p6
    FOREIGN KEY (app_package_id)
    REFERENCES app_packages;

ALTER TABLE IF EXISTS publishers
    ADD CONSTRAINT FK84mh2dxvfhtrap7uwjxehewg9
    FOREIGN KEY (user_id)
    REFERENCES users
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS rejection_reasons
    ADD CONSTRAINT FKjm7eo74tuqoyxhy4a88obfwxj
    FOREIGN KEY (review_id)
    REFERENCES reviews;

ALTER TABLE IF EXISTS reviewers
    ADD CONSTRAINT FK497vjqxraplr54hsti8w24dh9
    FOREIGN KEY (user_id)
    REFERENCES users
    ON DELETE CASCADE;
