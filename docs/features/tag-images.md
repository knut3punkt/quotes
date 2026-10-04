# Tag Images

## Purpose

The TV screen behind a quotation will be composed from pre-made per-tag assets (see `quote-tagging.md`, "Adaptive
visuals"). This feature makes those assets:

* a **background** for each `mood` tag: a full-screen 16:9 atmosphere image with calm areas for text;
* a **collage element** for each `motif` tag: one isolated, drawable object on a transparent background, to be
  layered over a background.

Images are generated on a ComfyUI server (Qwen Image 2.1), stored as PNG files on the server, and recorded in
`tag_images` with provenance and layout metadata for a future collage algorithm. Nothing is generated per quotation.

Generation is triggered by hand from the admin's Tags page. Triggering on new tags is not implemented yet.

---

## Eligibility

A run covers every tag that:

* has facet `mood` or `motif`;
* is canonical (`merged_into_id IS NULL`);
* is in use, with at least one non-rejected assignment;
* has no `tag_images` row yet.

Moods come first, then motifs, each most-used first, so a cancelled run has covered the tags that matter most.
Failed images leave no row, so the next run retries them. A run never regenerates a tag that already has an image.

When an admin merges tag A into tag B, A's images move to B. An image that finishes generating after its tag was
merged is stored on the canonical tag.

---

## Prompts

Prompts are built from versioned recipes in `server/src/main/resources/image-prompts/` (`background-v1.json`,
`motif-v1.json`):

```json
{
  "version": "background-v1",
  "template": "{{medium}} background evoking a {{tag}} mood: {{setting}}. ...",
  "slots": { "medium": ["A painterly", "A watercolor", ...], "setting": [...], ... },
  "negativePrompt": "text, letters, ..."
}
```

`{{tag}}` is the tag's display name. Every other placeholder is a slot, and one phrase is picked per slot with a
random generator seeded by the image's seed. The same seed also goes to the KSampler, so a stored image can be
reproduced exactly. The picked phrases are stored as `prompt_ingredients` and the full text as `prompt`, so
provenance never depends on the generator staying the same across Kotlin versions.

Recipe rules:

* Mood is open vocabulary, so background slots never name a colour or a feeling outright. They ask for a palette
  "that expresses the mood" and vary the medium, setting, light, texture and composition. Compositions always leave
  calm, open space, because a quotation goes on top.
* The motif template opens with Qwen Image 2.1's transparency phrasing ("This is an RGBA image with transparency.
  ... The image has alpha channel and the background is transparent."). That phrasing is what makes the model write
  an alpha channel. Motif styles are kept to a small family of illustration styles, so elements from different
  tags still sit together in one collage.
* `PromptRecipe.parse` refuses a recipe whose placeholders lack phrases, or that never uses `{{tag}}`.

When a recipe changes, add a new file (`background-v2.json`), keep the old one, and point `PromptRecipe` at the new
version. Each image records `prompt_recipe_version`.

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
* The job state lives in memory. After a server restart the last run is forgotten, and a new run picks up the tags
  that still have no image. A prompt that was in ComfyUI during the restart still finishes there, but is not
  recorded.

Admin API:

| Route | |
|---|---|
| `POST /admin/image-generation/start` | 202 with the initial state, 409 while a run is active |
| `POST /admin/image-generation/cancel` | Current state; no-op when idle |
| `GET /admin/image-generation/status` | Current state |
| `WS /admin/image-generation/ws` | Pushes the full state as JSON, at most four times a second |

The state is `{status, comfyUiBaseUrl, startedAt, finishedAt, comfyQueueRemaining, connectionWarning, items[]}`, with
each item `{tagId, tagName, facet, kind, status, prompt, seed, step, maxSteps, error, filePath, startedAt,
finishedAt}`. Seeds stay below 2^53, so JavaScript shows them exactly.

The admin's Tags page shows a **Tag images** panel with the generate and cancel buttons, overall progress, and the
queue. Each queue row shows the tag, kind, status, step progress for the active image, elapsed time, the error for a
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
* Several images per tag, or regenerating one. The table already allows several rows per tag.
