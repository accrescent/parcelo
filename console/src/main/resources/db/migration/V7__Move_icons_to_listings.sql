-- Copyright 2024 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

PRAGMA foreign_keys = OFF;

-- First, add the icon_id column to the listings table with a default (NULL) value
ALTER TABLE listings ADD COLUMN icon_id INT;

-- Second, migrate the icon_id column values from the apps table to the listings table
UPDATE listings SET icon_id = (SELECT icon_id FROM apps WHERE apps.id = listings.app_id);

-- Third, remove the default value from the icon_id column

-- The previous listings table without the default value on the icon_id column (and with the
-- expected foreign key constraints)
CREATE TABLE listings2 (id INTEGER PRIMARY KEY AUTOINCREMENT, app_id TEXT NOT NULL, locale TEXT NOT NULL, label TEXT NOT NULL, short_description TEXT NOT NULL, icon_id INT NOT NULL, CONSTRAINT fk_listings_app_id__id FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_listings_icon_id__id FOREIGN KEY (icon_id) REFERENCES icons(id) ON UPDATE RESTRICT);

INSERT INTO listings2 SELECT * FROM listings;
DROP TABLE listings;
ALTER TABLE listings2 RENAME TO listings;
CREATE UNIQUE INDEX listings_app_id_locale ON listings (app_id, locale);

-- Delete the obsolete icon_id column from the apps table
CREATE TABLE IF NOT EXISTS apps2 (id TEXT NOT NULL PRIMARY KEY, version_code INT NOT NULL, version_name TEXT NOT NULL, file_id INT NOT NULL, review_issue_group_id INT NULL, updating BOOLEAN DEFAULT 0 NOT NULL, CONSTRAINT fk_apps_file_id__id FOREIGN KEY (file_id) REFERENCES files(id) ON UPDATE RESTRICT, CONSTRAINT fk_apps_review_issue_group_id__id FOREIGN KEY (review_issue_group_id) REFERENCES review_issue_groups(id) ON UPDATE RESTRICT);

INSERT INTO apps2
    SELECT id, version_code, version_name, file_id, review_issue_group_id, updating FROM apps;
DROP TABLE apps;
ALTER TABLE apps2 RENAME TO apps;

PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
