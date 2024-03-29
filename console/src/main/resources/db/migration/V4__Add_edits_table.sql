-- Copyright 2023 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

-- Generated by Exposed 0.44.1 from the original Edits.kt
CREATE TABLE edits (id BINARY(16) NOT NULL PRIMARY KEY, app_id TEXT NOT NULL, short_description TEXT NULL, creation_time BIGINT NOT NULL, reviewer_id INT NULL, review_id INT NULL, published BOOLEAN DEFAULT 0 NOT NULL, CONSTRAINT fk_edits_app_id__id FOREIGN KEY (app_id) REFERENCES apps(id) ON DELETE CASCADE ON UPDATE RESTRICT, CONSTRAINT fk_edits_reviewer_id__id FOREIGN KEY (reviewer_id) REFERENCES reviewers(id) ON UPDATE RESTRICT, CONSTRAINT fk_edits_review_id__id FOREIGN KEY (review_id) REFERENCES reviews(id) ON UPDATE RESTRICT, CONSTRAINT check_edits_0 CHECK (short_description IS NOT NULL));
