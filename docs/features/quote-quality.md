# Quote Quality Assessment

## Purpose

Most quotes reach the library through a bulk-approved import, and no automated step checks the ones short enough to
skip excerpt extraction (55 words or fewer). The October 2026 review of all 2,239 approved quotes found:

| Verdict | Share |
|---|---|
| Good | 29% |
| OK | 37% |
| Weak | 22% |
| Bad | 13% |

Most of the bad material came from the import itself:

- citation-only rows ("Bk. I, Ch. IV");
- "Variant translation:" prefixes and Wikiquote editorial notes;
- untranslated originals;
- list fragments ending in a comma;
- plot narration and dialogue;
- misattributions;
- offensive passages;
- near-duplicates.

The quality job gives every quote, and every accepted excerpt, a verdict on how well it works as a TV quote. A human
can then filter on those verdicts and act.

It is **advisory only**. It never changes what the TV shows. The admin acts on verdicts with the existing bulk
unapprove action.

## Subjects

A subject is something the TV can show:

- a whole quote;
- each of the quote's accepted (`meets_thresholds`) excerpts.

An excerpt is assessed on its own, without its parent quote, because that is how it is displayed.

## Assessment

One model call per subject: prompt `prompts/quote-quality-v1.md`, schema in `quality/llm/QuoteQualityResponseSchema.kt`.

The response gives these fields in generation order. The observations come first, so the levels and verdict are
conditioned on the model's own reading.

1. `reading`, `unresolvedReferences`, `insight`.
2. `problems`, from a fixed list:
   - `FRAGMENT`
   - `CONTEXT_DEPENDENT`
   - `NARRATION_OR_DIALOGUE`
   - `EDITORIAL_OR_META`
   - `POSSIBLY_MISATTRIBUTED`
   - `NON_ENGLISH`
   - `FLAT`
   - `CLICHE`
   - `TOO_LONG`
   - `SENSITIVE_OR_DATED`
3. Anchored 1–5 levels:
   - `standsAlone`
   - `completeness`
   - `substance` (the idea)
   - `expression` (the wording)

   Quotability is split into substance and expression because the extraction judge's single quotability level
   collapsed to nearly binary on a small model.
4. `assessment` (one or two sentences).
5. `verdict`: excellent, good, acceptable, weak, or reject.

The prompt sets hard rules that tie problems to verdicts:

| If the subject has... | Its verdict is... |
|---|---|
| editorial text covering most of it, non-English text, or a fragment | reject |
| narration, dialogue, or offensive generalisations | weak at best, usually reject |
| context dependence or excess length | weak at best |
| flatness or cliché | acceptable at best |

When unsure, the model takes the lower verdict.

`QualityValidator` checks every response, whether it came from the local model or was uploaded. Out-of-range levels
and unknown verdicts or problems are rejected, never clamped. A run therefore holds only what its runner actually
answered.

### Prechecks

`QualityPrechecks` computes deterministic flags from the text. The server computes them and the model never sees
them, so prechecks and model judgment can be measured separately:

| Flag | Fires on |
|---|---|
| `OVER_WORD_LIMIT` | more words than `publicQuotes.maxFullQuoteWords` |
| `VERY_SHORT` | fewer than four words |
| `LOWERCASE_START` | text starting with a lowercase letter |
| `TRAILING_CONTINUATION` | text ending in `,` `;` `:` `—`, or with no terminal punctuation |
| `LIST_FRAGMENT` | a trailing comma or a leading bullet |
| `BRACKETED_INSERTION` | `[...]` |
| `ELLIPSIS` | an ellipsis |
| `UNBALANCED_QUOTES` | unmatched quote marks |
| `EDITORIAL_PREFIX` | "Variant translation:", "As quoted in", ... |
| `CITATION_ONLY` | ten words or fewer that look like a chapter or section reference |
| `LIKELY_NON_ENGLISH` | a non-`en` language, or too few English function words |

## Runs

Every assessment belongs to a run (`quote_quality_runs`). A run records:

- its runner (`llm` or `external`);
- method version, prompt version and model id;
- a snapshot of the model settings;
- notes.

Runs are never replaced, so any two can be compared. Re-assessing a subject within a run replaces that one row.

- **`llm` runs.** The server's configured model assesses these (`quality.llm` in `application.conf`), through
  `POST /admin/quality-runs/{id}/assess` from the quotes page's "Assess quality" bulk action. A run made with another
  prompt version or model refuses new work (409), so one run never mixes runners.
- **`external` runs.** Another runner uploads these. The runner follows the same prompt and schema: a frontier model,
  or a human. Uploads go through the same validator and prechecks.

## Schema decisions

`quote_quality_assessments` keeps a snapshot of the assessed text, `subject_text` and its `subject_hash`.

Both of its links, `quote_id` and `excerpt_id`, are `ON DELETE SET NULL`:

- Unapproving a quote deletes its `quotes` row, so the worst cases would otherwise vanish from every comparison.
- Re-extraction deletes and recreates `quote_excerpts` rows.

Runs are matched on `(subject_kind, subject_hash)`, which survives both. Identical text gives the same key, whatever
its row id.

## External runs

1. `POST /admin/quality-runs` with `{"label": ..., "runner": "external", "modelId": ...}`.
2. `GET /admin/quality/prompt` gives the system prompt and response schema verbatim.
3. `GET /admin/quality-runs/{id}/subjects` gives subjects with their exact user message and text hash.
   - It accepts the quote list's filter parameters, plus `onlyMissing=true`, `page` and `pageSize` (at most 500).
4. The runner answers each subject independently.
5. `POST /admin/quality-runs/{id}/assessments` with `{"items": [{quoteId, excerptId, textHash, response}]}`.
   - The response lists stored and rejected items.
   - An item is rejected for an unknown subject, a stale hash (the text changed after it was fetched), or an invalid
     response.

The October 2026 Claude run followed this protocol. Claude Code sub-agents each took a batch of 100 subjects, read the
fetched prompt and schema, wrote JSONL, and the results were uploaded batch by batch.

## Comparing runs

`GET /admin/quality-runs/compare?a=&b=&limit=` and the admin's Quality page compare two runs on the subjects both
assessed successfully. The comparison gives:

- a 5×5 verdict matrix;
- exact and within-one agreement;
- quadratic-weighted kappa;
- a keep/drop 2×2 with its kappa (keep is acceptable or better);
- per-level means;
- per-problem overlap;
- the disagreements, largest gap first.

Agreement is not accuracy. The disagreements list is what a human reviews to decide which runner was right.

## Using the results

The quotes list accepts these filters, which match the whole-quote assessment:

- `qualityRun`;
- `qualityVerdict`, multi-valued, including `notAssessed` and `failed`;
- `qualityFlag`, multi-valued, matching model problems or precheck flags.

With a run selected, each row shows the verdicts for the quote and its excerpts. The intended workflow is to filter a
run to `reject` (then `weak`), skim, and bulk-unapprove.

## Related: the TV length cap

Independently of this job, `PublicQuoteService` no longer shows a quote longer than `publicQuotes.maxFullQuoteWords`
(default 55) in full. Such a quote appears only through an accepted excerpt, and is skipped while it has none.
Before this change, 429 quotes over 55 words were shown whole, 169 of them over 100 words.
