-- Excerpt extraction's judge pass (docs/features/quote-extraction.md's "Selector and judge" section).
-- The selector (prompt v4+) no longer scores its own candidates; a separate judge does, so the
-- existing score columns now hold the judge's verdict (1-5 levels mapped to 0/25/50/75/100).
--
--   judge_prompt_version / judge_model_id: provenance for the judge calls, alongside the existing
--     selector prompt_version / model_id. NULL for attempts made before the judge existed.
--   context_signals: phrases flagged as possibly depending on omitted context, by the deterministic
--     check and the selector's own reference list. One phrase per line; NULL when none.
--   judge_notes: the judge's free-text reading (blind reading, insight, unresolved references,
--     meaning in source). Diagnostic only, never shown as part of the quotation.
--   context_fidelity_score becomes nullable: the fidelity check is skipped for candidates that
--     already failed the blind review.

ALTER TABLE quote_extraction_attempts
    ADD COLUMN judge_prompt_version TEXT,
    ADD COLUMN judge_model_id TEXT;

ALTER TABLE quote_excerpts
    ADD COLUMN context_signals TEXT,
    ADD COLUMN judge_notes TEXT,
    ALTER COLUMN context_fidelity_score DROP NOT NULL;
