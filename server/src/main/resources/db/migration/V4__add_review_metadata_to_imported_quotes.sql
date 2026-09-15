ALTER TABLE imported_quotes
    ADD COLUMN reviewed_by TEXT,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_note TEXT,
    ADD COLUMN duplicate_of_id INTEGER REFERENCES imported_quotes (id);
