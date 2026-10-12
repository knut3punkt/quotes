# Tag Images

## Purpose

The TV screen behind a quotation will be composed from pre-made per-tag assets (see `quote-tagging.md`, "Adaptive
visuals"). This feature makes those assets:

* **backgrounds** for each `mood` tag (3, in different styles): full-screen 16:9 atmosphere images, usually with
  a calm area for text;
* a **collage element** for each `motif` tag: one symbolic motif in a soft, style-appropriate vignette on a
  transparent background, to be layered over a background.

Images are generated on a ComfyUI server (Qwen Image 2.1), stored as PNG files on the server, and recorded in
`tag_images` with provenance and layout metadata for a future collage algorithm. Nothing is generated per quotation.

Generation is triggered by hand from the admin's Tags page. Triggering on new tags is not implemented yet.

---

## Eligibility

A run covers every tag that:

* has facet `mood` or `motif`;
* is canonical (`merged_into_id IS NULL`);
* is in use, with at least one non-rejected assignment;
* has fewer images from its kind's **current** recipe version than `IMAGES_PER_TAG` asks for: 3 backgrounds per
  mood tag, 1 element per motif tag. It gets the missing number, each in a different style profile, avoiding the
  styles it already has from that version.

Moods come first, then motifs, each most-used first, so a cancelled run has covered the tags that matter most. A
tag's images sit next to each other in the queue. Failed images leave no row, so the next run retries them, and a
tag with only some of its images is topped up. A run never regenerates an image that exists.

Images from older recipe versions don't count. So when a recipe gets a new version, every tag of that kind gets a
full new set on the next run. The old rows stay in `tag_images` and are still candidates for composition (see
`quote-composition.md`).

When an admin merges tag A into tag B, A's images move to B. An image that finishes generating after its tag was
merged is stored on the canonical tag.

---

## Prompts

Prompts are built from versioned style-profile recipes in `server/src/main/resources/image-prompts/`, read by
`StyledPromptRecipe`: `background-v4.json` for backgrounds and `motif-v2.json` for elements. Random choices are made
by application code, never by a model, with a generator seeded by the image's seed. The same seed also goes to the
KSampler, so a stored image can be reproduced exactly. The choices are stored as `prompt_ingredients` and the full
text as `prompt`, so provenance never depends on the generator staying the same across Kotlin versions.

```json
{
  "version": "background-v4",
  "description": "...",
  "inputs": { "tag": "..." },
  "template": "{{medium}} intended as a reusable background for quotations sharing a {{tag}} mood. Visual style: {{look}}. {{texture}}. {{composition}} {{quoteInstruction}} ...",
  "quoteLayouts": [ { "id": "left", "weight": 1.0, "instruction": "...", "composition": "..." }, ... ],
  "styleProfiles": [ { "id": "neon-noir", "weight": 1.7, "mediums": [...], "looks": [...], "textures": [...] }, ... ],
  "globalInstructions": [ "..." ],
  "negativePrompt": "text, typography, ...",
  "selectionRules": { ... }
}
```

### How a recipe is read

* `inputs` has exactly one key, the placeholder that takes the tag's display name: `{{tag}}` in backgrounds,
  `{{motif}}` in motifs.
* Each style profile has an `id`, a `weight`, and phrase lists with plural names. The template uses the singular
  as the placeholder: `mediums` fills `{{medium}}`, `edgeTreatments` fills `{{edgeTreatment}}`. Every profile has the
  same lists.
* When a run starts, each tag's styles are picked **by weight without replacement**, so one tag's images never
  share a style. Styles the tag already has from the current version are left out.
* For each image, the seeded generator picks one phrase from each list, uniformly and **only from that image's
  style**. Lists are picked in template order.
* `quoteLayouts` is optional. When a recipe has them, one is picked by weight and supplies both `{{composition}}`
  and `{{quoteInstruction}}`.
* `prompt_ingredients` holds `style`, one entry per list (`medium`, `look`, `texture`, or `medium`, `treatment`,
  `edgeTreatment`), and `quoteLayout` (the layout id) when there is one. Composition reads `style` to pair
  backgrounds with motifs, and `quoteLayout` to place the quote (`quote-composition.md`). So style and layout ids
  are part of that contract: renaming one in a new recipe version means updating `style-affinity` and the TV
  layouts too.
* `StyledPromptRecipe.parse` refuses a recipe that:
  * doesn't name exactly one input, or never uses it;
  * uses a placeholder it doesn't fill, or never uses one of its style lists;
  * uses layout placeholders without layouts, or has layouts without using their placeholders;
  * has profiles with differing lists, a field that isn't a plural list of strings, or an empty or blank phrase;
  * has duplicate or blank ids, or a weight that isn't positive.

### What reaches the image model

The model gets only the filled `template` and the `negativePrompt`. The other fields are guidance for whoever
edits the recipe. They are parsed, so a typo still fails loudly, but they are **not sent**:

* `description` and `inputs` describe the recipe.
* `globalInstructions` are meta-rules ("select exactly one style profile", "independent of the background's
  style"), or are already said by the template and negative prompt. Meta-instructions mean nothing to the text
  encoder and would only lengthen the prompt.
* `selectionRules` are enforced by the code as described above.
* `assetComposition` (motifs) is framing guidance:
  * the square canvas is the motif render size;
  * padding and no cropping are in the template;
  * the 55–70 % coverage figure stays a guide for writing phrases.

### Backgrounds (`background-v4.json`)

The `overlay` layout reserves no quote area, so the TV's contrast handling has to cope with a busy background
there.

### Motifs (`motif-v2.json`)

The template opens and closes with Qwen Image 2.1's transparency phrasing: "This is an RGBA image with
transparency." and "The image has alpha channel and the background is transparent." That phrasing is what makes
the model write an alpha channel. The workflow has no separate matting step.

The draft of this recipe left the phrasing out, on the assumption that the workflow would make the transparency.
It was added on purpose, and `selectionRules.edgeBehavior` and `assetComposition.alpha` say so. Without it,
elements come back opaque, and composition skips elements without alpha. The soft vignettes the edge treatments
ask for rely on the model writing partial alpha.

### Changing a recipe

When a recipe changes, add a new file, keep the old one (stored rows name it), and point
`StyledPromptRecipe.forKind` at the new version. Each image records `prompt_recipe_version`. `background-v1.json`
and `motif-v1.json` are the original recipes, in an older slot format that no code reads any more. They are kept
only as provenance for the rows made with them.

---

## ComfyUI workflows

`server/src/main/resources/comfyui/qwen-image-2.1-{background,motif}-v1.json` are API-format exports ("Export
(API)") of the Qwen Image 2.1 workflow. The server fills these node inputs, and `ComfyWorkflow` refuses at load time a
workflow that lacks one of them:

| Node | Class | Inputs filled |
|---|---|---|
| `459:452` | `TextEncodeQwenImage21` | `prompt`, `negative_prompt` |
| `459:458` | `KSampler` | `seed`, `steps` |
| `459:456` | `EmptyLatentImage` | `width`, `height` |
| `461` | `SaveImageAdvanced` | `filename_prefix` |

The export's `ResolutionSelector` node is removed, and width and height go straight onto the latent image, so the
server knows the exact output size. The defaults are 2752×1536 for backgrounds (Qwen Image 2.1's native 16:9 size,
about 4.2 MP) and 1024×1024 for elements.

If elements come back without transparency (`has_alpha = false`), the workflow is flattening the alpha channel.
Re-export the motif workflow from ComfyUI's official Qwen Image 2.1 RGBA template as a new version, keeping the
node ids above, or update `ComfyWorkflow`'s node constants to match.

---

## Layout metadata

`ImageAnalysis` computes this from the PNG when it is saved. It is deterministic and needs no model.

| Column | Meaning |
|---|---|
| `width`, `height` | Pixel size |
| `has_alpha` | The PNG has an alpha channel |
| `transparent_fraction` | Share of pixels with alpha < 16 |
| `mean_luminance` | Mean WCAG relative luminance (0-1) of the opaque pixels |
| `dominant_colors` | Up to 5 `{hex, weight}`, most common first, from opaque pixels (3-bit-per-channel bins) |
| `layout` | Kind-specific JSON, below |

All coordinates are 0-1 fractions of the image size, origin top left.

**Background `layout`:**

```json
{
  "gridColumns": 16, "gridRows": 9,
  "cells": [[{"luminance": 0.41, "detail": 0.004}, ...], ...],
  "calmRegion": {"x": 0.0, "y": 0.0, "width": 0.5, "height": 1.0},
  "textTone": "dark"
}
```

* `cells[row][column]` holds each cell's mean luminance and `detail`. Detail is the mean luminance gradient of a copy
  downscaled to 320 px wide, so grain and paper texture barely register while edges and objects do.
* `calmRegion` is the largest rectangle of calm cells. A cell is calm when its detail is at or below the image's
  median detail, or below an absolute floor of 0.02. The median guarantees a busy image still has a calm area, and
  the floor keeps a smooth image fully calm.
* `textTone` is `dark` when the calm region's luminance is above 0.179 (the WCAG contrast crossover point), otherwise
  `light`.

A collage algorithm can place the quotation in `calmRegion`, colour it by `textTone`, and use the grid to choose
low-detail cells for elements.

**Element `layout`:** `{"contentBox": {"x": ..., "y": ..., "width": ..., "height": ...}}` is the box around pixels
that are at least half opaque. It is the whole image when the PNG has no alpha channel, and `null` when it is fully
transparent. Use it to scale and anchor an element by its visible content rather than its canvas.

Provenance columns: `prompt`, `negative_prompt`, `prompt_recipe_version`, `prompt_ingredients`, `seed`,
`workflow_version`, `model_name`, `steps`, `comfy_prompt_id`, `generation_millis` (submission to finished output,
including ComfyUI queue time), `file_size_bytes`, `sha256`, `created_at`.

---

## Storage

Files are written to `imageGeneration.storageDir` (default `generated-images`, relative to the server's working
directory, so `server/generated-images/` under `:server:run`, which git ignores):

```
<storageDir>/background/<tagId>-<slug>-<seed>.png
<storageDir>/element/<tagId>-<slug>-<seed>.png
```

`tag_images.file_path` is relative to `storageDir`, so the directory can move. Each file is written to a temporary
file and then moved into place. If the database insert fails, the file is deleted.

---

## Serving

`GET /api/tag-images/{id}?width=` serves an image for TV frontends (`TagImageFileService`):

* The stored `file_path` is resolved under `storageDir`. A path that would escape it is refused.
* `width` snaps up to 640, 1280 or 1920, which keeps the cache bounded. With no width, or one not smaller than the
  original, the original PNG is served.
* A smaller copy is made once and cached at `<storageDir>/derived/<id>-<width>.{jpg,png}`:
  * backgrounds as JPEG at quality 0.88, about 350 KB at 1920 px instead of about 7 MB;
  * elements as PNG, to keep alpha.
  * The resize halves step by step, with bicubic interpolation.
  * The copy is written to a temporary file and moved into place.
* Responses carry `Cache-Control: public, max-age=31536000, immutable` and an `ETag` of the image's sha256 plus the
  width, and answer `If-None-Match` with 304. A tag image row is never rewritten in place.

Which images a quote shows is covered in `quote-composition.md`.

---

## Job and progress

`TagImageGenerationJob` runs in a coroutine scope owned by the application, so the admin request that starts it
returns at once. Only one run exists at a time.

* Prompts go to ComfyUI **one at a time**, and the server holds the queue. This keeps ComfyUI free for other use
  between images and makes cancelling clean.
* Completion is decided by polling `GET /history/{prompt_id}` every second, up to `imageTimeoutMillis` per image.
* For live progress, the server follows ComfyUI's WebSocket (`/ws?clientId=<run id>`) and maps `execution_start`
  and `progress` (sampler step / max) onto the item, and `status` onto ComfyUI's overall queue length. If the
  WebSocket drops, the run carries on with polling only, shows a warning, and reconnects.
* Each item moves through `queued` → `submitting` → `waiting` (in ComfyUI's queue) → `running` → `saving` →
  `done`, or ends as `failed` or `cancelled`. A failure only affects its own item.
* **Cancel** stops the run after the current item: the server removes that prompt from ComfyUI's queue
  (`POST /queue` with `delete`) and interrupts it (`POST /interrupt`). Remaining items are marked `cancelled`.
* The job state lives in memory. After a server restart the last run is forgotten, and a new run picks up the images
  that are still missing. A prompt that was in ComfyUI during the restart still finishes there, but is not
  recorded.

Admin API:

| Route | |
|---|---|
| `POST /admin/image-generation/start` | 202 with the initial state, 409 while a run is active |
| `POST /admin/image-generation/cancel` | Current state; no-op when idle |
| `GET /admin/image-generation/status` | Current state |
| `WS /admin/image-generation/ws` | Pushes the full state as JSON, at most four times a second |

The state is `{status, comfyUiBaseUrl, startedAt, finishedAt, comfyQueueRemaining, connectionWarning, items[]}`, with
one item per image `{tagId, tagName, facet, kind, style, status, prompt, seed, step, maxSteps, error, filePath, startedAt,
finishedAt}`. `style` is the image's style profile id. Seeds stay below 2^53, so
JavaScript shows them exactly.

The admin's Tags page shows a **Tag images** panel with the generate and cancel buttons, overall progress, and the
queue. Each queue row shows the tag, kind and style, status, step progress for the active image, elapsed time, the error for a
failed image, and the prompt, which expands to show the seed and file path.

---

## Configuration

`imageGeneration` in `application.conf`:

| Key | Default | Environment override |
|---|---|---|
| `comfyui.baseUrl` | `http://localhost:8188` | `COMFYUI_BASE_URL` |
| `comfyui.requestTimeoutMillis` | 30000 | `COMFYUI_REQUEST_TIMEOUT_MILLIS` |
| `comfyui.imageTimeoutMillis` | 900000 | `COMFYUI_IMAGE_TIMEOUT_MILLIS` |
| `storageDir` | `generated-images` | `IMAGE_STORAGE_DIR` |
| `background.width` / `height` / `steps` | 2752 / 1536 / 40 | `IMAGE_BACKGROUND_WIDTH` etc. |
| `motif.width` / `height` / `steps` | 1024 / 1024 / 40 | `IMAGE_MOTIF_WIDTH` etc. |

---

## Tests

Deterministic tests cover recipe composition and validation, workflow filling and validation, image analysis on
synthetic images, the ComfyUI HTTP client (mock engine) and WebSocket message parsing, configuration, and the job
against the dev database with a fake ComfyUI client: eligibility, failure isolation, the stored row and file,
refusing a second run, and cancelling. No test needs a running ComfyUI.

---

## Not in scope yet

* Generating automatically when new mood or motif tags appear.
* Showing images in the admin.
* Regenerating or deleting a single image.
