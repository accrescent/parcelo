-- Copyright 2024 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

-- First, add the short description column to the listings table with a default value
ALTER TABLE listings ADD COLUMN short_description TEXT DEFAULT '' NOT NULL;

-- Second, remove the default value from the short_description column
PRAGMA foreign_keys = OFF;

-- The previous listings table without the default value on the short_description column
CREATE TABLE listings2 (id INTEGER PRIMARY KEY AUTOINCREMENT, app_id TEXT NOT NULL, locale TEXT NOT NULL, label TEXT NOT NULL, short_description TEXT NOT NULL, CONSTRAINT fk_listings_app_id__id FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE CASCADE ON UPDATE RESTRICT);

INSERT INTO listings2 SELECT * FROM listings;
DROP TABLE listings;
ALTER TABLE listings2 RENAME TO listings;
CREATE UNIQUE INDEX listings_app_id_locale ON listings (app_id, locale);

PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;

-- Migrate the short description column values from the apps table to the listings table
UPDATE listings
    SET short_description = (SELECT short_description FROM apps WHERE apps.id = listings.app_id);

-- Delete the obsolete short description column from the apps table
ALTER TABLE apps DROP COLUMN short_description;
