# Quote Composition

## Purpose

A TV quote screen is composed from three layers of pre-made per-tag images (see `tag-images.md`):

1. a **background** for one of the quotation's `mood` tags;
2. the quotation's **motifs**, one image for each `motif` tag, shown as a row of round icons along an edge, like the
   status icons of a classic video game;
3. the **quotation** with its attribution.

Nothing is generated per quotation. This is the third iteration of the algorithm. The second dropped one motif element
into a calm slot of the background, picked to fit the background's style. That depended too much on the particular
motif and background, and the screens felt random. Icons give every motif a fixed, orderly frame, so any motif works
with any background, whatever its style. The background's own quote layout still decides where the text goes.

The work is split between server and TV:

* **Server:** decides *which* images a showing of a quotation gets ("Selection").
* **TV app:** decides *where* they go and how the text and icons are coloured ("Layout", "Text contrast", "Icon
  style"), using the metadata the server sends with each chosen image.

The TV side lives in `platforms/tv-web/src/composition/`, as pure functions; the only randomness (the fallback
gradient, the background's zoom origin) comes from an injected random source.

---

## Selection (server)

`server/src/main/kotlin/composition/`. `QuoteVisualSelector` is pure and takes a `kotlin.random.Random`.
`selectVisualCandidates` loads the candidates for a batch of quotes in one query.

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
  square would fill the icon edge to edge.

Images from every recipe version are candidates. Each image's `style` and, for backgrounds, `quoteLayout` are read
from `prompt_ingredients`. Images from the v1 recipes have neither. `style` is only shown in `?debug`.

**Choice**, for each showing:

1. Pick a random mood tag *among those with images*, then a random background of it. Picking the tag before the
   image gives every tag the same chance, however many images it has.
2. Every motif tag gets one random image of its own, whatever its style. The motifs are ordered by the tag's highest
   relevance to what is shown (an excerpt's tag can be central there and only significant on the whole quotation),
   then by name, and capped at **4** (`MAX_MOTIF_ICONS`). Tagging keeps at most three motifs per quotation or excerpt,
   but a shown excerpt also carries the whole quotation's tags.

Without backgrounds, the motifs are still chosen, and the TV renders the fallback.

**API.**

* `GET /api/quotes/random` gives each quotation `excerptId` (the excerpt shown as `text`, or null for the full text)
  and `visuals: {background: {imageId, tagId, tagName, width, height, meanLuminance, dominantColors, layout, style,
  quoteLayout} | null, elements: [{imageId, tagId, tagName, width, height, dominantColors, contentBox, style}]}`.
  `elements` has 0–4 entries, most relevant first.
* `GET /api/quotes/{id}/visuals[?excerptId=]` returns a fresh selection for one quotation, or for the given excerpt of
  it. The TV's re-roll key uses it.

---

## Layout (TV)

`layout.ts`. The stage is 16:9, with a 5% TV-safe margin. Sizes are worked out in 16×9 units, so the icons stay
round. The browser letterboxes the stage when the window has another shape. Layout is deterministic: the same
background and motifs are always laid out the same way.

**The quote** goes where the background's prompt kept room for it (`quoteLayout`, from `background-v4.json`):

| `quoteLayout` | Quote box |
|---|---|
| `left` / `right` | 40% × 72%, in that half, inset from the edge; left-aligned |
| `top` / `bottom` | a band 76% × 38%; centred |
| `center` | 56% × 60%, centred |
| `overlay` | as `center`, always with a soft panel (see "Text contrast") |

* **The reservation is checked.** A prompt is not a guarantee. If the quote box's mean detail is above 0.02 and more
  than 1.25 × that of the rest of the image, the layout is ignored. `overlay` is never checked.
* **The grid** takes over when the layout is ignored, or there is none (v1 backgrounds, the fallback gradient): the
  quote is centred, as `center`.

**The icons** sit in a **dock**: a line running from one corner of the safe area, the first (most relevant) motif in
the corner. There are eight docks, a row along the horizontal edge and a column along the vertical edge from each
corner.

* **Size.** An icon is **20%** of the stage height across (216 px at 1080p), large enough for the motif artwork to
  read from a sofa, with 2.5% between icons.
* **Choice.** Docks closer to the quote box than 2% of the stage height are out. Of the rest, the one with the lowest
  score wins: mean detail + 0.5 × peak (90th-percentile) detail + 0.05 × luminance spread (90th − 10th percentile),
  under the dock. Calm makes the motifs easy to see; an even tone lets one ring colour read all around. Each step down
  the preference order (top-left, top-right, bottom-left, bottom-right; row before column) adds 0.001, so near ties
  go to the usual corner and the icons feel like a fixed HUD rather than a placement by chance.
* **When nothing fits,** the icons shrink to 15% of the stage height. If still no dock is clear, the best dock is
  used anyway and the quote box gives way on the side facing it. With up to four icons, the current layouts never
  need either.

**In an icon,** the motif is fitted by its **visible content** (`contentBox`), not its canvas: centred, its longer side
spanning 80% of the space inside the ring. No rotation or jitter; the icons are meant to look orderly.

The quote's font size is the largest that fits the box. The TV shrinks it by binary search after layout, so long
quotations never spill over the icons.

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

## Icon style (TV)

`chooseIconStyle` in `contrast.ts`. All icons share one style, so they read as a set. It is worked out from the
background cells under the dock, the same way as the text's:

* **The ring** is tinted like the quote text: light or dark, whichever contrasts better with the worst part of the
  region, in the background's hue at low chroma. Its target is **3:1**, WCAG's minimum for graphical objects. The
  ring is a shape, not text. On the dock's region it usually comes out in the same tone as the quote.
* **Two thin edges** in the opposite tone (the backdrop's deep or pale shade) frame the ring inside and out, so it
  reads on any background, as on classic game HUDs.
* **The disc** is frosted glass in that shade, like the quote's backdrop. Its opacity goes from **0.4** on a quiet
  dock up to **0.7** where the peak detail reaches 0.08, so a busy scene is quieted behind the motifs.

---

## Fallback

When a quotation has no mood image, the TV draws a deep radial gradient (OKLCH L about 0.13–0.3). Its hue comes from the
motifs' dominant colours, or a neutral slate when there are no motifs. Its centre and angle are random. It is described
with the same luminance grid as a real background, so layout (by the grid) and contrast run unchanged.

---

## Rendering (TV)

`QuoteStage.tsx` draws the layers:

* the background, with a slow zoom (scale 1.0 → 1.05 over 30 s, transform only);
* the motif icons (`MotifIcons.tsx`);
* the quote (`QuoteDisplay.tsx`), on its backdrop when it has one.

**The glass** (`FrostedGlass.tsx`), behind the quote and inside each icon, doesn't use `backdrop-filter`. Older webOS
browsers lack it, and redoing it every frame over the zooming background is costly on a TV. Instead, the glass clips
a second copy of the background image:
* the copy is stage-sized and offset so it lines up with the stage;
* it has the same `ken-burns` zoom and origin, mounted together so the two zooms run in sync;
* it has `filter: blur(1.8% of the stage height) saturate(1.1)`.

The tint is laid over the copy at its opacity. Over the fallback gradient the glass is tint only.

**The quote's backdrop** is a frosted glass card that hugs the fitted text:
* padding of 0.75em × 1.05em, so it scales with the font;
* corners rounded at 2.4% of the stage height;
* a 1 px hairline inside its edge;
* a soft shadow in the background's darkest colour.

The card's copy of the background is offset by the card's measured position (`offsetLeft`/`offsetTop`, which ignore
transforms). It is clipped by its own layer, not the card, so it doesn't count as card overflow when the font is
fitted. `useFitText` measures the card with its padding.

**An icon** is a circle of glass, its copy of the background placed by the icon's known position on the stage. The
motif's canvas is drawn over it, clipped by the circle. On top, inset shadows draw the outer edge (1.4% of the
diameter, at least 1 px), the ring, and the inner edge (1%), 8% of the diameter in all. The circle has a soft drop
shadow in the background's darkest colour. An icon whose image fails to load is left out.

Each icon blurs its own stage-sized copy of the background, which costs more on a TV than one card does. If that
stutters on real hardware, the disc can drop to tint only.

A new composition is not shown until its background has loaded, or after 3 s at most. It then crossfades over the
previous one. The neighbouring quotations' images are preloaded.

Images are served downscaled: backgrounds at 1920 px wide (JPEG), elements at 640 px (PNG). See "Serving" in
`tag-images.md`.

Keys and switches for judging compositions:

* **ArrowUp** re-rolls the current quotation: a new server selection and a new layout.
* `?debug` in the URL shows:
  * the mood and the motifs, with their styles;
  * the dock, the ring's tone and contrast, and the glass opacity;
  * the layout used, or `grid (asked for …)` when the background's layout was ignored;
  * the tone, the contrast reached, and any halo;
  * the backdrop's opacity and reason, or `no backdrop (quiet)`, with the mean and peak detail.

---

## Tests

* **Server:**
  * the selector (with a seeded `Random`): every motif tag once with one of its images, in candidate order, the cap,
    motifs without a background, and mood tags shared fairly;
  * candidate grouping and ordering by relevance, derivative width snapping, resizing, and path containment;
  * the dev-database `ApplicationTest` checks the `visuals` field.
* **TV (`npm test`, Vitest):**
  * colour maths against known WCAG values;
  * for every quote layout, and the grid, with 1–4 motifs: the quote and every icon inside the safe area, apart, and
    the icons round and full size;
  * icons line up from a corner, the calmest clear dock wins, near ties go to the top left, and layout is
    deterministic;
  * the quote lands in the reserved region; a busy reserved region and a missing or unknown layout centre it;
  * the motif is centred in its icon by its visible content;
  * contrast reaches the target on every uniform grey, and the ring reaches 3:1;
  * a quiet region keeps bare text, while texture, a few sharp details, low contrast or an overlay background give a
    backdrop within its opacity range; the icons' glass thickens with detail;
  * the fallback works, and compositions are reproducible per seed.

---

## Ideas not built yet

* Weight the mood choice by relevance or by `text` basis.
* A gentle staggered fade-in of the icons.
* A bundled serif display font for quotations.
