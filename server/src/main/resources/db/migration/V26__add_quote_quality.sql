-- Quote quality assessment (docs/features/quote-quality.md). Unlike the enrichment tables, earlier runs
-- are never replaced: every run is kept so runs (models, prompt versions) can be compared.
--   quote_quality_runs: one row per run — who/what assessed, with which prompt and settings.
--   quote_quality_assessments: one row per assessed subject (a whole quote or one of its excerpts).
--
-- Assessments outlive the rows they describe: bulk unapprove deletes `quotes` rows and re-extraction
-- deletes `quote_excerpts` rows, so both links are SET NULL and the assessed text is snapshotted.
-- Runs are compared on (subject_kind, subject_hash), which survives both.

CREATE TABLE quote_quality_runs (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label TEXT NOT NULL,
    runner TEXT NOT NULL CHECK (runner IN ('llm', 'external')),
    method_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    model_id TEXT,
    settings JSONB,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quote_quality_assessments (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id INTEGER NOT NULL REFERENCES quote_quality_runs (id) ON DELETE CASCADE,
    quote_id INTEGER REFERENCES quotes (id) ON DELETE SET NULL,
    excerpt_id INTEGER REFERENCES quote_excerpts (id) ON DELETE SET NULL,
    subject_kind TEXT NOT NULL CHECK (subject_kind IN ('quote', 'excerpt')),
    subject_text TEXT NOT NULL,
    subject_hash TEXT NOT NULL,
    word_count INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('succeeded', 'failed')),
    error_message TEXT,
    model_id TEXT,
    reading TEXT,
    insight TEXT,
    assessment TEXT,
    unresolved_references TEXT[] NOT NULL DEFAULT '{}',
    problems TEXT[] NOT NULL DEFAULT '{}',
    stands_alone SMALLINT CHECK (stands_alone BETWEEN 1 AND 5),
    completeness SMALLINT CHECK (completeness BETWEEN 1 AND 5),
    substance SMALLINT CHECK (substance BETWEEN 1 AND 5),
    expression SMALLINT CHECK (expression BETWEEN 1 AND 5),
    verdict TEXT CHECK (verdict IN ('excellent', 'good', 'acceptable', 'weak', 'reject')),
    precheck_flags TEXT[] NOT NULL DEFAULT '{}',
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status = 'failed' OR verdict IS NOT NULL)
);

CREATE UNIQUE INDEX uq_quote_quality_assessments_run_subject
    ON quote_quality_assessments (run_id, subject_kind, subject_hash);
CREATE INDEX idx_quote_quality_assessments_run_quote ON quote_quality_assessments (run_id, quote_id);
