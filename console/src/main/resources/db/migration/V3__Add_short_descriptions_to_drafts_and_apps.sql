-- Copyright 2023 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

-- First, add the "short_description" field to the "drafts" table with a default value
ALTER TABLE drafts ADD short_description TEXT DEFAULT '' NOT NULL;

-- Second, remove the default value from the "short_description" field
PRAGMA foreign_keys = OFF;

-- The previous drafts table without the default value on the "short_description" field
CREATE TABLE drafts2 (id BINARY(16) NOT NULL PRIMARY KEY, app_id TEXT NOT NULL, label TEXT NOT NULL, version_code INT NOT NULL, version_name TEXT NOT NULL, creator_id BINARY(16) NOT NULL, creation_time BIGINT NOT NULL, file_id INT NOT NULL, icon_id INT NOT NULL, reviewer_id INT NULL, review_issue_group_id INT NULL, review_id INT NULL, publishing BOOLEAN DEFAULT 0 NOT NULL, short_description TEXT NOT NULL, CONSTRAINT fk_drafts_creator_id__id FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_drafts_file_id__id FOREIGN KEY (file_id) REFERENCES files(id) ON UPDATE RESTRICT, CONSTRAINT fk_drafts_icon_id__id FOREIGN KEY (icon_id) REFERENCES icons(id) ON UPDATE RESTRICT, CONSTRAINT fk_drafts_reviewer_id__id FOREIGN KEY (reviewer_id) REFERENCES reviewers(id) ON DELETE RESTRICT ON UPDATE RESTRICT, CONSTRAINT fk_drafts_review_issue_group_id__id FOREIGN KEY (review_issue_group_id) REFERENCES review_issue_groups(id) ON UPDATE RESTRICT, CONSTRAINT fk_drafts_review_id__id FOREIGN KEY (review_id) REFERENCES reviews(id) ON UPDATE RESTRICT, CONSTRAINT check_drafts_0 CHECK (NOT ((review_id IS NOT NULL) AND (reviewer_id IS NULL))));

INSERT INTO drafts2 SELECT * from drafts;
DROP TABLE drafts;
ALTER TABLE drafts2 RENAME TO drafts;

PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;

-- Now perform the previous two steps for the "apps" table

ALTER TABLE apps ADD short_description TEXT DEFAULT '' NOT NULL;

PRAGMA foreign_keys = OFF;

-- The previous apps table without the default value on the "short_description" field
CREATE TABLE apps2 (id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, version_code INT NOT NULL, version_name TEXT NOT NULL, file_id INT NOT NULL, icon_id INT NOT NULL, review_issue_group_id INT NULL, updating BOOLEAN DEFAULT 0 NOT NULL, short_description TEXT NOT NULL, CONSTRAINT fk_apps_file_id__id FOREIGN KEY (file_id) REFERENCES files(id) ON UPDATE RESTRICT, CONSTRAINT fk_apps_icon_id__id FOREIGN KEY (icon_id) REFERENCES icons(id) ON UPDATE RESTRICT, CONSTRAINT fk_apps_review_issue_group_id__id FOREIGN KEY (review_issue_group_id) REFERENCES review_issue_groups(id) ON UPDATE RESTRICT);

INSERT INTO apps2 SELECT * from apps;
DROP TABLE apps;
ALTER TABLE apps2 RENAME TO apps;

PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
