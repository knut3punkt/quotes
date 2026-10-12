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

It is **advisory only**. It never changes what the TV shows by itself. The admin acts on verdicts with the existing
bulk unapprove action. The one exception is opt-in: a human can set a TV preview filter, which limits the TV to
matching quotes until it is cleared (see "TV preview filter").

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

- its runner (`llm`, `external` or `human`);
- method version, prompt version and model id;
- a snapshot of the model settings;
- notes.

Runs are never replaced, so any two can be compared. Re-assessing a subject within a run replaces that one row.

- **`llm` runs.** The server's configured model assesses these (`quality.llm` in `application.conf`), through
  `POST /admin/quality-runs/{id}/assess` from the quotes page's "Assess quality" bulk action. A run made with another
  prompt version or model refuses new work (409), so one run never mixes runners.
- **`external` runs.** Another runner uploads these. The runner follows the same prompt and schema: a frontier model,
  or a human. Uploads go through the same validator and prechecks.
- **`human` runs.** A person rates these with the TV remote (see "Human ratings on TV"): a verdict and optional problem
  flags only, with no reading, insight or levels.

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
- per-problem overlap, with any custom flags a human used after the model's problems;
- the disagreements, largest gap first.

Agreement is not accuracy. The disagreements list is what a human reviews to decide which runner was right.

## Using the results

The quotes list accepts these filters, which match the whole-quote assessment:

- `qualityRun`;
- `qualityVerdict`, multi-valued, including `notAssessed` and `failed`;
- `qualityFlag`, multi-valued, matching model problems, precheck flags, or a custom flag's code.

With a run selected, each row shows the verdicts for the quote and its excerpts. The intended workflow is to filter a
run to `reject` (then `weak`), skim, and bulk-unapprove.

## TV preview filter

To judge a run in the real output, the admin's approved-quotes page can send its current filters to the TV ("Show
these on TV"). While that filter is set, `GET /api/quotes/random` returns only what matches it. Nothing is unapproved
or otherwise changed, and clearing the filter restores the full pool. The TV app needs no change, so a packaged webOS
app follows the filter as it is.

- **Storage.** One row in `tv_quote_filter`, holding the filter as the quote list's query string, without paging.
  `PUT /admin/tv-filter` with `{"query": "..."}` validates and sets it, `GET /admin/tv-filter` returns it (204 when
  none), and `DELETE /admin/tv-filter` clears it. Responses include the run's label and the pool size: how many
  quotes the TV can show, and how many of them as an excerpt.
- **Quote-level filters** (author, source, language, search, provider, confidence, length, tag, enrichment status)
  apply to the quote, as in the list.
- **Quality filters apply to the shown text**, not the whole quote as in the list. An accepted excerpt is shown only if
  its own assessment in the run matches. A quote without accepted excerpts is shown in full only if it is within the
  length cap and its whole-quote assessment matches. A quote with accepted excerpts is never shown in full, so one
  whose excerpts all fail is not shown. The TV's pool can therefore differ from the list's count.
- A stored filter that no longer parses, because the filter parameters changed, is ignored with a warning rather
  than breaking the TV.

The logic is `ShowableQuotes` in `PublicQuoteService.kt`. The quality conditions shared with the list are in
`admin/QualityFilterConditions.kt`.

### TV quality overlay

To see why a quote is on screen, and how runs disagree on it, the same admin bar has a **Show quality on TV** switch
and a picker for up to five runs. While it is on, the TV shows a small panel in a corner: the one that keeps it farthest from the quote as actually
fitted on screen and from the motif icons (nearest of the two first, then the other):
one row per run (oldest first) with the shown text's verdict, the model's problems and the run's label, then the
text's precheck flags once, since they depend only on the text. Like the filter's quality conditions, it follows the
shown text: an excerpt's own assessment, or the whole quote's when it is shown in full. A subject a run never assessed
shows "not assessed" in that run's row.

- **Storage.** One row in `tv_display_settings` (`show_quality`) and the chosen runs in `tv_display_quality_runs`
  (deleting a run drops it from the overlay). `GET/PUT /admin/tv-display` read and set them, with
  `{"showQuality": ..., "qualityRunIds": [...]}`. Turning it on needs at least one run.
- **The TV asks per quote.** `GET /api/quotes/{id}/quality[?excerptId=]` answers 204 while the overlay is off. The TV
  calls it as each quote comes on screen, so switching takes effect on the next quote, without relaunching the app.
- It is diagnostic only and changes no data. `TvDisplayService.kt` on the server, `QualityOverlay.tsx` on the TV.

### Human ratings on TV

The same admin bar has a **Rate on TV into** switch, a picker of `human` runs, and a **New human run** button. Rating
quotes there builds human-labelled data for improving the classifier.

Ratings use the model's verdict scale, so a human run is just another run on the Quality page. The verdict matrix,
kappa and disagreements against any model run need nothing new. A human rating has no levels, so the per-level
comparison skips it.

On the TV, while rating mode is on:

- Auto-advance stops. Quotes change when you rate them or press ← →.
- The quality overlay starts with a row for your rating of the shown text ("unrated" until you rate it) and a key hint.
- The runs chosen for the overlay always show below your row, whether or not you have rated the text yet.
- **OK** opens the rating panel on the edge away from the quote.
  - The top row is the five verdicts. ◀ ▶ choose one and OK saves.
  - ▼ moves to the flag chips below, and OK toggles a chip.
  - The panel opens on your earlier rating, or on acceptable.
  - **Back** closes it without saving. On webOS the remote's Back is `history.back()`, and it offers to quit once there
    is no history left. So the open panel pushes a history entry of its own, Back pops it, and the panel closes on
    `popstate`. Closing the panel any other way pops the entry too, so Back outside the panel behaves as before. The
    same applies to a browser's Back button. Escape closes the panel directly.
- **The pointer works too.** While the LG Magic Remote's pointer is showing, pressing the wheel is a click, not an OK
  key, so clicks are handled as well. A mouse on a PC browser works the same way.
  - A click on the stage opens the panel.
  - A click on a verdict saves it, and a click on a chip toggles it.
  - A click beside the panel closes it.
  - The wheel steps through the verdicts: down moves to a better one, like ▶.
- **1–5** (reject … excellent) save at once, on a remote that has number keys. They keep the flags already set.
- **OK while rating mode is off** shows a hint to turn it on in the admin, but only while the quality overlay is on.
  Plain viewing ignores OK.
- Ticking **Rate on TV** before any human run exists creates one, named with the date.
- **+ New** at the end of the chips opens a text field. On webOS you can use the on-screen keyboard or the remote's voice
  button. The flag is created and selected.
- After saving, a toast confirms and the next quote follows a second later. ◀ goes back, and rating again replaces
  the rating.

Decisions:

- **Validation.** A rating has no reading, insight or levels, so it does not go through `QualityValidator`.
  - `HumanRating` applies the same rules: an unknown verdict or flag is rejected, not dropped.
  - The server computes the subject, its hash and its prechecks from `(quoteId, excerptId)`, as for an upload.
- **Custom flags.** `quality_custom_flags` stores flags beyond the model's problems. Custom flags are never shown to the
  model.
  - A typed label is normalised to an upper-snake `code`, with accents dropped. `problems` stores that code.
  - A label that normalises to a model problem ("cliche") is that problem.
  - A label that normalises to a precheck flag is refused, since the server computes those itself.
- **Unrated first.** While rating mode is on, `GET /api/quotes/random` returns only subjects the human run has not
  rated yet.
  - The TV preview filter still applies, so it can aim the session, for example at quotes a model run called `weak`.
  - Only when nothing unrated is left does it fall back to the usual pool.
  - The TV tops up its batch near the end.
- **No auth.** Rating mode, switched from the admin, is the only guard on the public rating routes. While it is off:
  - `GET /api/quotes/{id}/rating[?excerptId=]` answers 204.
  - `PUT /api/quotes/{id}/rating` (`{"excerptId", "verdict", "problems"}`) answers 409.
  - `POST /api/quality-flags` (`{"label"}`) answers 409.
- **Storage.** The mode is `tv_display_settings.rating_run_id`.
  - `PUT /admin/tv-rating` sets it, with `{"runId": ...}`, or null to turn it off. Only a `human` run can be chosen.
  - `TvRatingService.kt` holds the server side.
  - On the TV, `rating/rating-panel.ts` holds the panel's pure key handling and `RatingPanel.tsx` draws it.

## Related: the TV length cap

Independently of this job, `PublicQuoteService` no longer shows a quote longer than `publicQuotes.maxFullQuoteWords`
(default 55) in full. Such a quote appears only through an accepted excerpt, and is skipped while it has none.
Before this change, 429 quotes over 55 words were shown whole, 169 of them over 100 words.
