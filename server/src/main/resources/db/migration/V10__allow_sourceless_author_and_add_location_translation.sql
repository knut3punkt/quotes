-- Scripture quotes (Bible, Quran, ...) have no human author — the book is the source, not the
-- author. A quote must still have an author, a source, or both; that "or both" rule is enforced
-- in ImportedQuoteAdminService, not here, matching how other cross-field validation in this app
-- lives in the service layer rather than as a CHECK constraint.
ALTER TABLE quotes ALTER COLUMN author_id DROP NOT NULL;

-- Per-edition version/translation (e.g. "King James Version"), structured out of what was
-- previously only prose inside sources.title/attribution_text.
ALTER TABLE sources ADD COLUMN translation TEXT;

-- Per-verse/per-chapter locator (e.g. "John 3:16", "2:255"), captured at import time instead of
-- being discarded or left buried inside raw_payload JSON.
ALTER TABLE imported_quotes ADD COLUMN raw_source_location TEXT;
