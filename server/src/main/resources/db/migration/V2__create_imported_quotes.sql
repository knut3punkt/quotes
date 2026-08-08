CREATE TABLE imported_quotes (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider TEXT NOT NULL,
    provider_quote_id TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    raw_author TEXT,
    raw_payload JSONB NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_status TEXT NOT NULL DEFAULT 'pending'
        CHECK (processing_status IN ('pending', 'approved', 'rejected', 'duplicate')),
    quote_id INTEGER REFERENCES quotes (id),
    UNIQUE (provider, provider_quote_id)
);

CREATE INDEX idx_imported_quotes_processing_status ON imported_quotes (processing_status);
