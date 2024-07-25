-- Copyright 2024 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

-- Add a NOT NULL constraint to the repository_metadata column
PRAGMA foreign_keys = OFF;

-- The previous apps table with the repository_metadata column made NOT NULL
CREATE TABLE apps2 (id TEXT NOT NULL PRIMARY KEY, version_code INT NOT NULL, version_name TEXT NOT NULL, file_id INT NOT NULL, review_issue_group_id INT NULL, updating BOOLEAN DEFAULT 0 NOT NULL, repository_metadata NOT NULL, CONSTRAINT fk_apps_file_id__id FOREIGN KEY (file_id) REFERENCES files(id) ON UPDATE RESTRICT, CONSTRAINT fk_apps_review_issue_group_id__id FOREIGN KEY (review_issue_group_id) REFERENCES review_issue_groups(id) ON UPDATE RESTRICT);

INSERT INTO apps2 SELECT * FROM apps;
DROP TABLE apps;
ALTER TABLE apps2 RENAME TO apps;

PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
