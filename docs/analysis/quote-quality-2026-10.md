# Quote quality analysis, October 2026

Why many quotes in the library are poor, how much of that is the local model (Ministral 3 8B) versus the prompts and
pipeline, and what changed as a result.

## 1. Review of the library

Six reviewers (Claude sub-agents) each took a batch of about 373 of the 2,239 approved quotes. They judged what the TV
actually showed at the time: an accepted excerpt if the quote had one, otherwise the full text.

| Verdict | Quotes | Share |
|---|---|---|
| Good | 647 | 29% |
| OK | 827 | 37% |
| Weak | 485 | 22% |
| Bad | 280 | 13% |

Quality varies a lot by author. Weak and bad quotes are about 12% of the Wilde/Twain batch, 44% of the Orwell batch
and 47% of the Nietzsche/Coelho batch, and most of the Marx quotes.

### Where the poor quality came from

These shares are averages of the six reviewers' estimates.

**The import and bulk approval: about 58%.** No automated step ever looked at quotes of 55 words or fewer. Problems
included:

- citation-only rows ("Bk. I, Ch. IV", "Sec. 125") and Wikiquote's own editorial notes;
- "Variant translation:" prefixes;
- German and French originals;
- list items ending in a comma (ids 1961-1979);
- plot narration and dialogue;
- misattributions that the source itself flags;
- racist and antisemitic letters (Marx; Twain's "Concerning the Jews");
- near-duplicates.

**Pipeline and prompt design: about 24%.**

- Any long quote without an accepted excerpt was shown in full. That was 429 quotes over 55 words, 169 of them over
  100 words, and up to 640 words.
- Segmentation broke on abbreviations ("C.S.", "Adm.", "23:12") and accepted fragments that ended on a semicolon.
- One hard rule failed any excerpt with a listed "unresolved reference".
- A 6-word minimum dropped short famous lines.
- Selector JSON was cut off at 800 tokens, with no retry.
- A failed re-run deleted the last good extraction.

**The model's own judgment: about 18%.**

## 2. Model or prompt? The judge replay

To separate the model from the prompt, 130 stored extraction candidates were replayed through the judge. The sample
was stratified across:

- accepted candidates;
- accepted candidates that open with a dangling word;
- candidates rejected on fidelity;
- candidates rejected on quotability;
- candidates rejected on an unresolved reference.

The replay crossed two models with two prompt versions: {Ministral, Claude} × {judge v3, judge v4}. The data and the
full report are in `server/src/test/resources/judge-replay/`.

| Change | Decisions that change |
|---|---|
| Nothing (Ministral re-run on its own v3 prompt): the noise floor | 12% |
| Model: Ministral to Claude, same prompt (v3 or v4) | about 30% |
| Prompt: Claude, v3 to v4 | 8% |
| Prompt: Ministral, v3 to v4 | 30% |

- **Fidelity.** On 29 of the 30 production fidelity rejections, Claude, using the same prompt, rated fidelity at 4 or
  above. Ministral's "the excerpt omits X" rejections went against its own prompt. The v4 fidelity prompt raised
  agreement between the models on fidelity calls from 66% to 80%.
- **Quotability.** Quotability stayed close to binary for Ministral: about half of all candidates got level 5,
  against 2% for Claude. Agreement between the models was 58% with v3 and 64% with v4. The prompt does not fix this.
- **Against Claude v4 as a reference.** Moving Ministral from v3 to v4 cut missed good excerpts from 24 to 11, but
  raised false accepts from 21 to 28.

**Conclusion.** In extraction, roughly two thirds of the judge's errors come from the model, and one third from the
prompt or pipeline. The prompt-side problems were concentrated in fidelity and in the hard reference rule, and both
are now fixed. Across everything the TV showed, however, the import and the full-text fallback did far more damage than
either.

## 3. The quality job: Ministral against Claude on the same prompt

Both models ran `quote-quality-v1` over every displayable subject: 2,239 quotes plus 472 accepted excerpts. The two
runs hold 2,710 assessments each, one fewer than the subject count because two quotes have identical text.

| Run | Excellent | Good | Acceptable | Weak | Reject |
|---|---|---|---|---|---|
| Ministral 3 8B (run 5) | 1,229 | 156 | 33 | 762 | 530 |
| Claude Opus 5.5 (run 6) | 166 | 828 | 325 | 990 | 401 |

### Agreement

- **Keep/drop** (keep = acceptable or better):
  - The runs agree on 79% of subjects, with kappa 0.58: 81% on whole quotes and 72% on excerpts.
  - Ministral keeps 334 subjects that Claude drops, and drops 235 that Claude keeps.
- **Exact verdict:** 35% agreement. Within one step: 76%. Weighted kappa: 0.55.
- **Ministral "reject"** is reliable: Claude also drops 503 of those 530 subjects (95%).
- **Ministral "excellent"** is not: Claude rates 279 of those 1,229 subjects weak or reject.

### What Ministral gets wrong

- **Offensive generalisations rated excellent.** Example: "...he wants woman, as the most dangerous toy."
- **Fragments rated excellent.** Lines ending on a semicolon, and verbless noun phrases.
- **Editorial and citation text.** Ministral flags `EDITORIAL_OR_META` on 51 subjects; Claude flags it on 202.
- **Wit marked as cliché.** Witty reversals such as "Familiarity breeds contempt — and children." are marked CLICHE
  (286 CLICHE flags, against 59 for Claude).

### Prechecks

The server's deterministic checks are nearly free of false positives. When `CITATION_ONLY`, `EDITORIAL_PREFIX`,
`LOWERCASE_START` or `LIST_FRAGMENT` fires, Claude drops the subject 64 times out of 65.

### What the library looks like now

Claude's verdicts on whole quotes short enough to be shown in full (55 words or fewer, 1,482 quotes):

| Verdict | Quotes | Share |
|---|---|---|
| Reject | 224 | 15% |
| Weak | 304 | 21% |
| Acceptable | 206 | |
| Good | 605 | |
| Excellent | 143 | |

## 4. What changed

1. **TV length cap.** A quote over 55 words without an accepted excerpt is no longer shown at all
   (`publicQuotes.maxFullQuoteWords`).
2. **Deterministic extraction fixes (method v3).**
   - Abbreviation- and initial-aware sentence splitting, and no split inside "23:12".
   - Clause punctuation falls between units, and a candidate may not start at a lowercase unit.
   - The minimum excerpt length is now 3 words.
   - A truncated selector response gets one retry at double the budget.
   - A failed re-run keeps the last successful extraction.
3. **New prompts and acceptance rule.**
   - Selector v6, standalone judge v4 and fidelity judge v3.
   - Fidelity now judges the excerpt itself, not the blind paraphrase.
   - An unresolved reference only fails an excerpt when a deterministic context signal corroborates it.
4. **Quote quality job.**
   - Runs are kept and compared (`quote_quality_runs`, `quote_quality_assessments`).
   - The admin gets an "Assess quality" bulk action, quality filters and a Quality page that compares runs.
   - External runners upload through the API, as Claude's run did.

   See `docs/features/quote-quality.md`.

## 5. Recommendations

1. **Unapprove the reject pile first.** On the quotes page, filter on run 6 (Claude) with verdict `reject`, skim, and
   bulk-unapprove. Then review `weak`.

   If you would rather rely on the local model, filter run 5 on `reject` and on the precheck flags `CITATION_ONLY`,
   `LIST_FRAGMENT`, `LOWERCASE_START` and `EDITORIAL_PREFIX`. Those are about 95% precise. Ministral's keep verdicts
   are not good enough to trust on their own.
2. **Check new imports at import time.** Run the prechecks, and the quality job's reject verdict, on new imports
   before approval. The import is where most of the poor quality enters.
3. **Re-extract only with a stronger judge.** The judge v4 prompts recover good excerpts with Ministral, but also let
   more weak ones through. Point `extraction.judge` at a stronger model before re-extracting the library. Re-extraction
   also removes excerpt interpretations and admin-edited excerpt tags, so re-run tagging afterwards.
4. **Quality prompt v2.** Claude's runners reported that the hard cut-offs were the hardest rules to apply:
   - `TOO_LONG` at about 55 words pushed strong 56-70-word passages down to "weak".
   - Any dangling "he" or opening "For" capped otherwise sharp lines at "weak".
   - An opening ellipsis or lowercase letter forced "reject".

   A v2 could soften these, so that a minor reference lowers `standsAlone` rather than capping the verdict, and length
   could be left to the server's `OVER_WORD_LIMIT` precheck. Then re-run and compare it against run 6.
