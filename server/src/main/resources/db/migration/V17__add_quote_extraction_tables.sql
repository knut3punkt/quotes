-- Quotable excerpt extraction (docs/features/quote-extraction.md). Two tables, split so run-level
-- provenance (one row per LLM call) isn't duplicated across the excerpts it produced:
--   quote_extraction_attempts: one row per extraction run against a quote, success or failure.
--   quote_excerpts: one row per validated candidate excerpt from a successful attempt.
-- Re-running extraction on a quote deletes its previous attempt (cascading to its excerpts) before
-- inserting the new one, so there is never more than one attempt per quote at a time.

CREATE TABLE quote_extraction_attempts (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    extraction_method TEXT NOT NULL DEFAULT 'llm-unit-selection',
    extraction_method_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    model_id TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL CHECK (status IN ('succeeded', 'failed')),
    excerpt_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- All structurally-valid, deduplicated candidates are kept here, including ones scoring below the
-- acceptance thresholds (meets_thresholds = false) — useful for future threshold tuning. Only rows
-- with meets_thresholds = true should ever be treated as usable by display logic.
CREATE TABLE quote_excerpts (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id INTEGER NOT NULL REFERENCES quote_extraction_attempts (id) ON DELETE CASCADE,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    start_unit INTEGER NOT NULL,
    end_unit INTEGER NOT NULL,
    word_count INTEGER NOT NULL,
    independence_score INTEGER NOT NULL CHECK (independence_score BETWEEN 0 AND 100),
    completeness_score INTEGER NOT NULL CHECK (completeness_score BETWEEN 0 AND 100),
    quotability_score INTEGER NOT NULL CHECK (quotability_score BETWEEN 0 AND 100),
    context_fidelity_score INTEGER NOT NULL CHECK (context_fidelity_score BETWEEN 0 AND 100),
    reason TEXT,
    meets_thresholds BOOLEAN NOT NULL
);

CREATE INDEX idx_quote_excerpts_quote_id ON quote_excerpts (quote_id);
