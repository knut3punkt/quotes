ALTER TABLE authors ADD COLUMN normalized_name TEXT;

UPDATE authors
SET normalized_name = lower(regexp_replace(trim(name), '\s+', ' ', 'g'));

ALTER TABLE authors ALTER COLUMN normalized_name SET NOT NULL;

CREATE UNIQUE INDEX idx_authors_normalized_name ON authors (normalized_name);

ALTER TABLE authors ADD COLUMN wikidata_qid TEXT;

CREATE UNIQUE INDEX idx_authors_wikidata_qid ON authors (wikidata_qid) WHERE wikidata_qid IS NOT NULL;
