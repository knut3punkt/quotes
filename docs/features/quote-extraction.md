# Quotable Excerpt Extraction

## Purpose

The quotes database contains source quotations that are sometimes too long to display comfortably in the TV application.

The extraction feature should identify one or more shorter excerpts from a long source quote that:

* work as independent quotations,
* express a complete and meaningful thought,
* preserve the author's exact wording,
* remain faithful to the meaning of the surrounding source,
* and are suitable for reading on a TV.

This operation is extraction, not summarization or rewriting.

## Core principle

The LLM chooses semantic ranges.

Application code owns the actual text.

The model must not generate the final quotation text from memory. It should return identifiers/ranges referring to units derived deterministically from the original quote.

The application reconstructs the excerpt from the original source text.

This guarantees that an extracted quotation is genuinely present in the source.

---

## Initial length policy

These values should be configurable rather than deeply hard-coded.

Suggested initial defaults:

* Extract only when the source exceeds approximately 55 words.
* Preferred excerpt length: 12–45 words.
* Acceptable excerpt length: 3–60 words. (Originally 6, which dropped "One must imagine Sisyphus happy." before the
  judge ever saw it.)

There is no hard cap on the number of excerpts returned from one source. Deterministic
duplicate/overlap resolution (see below) already prevents redundant or overlapping excerpts, and the
"avoid near-duplicate excerpts" guidance still applies — but the count itself is not artificially
limited to a fixed maximum.

Length is a usability heuristic rather than the primary definition of quality.

A very short excerpt may still be excellent.

Do not pad a good quotation merely to reach a target length.

---

## Extraction pipeline

### 1. Preserve the original text

Keep the source quote unchanged.

Do not normalize the stored source before determining extraction offsets.

Preprocessing may create an auxiliary representation, but excerpts must ultimately be reconstructed against the original source.

### 2. Divide the source into extractable units

Create ordered units with stable numeric IDs.

Prefer sentence boundaries.

If an individual sentence is itself too long for a useful TV quotation, it may additionally be divided at strong clause boundaries such as:

* semicolons,
* colons,
* em dashes,
* or similarly strong punctuation.

Avoid aggressive comma-based splitting unless clearly necessary.

Each unit should retain character offsets into the original source.

Segmentation details (method v3):

* **No split after abbreviations or initials.** A period after an initial ("J.", "C.S.", "(C."), a common title or
  rank ("Mr.", "Adm.", "Capt."), a bibliographic abbreviation ("Ch.", "Bk.", "Vol.") or a scripture book ("Matt.")
  does not end a sentence. Abbreviations that are also ordinary sentence-final words ("no.", "Tim.") are left out.
* **Colons.** A colon is a clause boundary only when followed by whitespace, so "Matthew 23:12" and "3:30" are never
  split.
* **Clause punctuation belongs to no unit.** The `;`, `:` or `—` where a long sentence is split falls in the gap
  between two units. A single-unit excerpt therefore never ends on a dangling semicolon. A range spanning the boundary
  still contains it verbatim, because excerpts are reconstructed from the first unit's start to the last unit's end.
* **Lowercase starts.** A unit that starts with a lowercase letter continues a sentence cut at clause punctuation.
  Validation rejects a candidate that starts with such a unit, unless it is the source's first unit. Such fragments
  were accepted before.

Example:

[1] Human beings desire certainty.
[2] Yet certainty is rarely available.
[3] What matters is learning how to live intelligently within uncertainty.
[4] A mature mind does not require the universe to provide guarantees.

### 3. Ask the LLM to select ranges

The model receives the ordered units.

It may select one unit or a contiguous range of units.

It must never join non-contiguous parts of the source.

Examples of valid selections:

1–1
2–3
3–4

Invalid:

1 + 4

The application reconstructs the selected text from the original character range represented by the first and last selected unit.

### 4. Evaluate semantic independence

A candidate should normally be rejected if understanding it requires material that was removed.

Important failure modes include:

* unresolved pronouns,
* unexplained "this", "that", "these", "such", "it", "they", etc.,
* introductory or transitional sentences,
* answers whose question has been removed,
* conclusions whose argument is required to understand them,
* references such as "as described above",
* missing definitions,
* incomplete comparisons,
* incomplete lists,
* dialogue fragments that require another speaker,
* excerpts whose apparent meaning changes materially when surrounding context is removed.

A pronoun is not automatically disqualifying. Reject it only when its referent is unavailable inside the excerpt.

### 5. Evaluate completeness

The excerpt should express a recognizable complete thought.

Prefer the smallest contiguous range that provides enough context to make the thought complete.

Do not include surrounding prose merely because it is adjacent.

### 6. Evaluate quotability

A technically independent sentence is not necessarily a useful quotation.

Prefer excerpts that contain a substantive observation, claim, insight, image, argument, principle, or memorable formulation.

The working standard: a strong quote expresses a substantial idea in language that can reasonably stand on its own
and is distinctive enough that a reader might deliberately save it, share it, return to it, or reflect on it. The
qualities that point that way are meaning, standalone value, conceptual density (a substantial idea in little
language), distinctive wording, reflective value, relevance beyond the immediate situation, and, as a bonus,
interpretive depth.

A quote does not have to be true, agreeable, inspirational, or morally admirable. Pessimistic, humorous,
controversial, or ambiguous lines can be excellent. Positive-sounding or motivational lines get no credit for tone
alone, and ambiguity counts as depth only when the readings arise naturally from the wording. Judge the words, not
the author's reputation.

Avoid selecting:

* housekeeping prose,
* scene-setting with no independent point,
* bibliographic information,
* transitional statements,
* repetitions,
* generic filler,
* sentences whose only purpose is to connect other sentences,
* clichés, generic advice, and motivational language that merely sounds profound,
* ordinary facts phrased dramatically,
* context-dependent dialogue and punchlines that only work in their original scene,
* vague lines onto which almost any meaning could be projected.

### 7. Evaluate contextual fidelity

Removing surrounding text must not substantially distort the author's apparent meaning.

An excerpt should be rejected if omission makes it reasonably read as asserting something materially different from what the source passage asserts.

The model is evaluating fidelity to the supplied passage, not whether the author's claim is factually correct.

### 8. Multiple excerpts

A source may contain several independently valuable quotations.

Return multiple excerpts only when they express substantially different ideas.

Avoid near-duplicate or heavily overlapping excerpts.

When two candidates communicate essentially the same idea, prefer the stronger and more concise candidate.

### 9. No forced result

Returning no excerpts is valid.

If the passage cannot be shortened without losing necessary context, return an empty result.

Never manufacture an excerpt simply because extraction was requested.

---

## Selector and judge

Extraction runs as two LLM stages with different goals:

1. **Selector (recall).** One call over the numbered units proposes every range that could plausibly work as a
   standalone quotation, including range variants (a short range and the same range extended back to the unit that
   states a referent). It does not score its candidates.
2. **Judge (precision).** Every structurally valid candidate is reviewed separately, and the judge's verdict alone
   decides acceptance.

Why the split: in the original single-call design the model chose a range and then scored it in the same
generation. The schema put the scores after the range and the `reason` last, so the model committed to a choice
before evaluating it and then rationalized it. The self-scores clustered at 85-95 regardless of quality. It also
judged independence with the whole passage in view, so a dangling "this" always *felt* resolved to it (curse of
knowledge). In practice, excerpts that referred back to earlier concepts, or were grammatical but flat, passed the
thresholds.

### Judge pass

For each candidate:

* **Blind call.** The judge sees only the excerpt text, never the source, and reads it as a first-time viewer would.
  It returns, in this order: what the quotation is about, the references it cannot resolve, the general insight
  (empty if none), and then anchored 1-5 levels for `standsAlone`, `completeness`, and `quotability`. Phrases
  flagged by the deterministic context check (below) are included as targeted questions.
* **Fidelity call.** This runs only if the blind call passed. The judge sees the full source and the excerpt, and
  rates on a 1-5 level whether the excerpt, read alone under the author's name, keeps its meaning in the source.

  Up to judge v3, the call also received the blind reading and compared the source against that paraphrase. A
  generalising paraphrase invited "the excerpt is really about X" objections, which rejected faithful aphorisms.

Rules applied in code, regardless of the levels:

* An empty insight fails quotability.
* An unresolved reference fails the excerpt only when it is **corroborated**: the deterministic context check
  (below) flagged the same phrase, as a whole word or phrase. Up to method v2, any listed reference failed the
  excerpt.

Small judges list ordinary nouns, names, "I" and generic "we" as unresolved. Under the corroboration rule, such an
uncorroborated listing counts only through the `standsAlone` level the prompt ties it to.

A malformed judge response drops only that candidate, and this is recorded in the attempt's error message. An
unreachable judge fails the whole attempt.

The judge has its own `extraction.judge` configuration block, so it can run on a different (typically stronger,
slower) model than the selector.

### Deterministic context signals

Before judging, application code flags phrases that suggest a candidate depends on omitted context:

* a backward-pointing first word (a connective such as "But" or "Thus", or a pronoun or demonstrative such as "This"
  or "It") when the excerpt does not start the source,
* phrases such as "the latter" or "as mentioned",
* and any reference whose referent unit the selector itself placed outside the range.

These are hints, not verdicts: per "Evaluate semantic independence" above, a pronoun is not automatically
disqualifying. They are passed to the blind judge and stored for review.

### Overlap resolution

Overlapping range variants are kept until after judging. They are then resolved by ranking passing excerpts first,
then by summed judge score, then shorter range, then lower start unit.

---

## LLM scoring

Scores now come from the judge (see "Selector and judge"). The judge answers in anchored 1-5 levels rather than 0-100,
because small local models used the 0-100 scale inconsistently. Levels are stored as 0/25/50/75/100 in the four
existing score columns, and the default thresholds of 75 mean "level 4 or better" on every dimension.
`contextualFidelity` is null when the fidelity call was skipped.

The original 0-100 definitions below still describe what each dimension measures.

For each proposed excerpt, return integer scores from 0 to 100 for:

### independence

How understandable the excerpt is without omitted surrounding text.

### completeness

How fully it expresses a coherent thought.

### quotability

How well it functions as a meaningful standalone quotation.

### contextualFidelity

How safely it preserves the meaning conveyed by the original passage.

Scores are useful metadata and diagnostics, not ground truth.

Initial application-side acceptance thresholds were approximately the following (superseded by the judge's
level-4-or-better defaults, 75 on each dimension):

* independence >= 80
* completeness >= 80
* contextualFidelity >= 85
* quotability >= 65

These thresholds should remain configurable and should be tuned against an evaluation set.

---

## Model input

The runtime prompt should provide numbered units rather than asking the model to reproduce source text.

Example user content:

SOURCE UNITS

[1] ...
[2] ...
[3] ...

Select zero or more quotable standalone excerpts according to the extraction rules.

---

## Runtime system prompt — version 1

You identify short, independently quotable excerpts inside longer source quotations.

You are performing extraction, not rewriting.

The source has been divided into numbered consecutive units. Select only contiguous ranges of these units.

Do not:

* rewrite or paraphrase the author,
* add words,
* combine non-contiguous units,
* manufacture a quotation when none works,
* select a fragment whose meaning depends materially on omitted context.

A good excerpt:

* expresses a complete thought,
* is understandable without the omitted text,
* preserves the meaning of the source passage,
* contains enough context to resolve important references,
* is concise,
* and is meaningful enough to stand as a quotation.

Prefer the smallest range that preserves a complete independent thought.

Several excerpts may be returned when they express genuinely different ideas.

Returning zero excerpts is correct when no suitable standalone excerpt exists.

Judge contextual fidelity using only the supplied source. Do not judge whether the author's underlying belief is true.

Return only the structured response required by the response schema.

---

## Runtime system prompt — version 2

Same as version 1, plus explicit scoring-scale guidance inserted before the final line. Version 1 relied only on
the JSON schema's per-field `description`s to convey the 0-100 scale; at least one local model (a small quantized
model) ignored that and answered every score with a bare 0 or 1, as though the fields were booleans. Version 2 adds
the following paragraph to the prompt itself, directly before "Return only the structured response...":

For each excerpt, score independence, completeness, quotability, and contextualFidelity as integers on a 0-to-100
scale, not a 0-to-1 scale. These are not yes/no or true/false judgments: 0 or 1 alone is almost always the wrong
answer. Use the full range to reflect real differences in quality — for example, a strong excerpt typically scores
somewhere in the 70-100 range on each dimension, a mediocre one in the 40-70 range, and a poor one well below 40.
Two excerpts of different quality should receive different scores, not identical ones.

Both prompt versions remain available as classpath resources for provenance; extracted excerpts record which
prompt version produced them.

## Runtime system prompt — version 3

Same as version 2, but the scoring paragraph now defines what each of the four scores measures, and specifically
clarifies quotability. Version 2 fixed the 0-100 scale but a model was then observed scoring an angry personal
rebuke (understandable and complete, but with no idea beyond its own dispute) highly on quotability purely because
it was vivid and dramatic. Version 3 adds:

* one-line definitions of independence, completeness, quotability, and contextualFidelity, distinguishing them from
  each other;
* an explicit statement that quotability rewards a generalizable observation, claim, insight, or principle, not
  intensity or emotion;
* a contrastive example: an angry rebuke ("You have made a terrible mistake...") should score low on quotability
  despite being complete and independent, while a general principle ("The measure of a person is not how they avoid
  mistakes...") should score high.

All prompt versions remain available as classpath resources for provenance; extracted excerpts record which prompt
version produced them.

## Runtime system prompt — version 4 (selector) and judge prompts v1

Version 4 turns the selection prompt into a recall-oriented candidate generator for the split described in "Selector
and judge". It:

* tells the model that a strict reviewer filters afterwards, so it should include borderline candidates rather than
  omit them;
* drops self-scoring entirely;
* asks for the references each candidate relies on, with the unit where each referent is stated;
* asks for range variants extended back to an antecedent;
* includes one worked example with its exact JSON.

The judge prompts are `excerpt-judge-standalone-vN.md` (blind) and `excerpt-judge-fidelity-vN.md`, versioned
together as `excerpt-judge-vN`. The standalone prompt carries the strict quotability bar:

* a quote-card test;
* anchored level descriptions in which "sensible but flat" is explicitly level 3, which fails;
* calibration examples for flat prose, dangling references, and angry outbursts.

Attempts record both the selector prompt version and the judge prompt version.

Judge v1 → v2 fixed over-strictness seen in the eval on Ministral 3 8B:

* The blind judge listed ordinary general concepts ("good fortune", "ambition", "a community of interpreters") as
  unresolved references, which the hard rule then rejected. v2 limits `unresolvedReferences` to phrases that point
  back to one specific thing the reader was not shown, using a "which one?" test and explicit counter-examples.
* The fidelity judge treated "leaves out the author's wider argument" as unfaithful. v2 states that omission alone
  is not a fidelity problem; only a reversed, distorted, or overstated claim is.

On the eval corpus, v2 raised good ranges accepted from 5 to 6 of 6. Bad ranges accepted stayed at 0.

## Runtime system prompt — selector version 5 and judge prompts v3

These versions spell out what makes a good quote, using the standard in "Evaluate quotability". The mechanics stay
the same.

Selector v5 replaces the short "promising candidate" list with:

* the qualities to prefer,
* the passages to avoid,
* an explicit note that a quote need not be true, agreeable, or inspirational.

It keeps the recall bias ("when uncertain, err slightly toward inclusion"). Units, references, range variants,
`coreIdea`, and the worked example are unchanged.

In judge v3, only the standalone prompt changed. The fidelity prompt is still `excerpt-judge-fidelity-v2.md`. The
standalone prompt adds:

* the core standard and the dimensions to weigh;
* what not to require and what to be skeptical of;
* the test "would I keep this if I didn't know who wrote it?" for quotability;
* "prefer false negatives" and a tie-break toward the lower level;
* calibration examples for a motivational cliché (level 3) and a cynical but sharp line (level 4-5).

The steps, the anchored levels, and the hard rules in code are unchanged, so acceptance thresholds still apply as
before.

## Runtime system prompt — selector version 6 and judge prompts v4 (method v3)

These versions came out of the October 2026 quality review and a controlled judge replay. The replay data and full
report are in `server/src/test/resources/judge-replay/`.

### What the review found

On the stored v3 verdicts:

* About 35-40% of rejections for an unresolved reference listed ordinary nouns, names, "I" or generic "we".
* Most fidelity rejections said "the excerpt omits X", although the prompt says omission alone is not a problem.
* Quotability was effectively binary: flat lines passed at 5/5/5.
* Accepted excerpts started with a dangling "It", "This" or "But".
* The selector rarely proposed multi-sentence ranges, and missed famous short lines.

### Prompt changes

Selector v6:

* asks for 2-4 unit ranges that make one argument;
* asks for the single most striking line, however short;
* forbids starting at a lowercase (mid-sentence) unit;
* adds a second worked example.

Standalone judge v4:

* Widens the "do not list" guidance to cover names, generic and indefinite noun phrases, generic roles, "I", dummy
  "it", cleft sentences, and lead-ins such as "Of course" or "Now".
* Only a reference that blocks the point caps `standsAlone`.
* Completeness gets all five anchors.
* A witty remark counts as having an insight.
* Adds quotability anchors for flat lines (level 3) and short paradoxes (level 5), and says that most passing quotes
  are a 4.

Fidelity judge v3:

* Compares the excerpt itself with the source, instead of comparing against the blind paraphrase.
* States that omission and generalisation are faithful.
* Adds calibration examples, including a character speaking against the author's view (level 2).

### Rule change

The acceptance rule changed with the prompts: an unresolved reference must be corroborated before it fails the
excerpt (see "Judge pass").

### Judge replay

The replay used 130 stored candidates, stratified across accepted, dangling-opener, fidelity-rejected,
quotability-rejected and reference-rejected. It ran the judge with both prompt versions, on Ministral 3 8B and on
Claude Opus 5.5 (the latter through sub-agents following the same prompt files).

* **Noise floor.** Ministral, re-run on its own v3 prompt, reproduces its stored decisions 88% of the time.
* **Changing the model.** With the same prompt, Ministral and Claude disagree on about 30% of decisions, with v3 and
  with v4 alike.
* **Changing the prompt.**
  * Claude changed only 8% of its decisions from v3 to v4.
  * Ministral changed 30%.
  * Against Claude v4 as the reference, Ministral's agreement rose from 65% to 70%. Missed good excerpts fell from 24
    to 11, but false accepts rose from 21 to 28.
* **Fidelity.** The fidelity changes worked: agreement on fidelity ≥ 4 rose from 66% to 80%.
* **Quotability.** Quotability agreement stayed low (58% → 64%), because Ministral still answers level 5 for about
  half of the candidates.

Conclusion: in the judge, roughly a third of the decisions depend on the model whatever the prompt. The remaining
prompt-level problems were concentrated in fidelity, and are fixed.

v4 makes Ministral accept more, including more weak excerpts. Before re-extracting the library with it, either:

* run the judge on a stronger model (`extraction.judge` can point elsewhere), or
* rely on the quality job (docs/features/quote-quality.md) to catch the weak excerpts that get through.

Re-extraction also deletes excerpt interpretations and admin-edited excerpt tags, which cascade from the excerpts.
Re-run interpretation and tagging afterwards.

### Replay tooling

`JudgeReplayRunner` (`./gradlew :server:runJudgeReplay`) replays stored candidates through the local judge (`llm`
mode) and compares replays (`report` mode). External verdict files in the same shape can be compared too.

---

## Structured model response

The response shape below is the original (v1-v3) single-call contract. From v4 the selector returns, per candidate,
in this order: `startUnit`, `endUnit`, `references` (`[{phrase, referentUnit}]`), `coreIdea` (at most ~15 words), and
`reason`, with no scores. The judge's two response shapes are defined in `ExcerptJudgeResponseSchema.kt`. In every
schema, free-text observations come before the levels, so the levels are generated after the model's own reading
rather than before it.

Conceptual response:

```json
{
  "excerpts": [
    {
      "startUnit": 2,
      "endUnit": 3,
      "independence": 94,
      "completeness": 96,
      "quotability": 87,
      "contextualFidelity": 95,
      "reason": "Expresses a complete claim without relying on the preceding setup."
    }
  ]
}
```

The `reason` field is diagnostic. It should not be shown as part of the quotation.

The production request should use llama-server's schema-constrained `response_format`.

The schema should constrain:

* `excerpts` to an array (no maximum element count — see "Initial length policy"),
* `startUnit` and `endUnit` to integers,
* all score fields to integers from 0 through 100,
* and `reason` to a short string.

Some relationships cannot be expressed conveniently in JSON Schema and must be validated in application code, including:

* endUnit >= startUnit,
* valid unit IDs,
* allowed word count,
* ordering,
* overlapping ranges,
* duplicate ranges.

---

## Deterministic application validation

Never trust a structurally valid LLM response by itself.

For every candidate:

Validate that the referenced units exist.

Validate that startUnit <= endUnit.

Reconstruct the excerpt from the original text rather than using LLM-generated text.

Calculate word count locally.

Reject ranges exceeding configured hard limits.

Reject exact duplicates.

Resolve substantial overlaps deterministically.

Do not modify the author's punctuation or wording as part of extraction.

A malformed candidate should not corrupt processing of other valid candidates.

---

## llama.cpp integration

The local development llama-server exposes an OpenAI-compatible API.

Use the configured base URL and:

`POST /v1/chat/completions`

Prefer schema-constrained JSON output using `response_format`.

Suggested initial generation configuration:

* temperature: approximately 0.1
* streaming: false
* small output-token budget because only structured metadata is returned
* an optional, configurable reasoning-effort passthrough (e.g. "low"/"medium"/"high") for
  reasoning-capable local models, left unset by default so no such field is sent unless explicitly
  configured; the exact wire field a given model/server build honors should be verified locally
  before relying on it

Do not couple extraction domain logic directly to llama.cpp-specific DTOs.

There should be a boundary that makes the inference implementation replaceable.

The plan should inspect existing project patterns before deciding what that abstraction looks like.

---

## Failure behavior

LLM extraction is non-critical enrichment.

A connection failure, timeout, malformed response, or model failure must not damage or replace the original quote.

Preserve enough error information for diagnostics.

Retry behavior should be conservative and explicit.

Do not retry deterministic validation failures indefinitely.

Current behavior:

* **Truncation.** A selector response cut off at the token limit (`finish_reason == "length"`) is retried once with
  twice the budget. A second truncation fails the attempt. Truncated JSON is never accepted, even when it happens to
  parse. Before this retry existed, six long sources failed outright on truncated JSON.
* **Token budgets.** The defaults are 2000 tokens for the selector and 700 for the judge.
* **No other retries.** Nothing else is retried.
* **Failed re-runs.** A failed re-run replaces only an earlier failed attempt. It keeps the last successful attempt
  and its excerpts, so an unreachable model never removes excerpts the TV is showing.

---

## Provenance

An extracted quote should retain a link to its original quote.

Prefer storing enough information to establish how the excerpt was produced, for example:

* original/source quote ID,
* extraction method/version,
* model identifier if available,
* prompt version,
* source character start/end offsets,
* extraction timestamp if useful.

Exact persistence fields should be decided based on the existing schema.

Do not duplicate metadata unnecessarily if an existing relationship can represent it cleanly.

---

## Prompt versioning

The runtime prompt is application behavior and should be version controlled.

Prefer a resource such as:

`server/src/main/resources/prompts/quote-extraction-v1.md`

Avoid embedding a large multiline prompt directly inside Kotlin code.

Record or otherwise make observable which prompt version produced an extraction so future prompt changes can be evaluated safely.

---

## Evaluation

Do not judge the feature only by whether the model returns valid JSON.

Create a small curated evaluation corpus containing difficult examples such as:

* a long passage containing one clearly independent sentence,
* several independently quotable thoughts,
* a passage where every sentence relies on previous context,
* pronoun-heavy prose,
* dialogue,
* a single extremely long sentence,
* philosophical prose where removing premises changes the conclusion,
* quotations containing semicolons or em dashes,
* a passage where the best excerpt is very short,
* a passage for which the correct result is no excerpt.

Separate deterministic tests from model-quality evaluation.

Deterministic automated tests should cover segmentation, offsets, reconstruction, validation, malformed responses, configuration, and failure handling.

Model-quality evaluation should preserve representative inputs and allow outputs to be reviewed using the independence, completeness, quotability, and contextual-fidelity rubric.

Eval fixtures (`server/src/test/resources/extraction-eval/*.json`) may carry optional gold labels, which use the unit
ids the runner prints:

* `goodRanges`: ranges that should be accepted.
* `badRanges`: tempting ranges that must be rejected, each with a `why`.
* `expectEmpty`: accepting anything from this source is wrong.

`ExtractionEvalRunner` runs the full selector-and-judge pipeline, prints every candidate with the judge's reading,
and ends with a summary (accepted good, accepted bad, accepted unlabeled, missed good, and expected-empty but
accepted) so prompt and model changes can be compared run to run. Real problem excerpts found in the admin should be
added as fixtures with `badRanges`.

---

## Possible second-pass validation

Implemented as the judge pass. See "Selector and judge".

---

## Non-goals for the initial feature

The first implementation should not:

* paraphrase quotations,
* summarize quotations,
* improve an author's wording,
* generate interpretations,
* classify themes or tags,
* fact-check quotations,
* identify quotation authors,
* use embeddings,
* or automatically delete/replace existing long quotations.

Those are separate concerns.
