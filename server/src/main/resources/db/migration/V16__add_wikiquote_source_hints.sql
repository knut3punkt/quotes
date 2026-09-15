-- Auto-extracted from Wikiquote section headings/citations at import time (see
-- wikiquote/SourceHintExtractor.kt), so the admin approve dialog can prefill a source
-- title/year suggestion instead of a reviewer retyping it from raw_payload every time.
ALTER TABLE imported_quotes ADD COLUMN raw_source_title TEXT;
ALTER TABLE imported_quotes ADD COLUMN raw_source_year INTEGER;
