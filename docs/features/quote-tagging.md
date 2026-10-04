# Quote Tagging

## Purpose

Tag quotations, and the excerpts extracted from them, so that two planned features have something to work with:

1. **Search in the TV app.** People will search by voice or text at very different levels of abstraction: a broad
   theme ("death", "love"), a specific idea ("fear of being forgotten", "self-deception"), or a feeling ("something
   uplifting"). Search must also find a quotation through its interpretive readings, not only through its literal
   words.
2. **Adaptive visuals.** The TV screen behind a quotation is composed from a pre-made set of backgrounds and layered
   symbol or image elements, chosen by the quotation's tags. No image is generated per quotation. The assets are
   made per tag, so visual tags must form a vocabulary that is reused often.

Neither feature is implemented yet. Tagging is manually triggered from the admin for selected quotes, using the
same local LLM infrastructure as extraction and interpretation.

---

## Facets

Every tag belongs to exactly one facet. The same name may exist in two facets ("light" as a concept and as a motif).

| Facet | What it is | Main consumer | Examples |
|---|---|---|---|
| `concept` | An idea the quotation is about | Search | death, knowledge, impermanence, fear of oblivion |
| `mood` | The emotional register a reader feels, as an adjective | Background atmosphere and colour; also search | serene, melancholic, defiant, wry |
| `motif` | A concrete, drawable thing the quotation evokes, literally or symbolically | Layered symbol/image elements | sea, lamp, seed, path, mirror |

### Concept breadth

Concepts carry a `breadth`:

* `broad` is a theme many quotations share (death, love, time, freedom).
* `specific` is a granular idea (impermanence, luck versus skill, the limits of reason).

This attribute is what gives search its levels of abstraction, without a curated category tree. It is stored on the
tag, set when the tag is first created, and editable by an admin.

### Mood is not topic

A quotation about death can be serene, and one about joy can be wistful. The prompt states this explicitly,
because the model otherwise tends to derive mood from topic.

### Motifs must be drawable

"Freedom" is a concept, while "open door" or "bird" is a motif. A motif may come from imagery the quotation clearly
suggests without naming it (a long journey suggests "path"), but must not be invented. Most plain statements have no
motifs, and an empty list is correct.

---

## Assignment attributes

Each tag on a quotation or excerpt carries:

* `relevance` 1-3. 3 = central, 2 = significant, 1 = peripheral. Search can rank by it. Visuals should use only
  central and significant tags.
* `basis`, which is `text` or `interpretation`. An `interpretation` tag comes only from one of the quotation's
  generated interpretations (see `quote-interpretations.md`). Search can include such tags to find quotations
  through their deeper readings; visuals should prefer `text` tags.
* `origin`, which is `llm` or `admin`.
* `rejected`. An admin removal is stored as a rejected row, not deleted (see "Re-running").

---

## Subjects

Unlike interpretations, tagging never covers both a quotation and its excerpts:

* A quotation with one or more excerpts that have `meets_thresholds = true` is tagged only through those excerpts.
  There is one call per excerpt, and no call for the whole quotation.
* A quotation with no such excerpts is tagged as a whole.

Each subject is tagged in its own call with its own stored interpretations as context. An excerpt also gets the
parent quotation as `ORIGINAL CONTEXT`, so that it isn't given tags the surrounding passage contradicts.

A quotation can be tagged as a whole first and get excerpts later. In that case, the next tagging run removes the
whole quotation's LLM tags as soon as at least one excerpt has been tagged successfully. Admin-added whole-quotation
tags and rejected markers stay. If every excerpt call fails, the whole quotation's tags are left alone, so the
quotation is never left untagged by a failure.

---

## Open vocabulary

The model may coin new tags. This keeps coverage high but invites fragmentation ("death", "mortality", "dying").
Three controls keep the vocabulary usable.

1. **Vocabulary hint.** Each call shows the model the existing canonical tags per facet, most used first. The default
   caps are 150 concepts, 60 moods and 80 motifs (configurable). The model is told to prefer an existing tag
   whenever it means the same thing. The hint is reloaded per quotation, so tags coined earlier in a batch are
   already preferred.
2. **Deterministic normalization** (`TagNormalization`). Names are trimmed, lowercased, and stripped of punctuation
   (except inner hyphens and apostrophes) and of a leading article. Motifs also get a simple plural fold ("stars"
   becomes "star"). Concepts don't, because "values" and "value" are different ideas. Two names that normalize alike
   within a facet are the same tag.
3. **Admin merge.** Merging tag A into tag B moves every assignment of A to B and keeps A as an alias
   (`merged_into_id`). When the model or an admin later uses A's name, it resolves to B, so the duplicate is never
   re-created. Merges also re-point A's own aliases, so alias chains stay one hop long.

The future visuals feature will map tags to assets by frequency: it will create assets for the most-used moods and
motifs first and fall back for the long tail. Merging keeps that tail short.

---

## Re-running

Tagging lives in the same table as admin edits, so re-running is deliberately conservative:

* Only subjects whose call **succeeded** have their earlier `llm` tags replaced. A failed call leaves that subject's
  previous tags in place.
* Admin-added tags are never removed by a re-run.
* Admin-rejected tags are never added back. Re-adding one by hand un-rejects it.
* Attempt rows (`quote_tagging_attempts`) are replaced on every run, one per subject, for provenance.

Re-running extraction deletes and replaces excerpts. Through `ON DELETE CASCADE`, that also removes the excerpts' tags,
including admin edits on them, the same as for interpretations.

---

## Model contract

User content, with each block clearly labelled:

```
QUOTE:                       (or QUOTE TO TAG: + ORIGINAL CONTEXT: for excerpts)
METADATA:                    (author and source, for disambiguation only; never tagged)
INTERPRETATIONS:             (numbered: lens, textual support, speculativeness, text)
EXISTING TAGS (prefer ...):  Concepts: ... / Moods: ... / Motifs: ...
```

Schema-constrained response:

```json
{
  "concepts": [{"name": "impermanence", "breadth": "specific", "relevance": 3, "basis": "text"}],
  "moods": [{"name": "serene", "relevance": 2, "basis": "text"}],
  "motifs": [{"name": "river", "relevance": 2, "basis": "text"}]
}
```

The schema only sets loose ceilings (12 concepts, 3 moods, 5 motifs, 48-character names). `TaggingValidator`
applies the real rules deterministically:

* normalizes each name;
* enforces word limits (concept 5, mood 2, motif 3);
* enum values for `breadth` and `basis` (a concept without a valid breadth is dropped);
* clamps relevance to 1-3;
* merges duplicates within a facet, keeping the highest relevance, with `text` basis winning over `interpretation`;
* caps each facet at the policy limits (defaults: 9 concepts, 2 moods, 3 motifs), keeping the most relevant.

A malformed item is dropped on its own. A `finish_reason` of `length` fails the call, as in the other LLM features.

---

## Runtime system prompt — version 1

`server/src/main/resources/prompts/quote-tagging-v1.md`, version label `quote-tagging-v1`. It:

* explains both uses of the tags (search and visual design) and gives the search question "what would someone say
  when they are looking for this quotation?";
* defines the three facets, with the targets of 1-3 broad and 2-6 specific concepts, 1-2 moods and 0-3 motifs;
* tells the model to prefer existing tags and coin new ones only for genuinely different ideas;
* tells the model to tag ideas brought out by the interpretations as `interpretation` basis, never making a highly
  speculative reading central;
* forbids tagging the author, the source, or generic words like "life" or "wisdom";
* gives two worked examples with exact JSON: a plain aphorism with no motifs, and a scriptural line with
  interpretations.

When the prompt changes, add a new resource file, keep the old one, and bump `TaggingPrompts.PROMPT_VERSION`.
Attempts record the prompt version.

---

## Configuration

`tagging.llm` (`TAGGING_LLM_*` environment overrides) and `tagging.policy` in `application.conf`. The default
temperature is 0.2, between extraction's 0.1 and interpretation's 0.5: the output should be consistent so that the
vocabulary converges, but not so rigid that it never notices a less obvious idea.

---

## Admin

* **Approved quotes page:**
  * "Generate tags" bulk action;
  * tag chips per quote and per qualifying excerpt, coloured by facet (broad concepts in bold, interpretation-basis
    tags dashed);
  * × on a chip to remove the tag;
  * "Add tag" with autocomplete from the vocabulary.
* **Tags page:**
  * every canonical tag with its usage count and aliases;
  * rename (also fixes casing; a rename that would collide with another tag is refused in favour of a merge);
  * change a concept's breadth;
  * merge into another tag of the same facet.

Routes: `POST /admin/quotes/generate-tags`, `POST /admin/quotes/{id}/tags`, `POST /admin/tag-assignments/{id}/reject`,
`GET /admin/tags?facet=&search=&limit=`, `PATCH /admin/tags/{id}`, `POST /admin/tags/{id}/merge`.

---

## Evaluation

`.\gradlew.bat :server:runTaggingEval` runs the fixtures in `server/src/test/resources/tagging-eval/` against a real
llama-server and prints the tags for manual review. The fixtures run in order against a vocabulary that grows from
the earlier fixtures' tags, and reused tags are marked, so the run shows whether the model converges on a shared
vocabulary. Review for:

* concepts at both breadths, naming ideas rather than paraphrasing;
* mood following feeling rather than topic;
* motifs only where there is drawable imagery;
* `interpretation` basis used for ideas that only the readings support;
* reuse of existing tags instead of synonyms.

Deterministic tests cover normalization, validation, prompt building, the client, configuration, and the service's
re-run, merge and failure semantics against the dev database. Ordinary tests never need a running LLM.

---

## Not in scope yet

* Search endpoints and the TV search UI.
* The visuals pipeline beyond per-tag asset generation: generating images for mood and motif tags is covered in
  `tag-images.md`, but the collage algorithm and TV rendering are not built.
* A hierarchy or relations between concepts, and embeddings for synonym detection or semantic search.
* Automatic tagging on import.
* Tags in `/api/quotes/random`.
