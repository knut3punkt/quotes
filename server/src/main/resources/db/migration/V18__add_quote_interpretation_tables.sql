-- Quote interpretation generation (docs/features/quote-interpretations.md). Same two-table shape as
-- V17's extraction tables, so run-level provenance (one row per LLM call) isn't duplicated across the
-- interpretations it produced:
--   quote_interpretation_attempts: one row per generation run against a quote, success or failure.
--   quote_interpretations: one row per validated interpretation from a successful attempt.
-- Re-running generation on a quote deletes its previous attempt (cascading to its interpretations)
-- before inserting the new one, so there is never more than one attempt per quote at a time — the
-- same replace semantics as extraction, chosen for consistency with that existing feature.

CREATE TABLE quote_interpretation_attempts (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    interpretation_method TEXT NOT NULL DEFAULT 'llm-freeform',
    interpretation_method_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    model_id TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL CHECK (status IN ('succeeded', 'failed')),
    interpretation_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- Unlike quote_excerpts, there is no meets_thresholds column: textualSupport/speculativeness are
-- descriptive metadata for the admin UI, not an accept/reject gate, so every validated interpretation
-- is stored and displayed.
CREATE TABLE quote_interpretations (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id INTEGER NOT NULL REFERENCES quote_interpretation_attempts (id) ON DELETE CASCADE,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    lens TEXT NOT NULL,
    interpretation TEXT NOT NULL,
    textual_support INTEGER NOT NULL CHECK (textual_support BETWEEN 0 AND 100),
    speculativeness INTEGER NOT NULL CHECK (speculativeness BETWEEN 0 AND 100)
);

CREATE INDEX idx_quote_interpretations_quote_id ON quote_interpretations (quote_id);
