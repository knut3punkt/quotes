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
* Acceptable excerpt length: 6–60 words.

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

Avoid selecting:

* housekeeping prose,
* scene-setting with no independent point,
* bibliographic information,
* transitional statements,
* repetitions,
* generic filler,
* sentences whose only purpose is to connect other sentences.

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

## LLM scoring

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

Initial application-side acceptance thresholds may be approximately:

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

---

## Structured model response

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

---

## Possible second-pass validation

Do not require this for the initial implementation unless testing shows it is necessary.

A later quality mode may make a second LLM request for each proposed excerpt and ask whether the excerpt remains faithful and independent when compared with its full surrounding source.

Design the first implementation so this can be added without rewriting the extraction domain model.

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
