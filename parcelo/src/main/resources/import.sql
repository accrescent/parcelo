-- SPDX-FileCopyrightText: © 2025 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

-- Sample data which would be inserted when a user logs in for the first time
INSERT INTO organizations (id, app_draft_limit) VALUES ('f2b9003d-c415-47e6-b2aa-bbdd43a2bba1', 3);
INSERT INTO users (id, identity_provider, scoped_user_id)
    VALUES ('e8b542fb-5cf8-4e77-aa88-4b59e7db5ff1', 'github', 'user100');
INSERT INTO organization_acls (
    id,
    organization_id,
    user_id,
    can_create_app_drafts,
    can_view_organization
) VALUES (
    nextval('organization_acls_seq'),
    'f2b9003d-c415-47e6-b2aa-bbdd43a2bba1',
    'e8b542fb-5cf8-4e77-aa88-4b59e7db5ff1',
    true,
    true
);
INSERT INTO api_keys (id, user_id, api_key_hash) VALUES (
    nextval('api_keys_seq'),
    'e8b542fb-5cf8-4e77-aa88-4b59e7db5ff1',
    '90f0e3325b395646fbe14d36721bf23bc3a62f4df47f2e9bb16f2c9ccb071b69'
);
