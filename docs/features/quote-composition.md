# Quote Composition

## Purpose

A TV quote screen is composed from three layers of pre-made per-tag images (see `tag-images.md`):

1. a **background** for one of the quotation's `mood` tags;
2. at most one **motif element** for one of its `motif` tags, in a style that fits the background;
3. the **quotation** with its attribution.

Nothing is generated per quotation. This is the second iteration of the algorithm. It follows the style-profile
recipes (`background-v4`, `motif-v2`): the background's own quote layout decides where the text goes, and the motif
is matched to the background by style. It is deliberately simple, so it can be judged on screen and refined.

The work is split between server and TV:

* **Server:** decides *which* images a showing of a quotation gets ("Selection", "Style affinity").
* **TV app:** decides *where* they go and how the text is coloured ("Layout", "Text contrast"), using the metadata
  the server sends with each chosen image.

The TV side lives in `platforms/tv-web/src/composition/`, as pure functions with an injected random source.

---

## Selection (server)

`server/src/main/kotlin/composition/`. `QuoteVisualSelector` is pure and takes a `kotlin.random.Random` and a
`StyleAffinity`. `selectVisualCandidates` loads the candidates for a batch of quotes in one query.

**Candidates.** A tag counts toward the quotation's visuals when its assignment:

* is not rejected;
* has relevance 2 or 3 ("visuals should use only central and significant tags", `quote-tagging.md`).

A quotation with qualifying excerpts (`meets_thresholds = true`) is shown on the TV as one of them, picked at random
for each batch; a quotation without is shown in full. A shown excerpt's tags count, along with any tags on the whole
quotation (admin-added ones can survive excerpt tagging), but not the tags of the quotation's other excerpts. When
the full text is shown, all its tags count.

Which images count:

* mood tags offer their `background` images;
* motif tags offer their `element` images, but only those with an alpha channel and a non-null `contentBox`. An opaque
  square breaks a collage.

Images from every recipe version are candidates. Each image's `style` and, for backgrounds, `quoteLayout` are read
from `prompt_ingredients`. Images from the v1 recipes have neither.

**Choice**, for each showing:

1. Pick a random mood tag *among those with images*. Picking the tag before the image gives every tag the same
   chance, however many images it has.
2. Pair each of that tag's background images with each motif tag. For each pair, use the motif tag's image that
   best fits the background (ties go to chance), scored by "Style affinity" below.
3. Keep the pairs with the highest score and pick one at random. When the best score is 0, nothing fits, and the pick
   is among all pairs: an unfitting motif is better than none.

Both the background and the motif image are chosen jointly. A mood tag's three backgrounds are in different styles,
so the one that suits the quotation's motifs best tends to win.

Without motif candidates, the background is a random image of the mood tag. Without backgrounds, the motif is a random
image of a random motif tag, and the TV renders the fallback.

**API.**

* `GET /api/quotes/random` gives each quotation `excerptId` (the excerpt shown as `text`, or null for the full text)
  and `visuals: {background: {imageId, tagId, tagName, width, height, meanLuminance, dominantColors, layout, style,
  quoteLayout} | null, elements: [{imageId, tagId, tagName, width, height, dominantColors, contentBox, style}],
  styleFit}`.
  * `elements` has 0 or 1 entries.
  * `styleFit` is the chosen pair's score, or null when there was no pair.
* `GET /api/quotes/{id}/visuals[?excerptId=]` returns a fresh selection for one quotation, or for the given excerpt of
  it. The TV's re-roll key uses it.

---

## Style affinity (server)

Background and motif styles are picked independently at generation time (`motif-v2.json`'s
`selectionRules.styleSelection`), so a quotation's motif may be in any style. Composition makes up for that by
preferring pairs that look right together.

`server/src/main/resources/composition/style-affinity-v1.json` puts each style profile in several overlapping
**groups** of shared visual traits. `StyleAffinity` reads it and scores a (background style, motif style) pair:

| Score | Meaning |
|---|---|
| 3 | the same style |
| 2 | two or more shared groups |
| 1 | one shared group |
| 0 | no shared group, or either style unknown (v1 images) |

| Group | What unites it |
|---|---|
| `neon-night` | darkness lit by saturated artificial light |
| `digital-signal` | screen and electronic artefacts |
| `psychedelic` | luminous, recursive, spectral forms |
| `cosmic-mystic` | celestial scale, sacred geometry, stillness |
| `dream-uncanny` | quiet strangeness, dream logic |
| `low-key-shadow` | deep blacks, a single light |
| `minimal` | large simple forms, strong negative space |
| `raw-energy` | aggressive, distorted, high-energy marks |
| `monumental` | massive stone and metal forms in fog |
| `organic` | living, translucent, wet matter |
| `mechanical` | machinery and industry |
| `retro-future` | imagined past futures, chrome, analog |
| `prismatic` | glass, chrome, iridescence |
| `geometric` | planes, lines, facets |
| `handmade` | ink, paint, paper, hatching |
| `photographic` | photographic realism and fragments |
| `warm-metallic` | gold, brass, amber, crimson |
| `bright` | high-key daylight and optimism |

The membership lists are in the JSON file. Some examples:

* neon-noir + cyberpunk = 2 (`neon-night`, `low-key-shadow`);
* brutalist-scifi + gothic-futurism = 2 (`low-key-shadow`, `monumental`);
* ink-noir + etching-engraving = 1 (`handmade`);
* solarpunk + neon-noir = 0.

Groups rather than a style-by-style matrix keep the mapping reviewable: a new style joins a few groups instead of
needing a score against every other style. The groups are a first cut, meant to be tuned on screen.

Parsing refuses duplicate or blank group ids, empty groups, a style listed twice in a group, and style ids that are
not profiles of the current recipes. A test checks that every current style is in at least two groups. When the
recipes gain styles, add them to groups. When the mapping changes in substance, add a new version of the file.

---

## Layout (TV)

`layout.ts`. The stage is 16:9, with a 5% TV-safe margin. Sizes are worked out in 16×9 units, so elements keep their
aspect ratio. The browser letterboxes the stage when the window has another shape.

The quote goes where the background's prompt kept room for it (`quoteLayout`, from `background-v4.json`). The motif
goes in the calmest of that layout's slots outside the quote box:

| `quoteLayout` | Quote box | Motif slots |
|---|---|---|
| `left` / `right` | 40% × 72%, in that half, inset from the edge; left-aligned | three positions (upper, middle, lower) in the other half |
| `top` / `bottom` | a band 76% × 38%; centred | left, middle and right of the other band |
| `center` | 56% × 60%, centred | the four corners and the middle of each side, small |
| `overlay` | as `center`, always with a soft panel (see "Text contrast") | as `center` |

* **The calmest slot** is the one with the lowest mean `detail`. Slots within 10% of it are picked between at random.
* **The reservation is checked.** A prompt is not a guarantee. If the quote box's mean detail is above 0.02 and more
  than 1.25 × that of the rest of the image, the layout is ignored. `overlay` is never checked.
* **The grid** takes over when the layout is ignored, or there is none (v1 backgrounds, the fallback gradient):
  * the quote goes on the **calmer half** by mean `detail`, as `left`/`right` above (near-ties go to the side of
    `calmRegion`, then to chance), and the motif goes on the other half;
  * without a motif, the quote is centred.

The motif is fitted to its slot by its **visible content** (`contentBox`), not by its canvas. It is scaled to
85–100% of the slot, with a small jitter that never leaves the slot and a rotation within ±5°. Slots never overlap
the quote box. Slots near a stage edge anchor the motif toward it.

The quote's font size is the largest that fits the box. The TV shrinks it by binary search after layout, so long
quotations never spill over the motif.

---

## Text contrast (TV)

`contrast.ts`. The method looks only at the background cells under the quote box, weighted by overlap. From them it
takes the mean, 10th-percentile and 90th-percentile luminance, and the mean and 90th-percentile ("peak") detail.

1. **Tone.** Choose light or dark text, whichever contrasts better with the *worst* part of the region:
   * light text is measured against the 90th percentile;
   * dark text against the 10th.
2. **Tint, not pure white or black.** The text takes the hue of the background's heaviest dominant colour that has real
   chroma (OKLCH C > 0.03), at low chroma:
   * light text: OKLCH L 0.96;
   * dark text: OKLCH L 0.20.
3. **Target.** Worst-case contrast of at least **4.5:1**. That is stricter than WCAG's large-text 3:1, because a TV is
   read from across the room over an image. If the target isn't met, the lightness moves toward an extreme (0.99 or
   0.12).
4. **Backdrop or bare text.** The quote gets a frosted backdrop (below) when any of these holds:
   * `texture`: the mean detail is above **0.015**;
   * `detail`: the peak detail is above **0.022**. This catches a few sharp or bright details (neon lines, filaments,
     hatching) that cell means average away. It also caught the v4 backgrounds where the image model painted
     pseudo-text into the reserved region;
   * `contrast`: the text colour alone can't reach the target;
   * `overlay`: always for an `overlay` background. That prompt reserved no room, on the promise that the quote brings
     "its own contrasting background or translucent panel".

   Otherwise the text stands bare on the background. It gets a soft, wide halo (`text-shadow` in the opposite tone and
   the background's hue) only when contrast is marginal (< 1.3 × target) or the region still has some texture.
5. **Backdrop opacity.** The backdrop's tint is a deep (or pale) shade of the background's hue, never black or white.
   Its opacity is **solved**, not fixed:
   * a binary search finds the lowest opacity at which the sRGB blend over the worst cell (the way CSS composites)
     reaches the target;
   * it is kept within **0.4–0.8**. Below that the glass looks accidental, and above it, it becomes a box.

   The blur removes detail inside cells, so the cell-based worst case is a fair model of what is under the glass. Text
   on a backdrop gets only a faint, tight shadow.

The thresholds were calibrated on the 51 v4 backgrounds by eye. At those values about 45% of them leave the text bare.
The calm side of `left`/`right` layouts is nearly always quiet, while `center`, `top` and `overlay` usually get a
backdrop.

The attribution uses the same hue a step softer, but only when it still meets the target.

The colours are emitted as `rgb()`/`rgba()`, because older webOS browsers lack CSS `oklch()` and `min()`.

---

## Fallback

When a quotation has no mood image, the TV draws a deep radial gradient (OKLCH L about 0.13–0.3). Its hue comes from the
motif's dominant colours, or a neutral slate when there is no motif. Its centre and angle are random. It is described
with the same luminance grid as a real background, so layout (by the grid) and contrast run unchanged.

---

## Rendering (TV)

`QuoteStage.tsx` draws the layers:

* the background, with a slow zoom (scale 1.0 → 1.05 over 30 s, transform only);
* the motif, with a `drop-shadow` in the background's darkest dominant colour;
* the quote (`QuoteDisplay.tsx`), on its backdrop when it has one.

**The backdrop** is a frosted glass card that hugs the fitted text:
* padding of 0.75em × 1.05em, so it scales with the font;
* corners rounded at 2.4% of the stage height;
* a 1 px hairline inside its edge;
* a soft shadow in the background's darkest colour.

`useFitText` measures the card with its padding, so it never spills over the motif.

The glass doesn't use `backdrop-filter`. Older webOS browsers lack it, and redoing it every frame over the zooming
background is costly on a TV. Instead, the card clips a second copy of the background image:
* the copy is stage-sized and offset by the card's measured position (`offsetLeft`/`offsetTop`, which ignore
  transforms);
* it has the same `ken-burns` zoom and origin, mounted together so the two zooms run in sync;
* it has `filter: blur(1.8% of the stage height) saturate(1.1)`;
* the copy is clipped by its own layer, not the card, so it doesn't count as card overflow when the font is fitted.

The tint is laid over the copy at the solved opacity. Over the fallback gradient the card is tint only.

A new composition is not shown until its background has loaded, or after 3 s at most. It then crossfades over the
previous one. The neighbouring quotations' images are preloaded.

Images are served downscaled: backgrounds at 1920 px wide (JPEG), elements at 640 px (PNG). See "Serving" in
`tag-images.md`.

Keys and switches for judging compositions:

* **ArrowUp** re-rolls the current quotation: a new server selection and a new layout.
* `?debug` in the URL shows:
  * the mood and motif with their styles, and the style fit;
  * the layout used, or `grid (asked for …)` when the background's layout was ignored;
  * the tone, the contrast reached, and any halo;
  * the backdrop's opacity and reason, or `no backdrop (quiet)`, with the mean and peak detail.

---

## Tests

* **Server:**
  * the selector (with a seeded `Random` and a small test mapping): at most one motif, the best-fitting motif tag and
    background image win, the best image of a tag is used, unfitting pairs still show, and ties are shared fairly;
  * the style affinity: scores, refusing broken mappings, and every current style in two or more groups;
  * candidate grouping, derivative width snapping, resizing, and path containment;
  * the dev-database `ApplicationTest` checks the `visuals` field.
* **TV (`npm test`, Vitest):**
  * colour maths against known WCAG values;
  * every quote layout, and the grid, keeps the quote and the motif inside the safe area and apart;
  * the quote lands in the reserved region, and the motif takes the calmest slot;
  * a busy reserved region and a missing or unknown layout fall back to the grid;
  * contrast reaches the target on every uniform grey;
  * a quiet region keeps bare text, while texture, a few sharp details, low contrast or an overlay background give a
    backdrop within its opacity range;
  * the fallback works, and compositions are reproducible per seed.

---

## Ideas not built yet

* Weight the mood choice by relevance or by `text` basis.
* Harmonise the motif's colours with the background.
* Gentle motif drift.
* A bundled serif display font for quotations.
* Tune the affinity groups from what looks right on screen.
