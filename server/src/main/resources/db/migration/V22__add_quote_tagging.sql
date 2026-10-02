-- Quote tagging (docs/features/quote-tagging.md). Replaces V1's placeholder tags/quote_tags tables,
-- which no code ever wrote to, with a faceted, open-vocabulary tag model:
--   tags: one row per tag name within a facet. A merged tag keeps its row, with merged_into_id
--         pointing at the canonical tag, so its name keeps resolving as an alias.
--   quote_tagging_attempts: one row per LLM tagging call (whole quote or one excerpt), success or
--         failure, same shape as quote_interpretation_attempts.
--   quote_tag_assignments: one row per tag on a quote or excerpt. Admin edits are rows too: an
--         admin-added tag has origin 'admin', and an admin-removed tag stays as rejected = true so a
--         re-run never adds it back.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM quote_tags) OR EXISTS (SELECT 1 FROM tags) THEN
        RAISE EXCEPTION 'V22 replaces tags/quote_tags, but they contain rows; migrate that data by hand first';
    END IF;
END $$;

DROP TABLE quote_tags;
DROP TABLE tags;

CREATE TABLE tags (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facet TEXT NOT NULL CHECK (facet IN ('concept', 'mood', 'motif')),
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    breadth TEXT CHECK (breadth IN ('broad', 'specific')),
    merged_into_id INTEGER REFERENCES tags (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL CHECK (created_by IN ('llm', 'admin')),
    UNIQUE (facet, normalized_name),
    CHECK (breadth IS NULL OR facet = 'concept'),
    CHECK (merged_into_id IS NULL OR merged_into_id <> id)
);

CREATE TABLE quote_tagging_attempts (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    excerpt_id INTEGER REFERENCES quote_excerpts (id) ON DELETE CASCADE,
    tagging_method TEXT NOT NULL DEFAULT 'llm-open-vocabulary',
    tagging_method_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    model_id TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL CHECK (status IN ('succeeded', 'failed')),
    tag_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_quote_tagging_attempts_quote_id ON quote_tagging_attempts (quote_id);

-- attempt_id is SET NULL rather than CASCADE: a re-run replaces the quote's attempts, but rejected
-- markers and admin-added rows must outlive the attempt that produced them.
CREATE TABLE quote_tag_assignments (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    excerpt_id INTEGER REFERENCES quote_excerpts (id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    attempt_id INTEGER REFERENCES quote_tagging_attempts (id) ON DELETE SET NULL,
    origin TEXT NOT NULL CHECK (origin IN ('llm', 'admin')),
    relevance INTEGER NOT NULL CHECK (relevance BETWEEN 1 AND 3),
    basis TEXT NOT NULL CHECK (basis IN ('text', 'interpretation')),
    rejected BOOLEAN NOT NULL DEFAULT false
);

-- One row per tag per subject (whole quote = NULL excerpt_id).
CREATE UNIQUE INDEX uq_quote_tag_assignments_subject_tag
    ON quote_tag_assignments (quote_id, COALESCE(excerpt_id, 0), tag_id);
CREATE INDEX idx_quote_tag_assignments_tag_id ON quote_tag_assignments (tag_id);
