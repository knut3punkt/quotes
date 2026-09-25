-- Extends quote interpretation generation to also cover individual excerpts (docs/features/quote-interpretations.md's
-- "Extracted quotations" section). NULL excerpt_id means the row is about the whole quote; a non-null
-- value means it is about that specific quote_excerpts row, interpreted with the parent quote supplied
-- as surrounding context. ON DELETE CASCADE on excerpt_id matters cross-feature: re-running extraction
-- on a quote deletes and replaces its quote_excerpts rows, so any interpretations generated against a
-- now-deleted excerpt are cleaned up automatically rather than left dangling.

ALTER TABLE quote_interpretation_attempts
    ADD COLUMN excerpt_id INTEGER REFERENCES quote_excerpts (id) ON DELETE CASCADE;

ALTER TABLE quote_interpretations
    ADD COLUMN excerpt_id INTEGER REFERENCES quote_excerpts (id) ON DELETE CASCADE;

CREATE INDEX idx_quote_interpretations_excerpt_id ON quote_interpretations (excerpt_id);
