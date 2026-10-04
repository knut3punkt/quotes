# Quote Composition

## Purpose

A TV quote screen is composed from three layers of pre-made per-tag images (see `tag-images.md`):

1. a **background** for one of the quotation's `mood` tags;
2. up to four **collage elements** for its `motif` tags;
3. the **quotation** with its attribution.

Nothing is generated per quotation. This is the first iteration of the algorithm. It is deliberately simple, so it can
be judged on screen and refined.

The work is split between server and TV:

* **Server:** decides *which* images a showing of a quotation gets ("Selection").
* **TV app:** decides *where* they go and how the text is coloured ("Layout", "Text contrast"), using the metadata
  the server sends with each chosen image.

The TV side lives in `platforms/tv-web/src/composition/`, as pure functions with an injected random source.

---

## Selection (server)

`server/src/main/kotlin/composition/`. `QuoteVisualSelector` is pure and takes a `kotlin.random.Random`.
`selectVisualCandidates` loads the candidates for a batch of quotes in one query.

**Candidates.** A tag counts toward the quotation's visuals when its assignment:

* is not rejected;
* has relevance 2 or 3 ("visuals should use only central and significant tags", `quote-tagging.md`).

Tags on the whole quotation and tags on its excerpts both count. A quotation with qualifying excerpts is tagged only
through them, and the TV shows the full text.

Which images count:

* mood tags offer their `background` images;
* motif tags offer their `element` images, but only those with an alpha channel and a non-null `contentBox`. An opaque
  square breaks a collage.

**Choice**, for each showing:

1. Pick a random mood tag *among those with images*, then a random image of it. If there is none, `background` is
   null and the TV renders the fallback.
2. Draw N uniformly from 1 to 4. Take min(N, available) distinct motif tags with images, in random order, and one
   random image of each. M is the number of elements this gives.

Picking the tag before the image gives every tag the same chance, however many images it has.

**API.**

* `GET /api/quotes/random` gives each quotation
  `visuals: {background: {imageId, tagId, tagName, width, height, meanLuminance, dominantColors, layout} | null,
  elements: [{imageId, tagId, tagName, width, height, dominantColors, contentBox}]}`.
* `GET /api/quotes/{id}/visuals` returns a fresh selection for one quotation. The TV's re-roll key uses it.

---

## Layout (TV)

`layout.ts`. The stage is 16:9, with a 5% TV-safe margin. Sizes are worked out in 16×9 units, so elements keep their
aspect ratio. The browser letterboxes the stage when the window has another shape.

| M | Arrangement |
|---|---|
| 0 | Quote centred, about 62% wide. |
| 4 | One element per corner slot (24% × 34%, content anchored toward the corner). The quote is centred between the slot columns. |
| 3 | The **calmest quadrant** (lowest mean `detail`) stays empty. The quote box is the free rectangle reaching into that corner, so it sits off-centre toward it. |
| 1, 2 | The quote goes on the **calmer half** (by mean `detail`; near-ties go to the side of `calmRegion`, then to chance), left-aligned. Elements fill the other half: one large slot, or two slots staggered diagonally. |

Each element is fitted to its slot by its **visible content** (`contentBox`), not by its canvas. It is scaled to
85–100% of the slot, with a small jitter that never leaves the slot and a rotation within ±5°. Slots never overlap the
quote box.

The quote's font size is the largest that fits the box. The TV shrinks it by binary search after layout, so long
quotations never spill into the collage.

---

## Text contrast (TV)

`contrast.ts`. The method looks only at the background cells under the quote box, weighted by overlap. From them it
takes the mean, 10th-percentile and 90th-percentile luminance, and the mean detail.

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
4. **Halo.** When the region is textured (mean detail > 0.02), when there is a scrim, or when contrast is marginal
   (< 1.3 × target), the text gets a soft, wide `text-shadow`. It is in the opposite tone, in the background's hue.
5. **Scrim.** Only if the target still isn't met. The scrim is a rounded shape behind the quote box with a wide,
   blurred edge, in a deep (or pale) shade of the background's hue, never black or white. Its opacity is **solved**,
   not fixed:
   * a binary search finds the lowest opacity at which the sRGB blend over the worst cell (the way CSS composites)
     reaches the target;
   * a small margin is added for the feathered edge;
   * the opacity is capped at 0.7.

The attribution uses the same hue a step softer, but only when it still meets the target.

The colours are emitted as `rgb()`/`rgba()`, because older webOS browsers lack CSS `oklch()` and `min()`.

---

## Fallback

When a quotation has no mood image, the TV draws a deep radial gradient (OKLCH L about 0.13–0.3). Its hue comes from the
chosen elements' dominant colours, or a neutral slate when there are none. Its centre and angle are random. It is
described with the same luminance grid as a real background, so layout and contrast run unchanged.

---

## Rendering (TV)

`QuoteStage.tsx` draws the layers:

* the background, with a slow zoom (scale 1.0 → 1.05 over 30 s, transform only);
* the scrim;
* the elements, with a `drop-shadow` in the background's darkest dominant colour;
* the quote.

A new composition is not shown until its background has loaded, or after 3 s at most. It then crossfades over the
previous one. The neighbouring quotations' images are preloaded.

Images are served downscaled: backgrounds at 1920 px wide (JPEG), elements at 640 px (PNG). See "Serving" in
`tag-images.md`.

Keys and switches for judging compositions:

* **ArrowUp** re-rolls the current quotation: a new server selection and a new layout.
* `?debug` in the URL shows the chosen tags, the arrangement, the tone, the contrast reached, and any halo or scrim.

---

## Tests

* **Server:** the selector (with a seeded `Random`), candidate grouping, derivative width snapping, resizing, and
  path containment. The dev-database `ApplicationTest` checks the `visuals` field.
* **TV (`npm test`, Vitest):**
  * colour maths against known WCAG values;
  * every arrangement stays inside the safe area, with no overlap between the quote box and slots;
  * a calm side or corner is chosen;
  * contrast reaches the target on every uniform grey;
  * a scrim appears only when needed;
  * the fallback works, and compositions are reproducible per seed.

---

## Ideas not built yet

* Weight the mood choice by relevance or by `text` basis.
* Harmonise element colours with the background.
* Gentle per-element drift.
* A bundled serif display font for quotations.
* Place the M=0 quote in the background's `calmRegion`.
