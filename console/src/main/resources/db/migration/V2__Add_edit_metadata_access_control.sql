-- Copyright 2023 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

ALTER TABLE access_control_lists ADD edit_metadata BOOLEAN DEFAULT 0 NOT NULL;

-- It's okay to grant the edit_metadata permission to all users since we don't support multi-user
-- app management yet.
UPDATE access_control_lists SET edit_metadata = TRUE;
