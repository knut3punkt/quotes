-- Enables the fuzzy-hint tier of cross-source dedup (see ImportStaging.kt). The exact-match tier
-- below needs no extension; it's a plain index on the normalized text.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Mirrors ImportStaging.normalizeQuoteText() in Kotlin, used here only to backfill existing rows.
-- New rows are normalized in application code, not by this function.
CREATE OR REPLACE FUNCTION normalize_quote_text(input TEXT) RETURNS TEXT AS $func$
DECLARE
    result TEXT := input;
BEGIN
    result := regexp_replace(result, '[''‘’‚‛]', '''', 'g');
    result := regexp_replace(result, '["“”„‟]', '"', 'g');
    result := regexp_replace(result, '[‐‑‒–—―]', '-', 'g');
    result := lower(result);
    result := regexp_replace(result, '\s+', ' ', 'g');
    result := trim(both ' ' from result);
    result := regexp_replace(result, '[.,;:!?"''\-\s]+$', '');
    RETURN result;
END;
$func$ LANGUAGE plpgsql IMMUTABLE;

ALTER TABLE quotes ADD COLUMN normalized_text TEXT;
UPDATE quotes SET normalized_text = normalize_quote_text(text);
ALTER TABLE quotes ALTER COLUMN normalized_text SET NOT NULL;

ALTER TABLE imported_quotes ADD COLUMN normalized_text TEXT;
UPDATE imported_quotes SET normalized_text = normalize_quote_text(raw_text);
ALTER TABLE imported_quotes ALTER COLUMN normalized_text SET NOT NULL;

ALTER TABLE imported_quotes ADD COLUMN possible_duplicate_of_id INTEGER REFERENCES imported_quotes (id);

CREATE INDEX idx_quotes_normalized_text ON quotes (normalized_text);
CREATE INDEX idx_imported_quotes_normalized_text ON imported_quotes (normalized_text);

CREATE INDEX idx_quotes_normalized_text_trgm ON quotes USING GIN (normalized_text gin_trgm_ops);
CREATE INDEX idx_imported_quotes_normalized_text_trgm ON imported_quotes USING GIN (normalized_text gin_trgm_ops);
