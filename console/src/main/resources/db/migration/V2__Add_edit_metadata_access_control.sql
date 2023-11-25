-- Copyright 2023 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

ALTER TABLE access_control_lists ADD edit_metadata BOOLEAN DEFAULT 0 NOT NULL;
