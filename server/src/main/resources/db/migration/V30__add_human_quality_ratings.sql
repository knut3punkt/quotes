-- Human ratings from the TV remote (docs/features/quote-quality.md, "Human ratings on TV").
--   A `human` run holds a person's verdicts (and optional problem flags) for what the TV showed. It has no
--   reading, insight or levels, so it is compared with other runs on verdicts and flags only.
--   tv_display_settings.rating_run_id: while set, the TV lets the remote rate into that human run.
--   quality_custom_flags: problem flags a human added beyond the model's fixed set. `code` is the normalised
--   UPPER_SNAKE form stored in quote_quality_assessments.problems; `label` is how it was first typed.

ALTER TABLE quote_quality_runs DROP CONSTRAINT quote_quality_runs_runner_check;
ALTER TABLE quote_quality_runs ADD CONSTRAINT quote_quality_runs_runner_check
    CHECK (runner IN ('llm', 'external', 'human'));

ALTER TABLE tv_display_settings
    ADD COLUMN rating_run_id INTEGER REFERENCES quote_quality_runs (id) ON DELETE SET NULL;

CREATE TABLE quality_custom_flags (
    code TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
