CREATE SEQUENCE app_draft_acls_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_draft_listing_icon_upload_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_draft_upload_processing_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_edit_acls_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_edit_listing_icon_upload_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_edit_upload_processing_jobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE app_package_permissions_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE organization_acls_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE orphaned_blobs_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE published_apks_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE published_images_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE rejection_reasons_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE app_draft_acls (
    can_delete boolean NOT NULL,
    can_publish boolean NOT NULL,
    can_replace_package boolean NOT NULL,
    can_review boolean NOT NULL,
    can_submit boolean NOT NULL,
    can_update boolean NOT NULL,
    can_view boolean NOT NULL,
    can_view_existence boolean NOT NULL,
    id bigint NOT NULL,
    app_draft_id text NOT NULL,
    user_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_draft_id, user_id)
);

CREATE TABLE app_draft_listing_icon_upload_jobs (
    id bigint NOT NULL,
    app_draft_listing_id uuid NOT NULL,
    background_operation_id text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE app_draft_listings (
    icon_image_id uuid,
    id uuid NOT NULL,
    app_draft_id text NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_draft_id, language)
);

CREATE TABLE app_draft_upload_processing_jobs (
    id bigint NOT NULL,
    app_draft_id text NOT NULL,
    background_operation_id text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE app_drafts (
    publishing boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    published_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    app_package_id uuid,
    review_id uuid,
    default_listing_language text,
    id text NOT NULL,
    organization_id text NOT NULL,
    PRIMARY KEY (id),
    CHECK (app_package_id IS NOT NULL OR submitted_at IS NULL),
    CHECK (default_listing_language IS NOT NULL OR submitted_at IS NULL),
    CHECK (submitted_at IS NOT NULL OR review_id IS NULL),
    CHECK (review_id IS NOT NULL OR publishing = false),
    CHECK (review_id IS NOT NULL OR published_at IS NULL),
    CHECK (publishing = false OR published_at IS NULL)
);

CREATE TABLE app_edit_acls (
    can_review boolean NOT NULL,
    id bigint NOT NULL,
    app_edit_id text NOT NULL,
    user_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_edit_id, user_id)
);

CREATE TABLE app_edit_listing_icon_upload_jobs (
    id bigint NOT NULL,
    app_edit_listing_id uuid NOT NULL,
    background_operation_id text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE app_edit_listings (
    icon_image_id uuid,
    id uuid NOT NULL,
    app_edit_id text NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_edit_id, language)
);

CREATE TABLE app_edit_upload_processing_jobs (
    id bigint NOT NULL,
    app_edit_id text NOT NULL,
    background_operation_id text NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE app_edits (
    expected_app_entity_tag integer NOT NULL,
    publishing boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    published_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    app_package_id uuid NOT NULL,
    review_id uuid,
    app_id text NOT NULL,
    default_listing_language text NOT NULL,
    id text NOT NULL,
    PRIMARY KEY (id),
    CHECK (submitted_at IS NOT NULL OR review_id IS NULL),
    CHECK (submitted_at IS NOT NULL OR publishing = false),
    CHECK (submitted_at IS NOT NULL OR published_at IS NULL),
    CHECK (publishing = false OR published_at IS NULL)
);

CREATE TABLE app_listings (
    icon_image_id uuid NOT NULL,
    app_id text NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    PRIMARY KEY (app_id, language)
);

CREATE TABLE app_package_permissions (
    max_sdk_version integer,
    id bigint NOT NULL,
    app_package_id uuid NOT NULL,
    name text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_package_id, name)
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
    active_edit_limit integer NOT NULL,
    entity_tag integer NOT NULL,
    publicly_listed boolean NOT NULL,
    app_package_id uuid NOT NULL,
    default_listing_language text NOT NULL,
    id text NOT NULL,
    organization_id text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE background_operations (
    succeeded boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id text NOT NULL,
    parent_id text NOT NULL,
    type text NOT NULL CHECK ((type in ('PUBLISH_APP_DRAFT','PUBLISH_APP_EDIT','UPLOAD_APP_DRAFT','UPLOAD_APP_DRAFT_LISTING_ICON','UPLOAD_APP_EDIT','UPLOAD_APP_EDIT_LISTING_ICON'))),
    result bytea,
    PRIMARY KEY (id),
    CHECK (result IS NOT NULL OR succeeded = false)
);

CREATE TABLE images (
    upload_pub_sub_event_time timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE ip_address_salts (
    id boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    current_salt bytea NOT NULL,
    PRIMARY KEY (id),
    CHECK (id = true)
);

CREATE TABLE organization_acls (
    can_create_app_drafts boolean NOT NULL,
    can_edit_apps boolean NOT NULL,
    can_view_apps boolean NOT NULL,
    can_view_organization boolean NOT NULL,
    id bigint NOT NULL,
    organization_id text NOT NULL,
    user_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE organizations (
    active_app_draft_limit integer NOT NULL,
    published_app_limit integer NOT NULL,
    id text NOT NULL,
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
    id bigint NOT NULL,
    image_id uuid NOT NULL,
    bucket_id text NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (bucket_id, object_id)
);

CREATE TABLE rejection_reasons (
    id bigint NOT NULL,
    review_id uuid NOT NULL,
    reason text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE reviews (
    approved boolean NOT NULL,
    id uuid NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE users (
    publisher boolean NOT NULL,
    reviewer boolean NOT NULL,
    email text NOT NULL,
    id text NOT NULL,
    oidc_issuer text NOT NULL,
    oidc_provider text NOT NULL CHECK ((oidc_provider in ('LOCAL','UNKNOWN'))),
    oidc_subject text NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (oidc_issuer, oidc_subject)
);

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

ALTER TABLE IF EXISTS app_draft_listing_icon_upload_jobs
    ADD CONSTRAINT FKnfr45yr7er1bd47omrmecrnv0
    FOREIGN KEY (background_operation_id)
    REFERENCES background_operations;

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

ALTER TABLE IF EXISTS app_draft_upload_processing_jobs
    ADD CONSTRAINT FKo5dgu6m2o5dlf2xk20llhupyc
    FOREIGN KEY (background_operation_id)
    REFERENCES background_operations;

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

ALTER TABLE IF EXISTS app_edit_listing_icon_upload_jobs
    ADD CONSTRAINT FKi2vd4l6ra8i12byx6p9pk6s47
    FOREIGN KEY (app_edit_listing_id)
    REFERENCES app_edit_listings
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_edit_listing_icon_upload_jobs
    ADD CONSTRAINT FKntqcvk4fhk2v48k6yyk1bdijw
    FOREIGN KEY (background_operation_id)
    REFERENCES background_operations;

ALTER TABLE IF EXISTS app_edit_listings
    ADD CONSTRAINT FK8fmdmeiilyxcwql50l0c2b00m
    FOREIGN KEY (app_edit_id)
    REFERENCES app_edits;

ALTER TABLE IF EXISTS app_edit_listings
    ADD CONSTRAINT FKojyernf1xpkkd6tl2aybighet
    FOREIGN KEY (icon_image_id)
    REFERENCES images;

ALTER TABLE IF EXISTS app_edit_upload_processing_jobs
    ADD CONSTRAINT FK23rht5i586wcjjayikthmpbq1
    FOREIGN KEY (app_edit_id)
    REFERENCES app_edits
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS app_edit_upload_processing_jobs
    ADD CONSTRAINT FK9aiah9u0jkutndw3ukx2o2ykk
    FOREIGN KEY (background_operation_id)
    REFERENCES background_operations;

ALTER TABLE IF EXISTS app_edits
    ADD CONSTRAINT FK37f96egr7eyanbvg9rg5af749
    FOREIGN KEY (app_id)
    REFERENCES apps;

ALTER TABLE IF EXISTS app_edits
    ADD CONSTRAINT FKrywe716r3571orcuhljqnhege
    FOREIGN KEY (app_package_id)
    REFERENCES app_packages;

ALTER TABLE IF EXISTS app_edits
    ADD CONSTRAINT FKqqwaj84ip1g1w08qqfghuhmqw
    FOREIGN KEY (review_id)
    REFERENCES reviews;

ALTER TABLE IF EXISTS app_listings
    ADD CONSTRAINT FK5c7or2co40sjmrmj2dlsj3ttt
    FOREIGN KEY (app_id)
    REFERENCES apps;

ALTER TABLE IF EXISTS app_listings
    ADD CONSTRAINT FKgtsrchuvmu7hlhbwa4gvg8cr4
    FOREIGN KEY (icon_image_id)
    REFERENCES images;

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

ALTER TABLE IF EXISTS published_images
    ADD CONSTRAINT FKbnbf68yjoaad3cmsn3lgsejsf
    FOREIGN KEY (image_id)
    REFERENCES images;

ALTER TABLE IF EXISTS rejection_reasons
    ADD CONSTRAINT FKjm7eo74tuqoyxhy4a88obfwxj
    FOREIGN KEY (review_id)
    REFERENCES reviews;
