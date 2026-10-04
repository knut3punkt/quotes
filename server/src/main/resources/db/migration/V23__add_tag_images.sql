-- Per-tag generated images (docs/features/tag-images.md). One row per successfully generated image;
-- the PNG itself lives on disk under the configured imageGeneration.storageDir, and file_path is
-- relative to that directory. A tag may have several images; a tag with none is "missing" and is
-- picked up by the next generation run. Failed generations leave no row.
--   kind 'background' is generated for mood tags, 'element' (a transparent collage element) for motif tags.
--   dominant_colors: [{"hex": "#rrggbb", "weight": 0.31}, ...], most common first.
--   layout: for a background, a 16x9 grid of {luminance, detail} cells plus calmRegion and textTone;
--           for an element, contentBox. Coordinates are 0-1 relative to the image, origin top left.
CREATE TABLE tag_images (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag_id INTEGER NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('background', 'element')),
    file_path TEXT NOT NULL UNIQUE,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256 TEXT NOT NULL,
    has_alpha BOOLEAN NOT NULL,
    transparent_fraction REAL NOT NULL,
    mean_luminance REAL NOT NULL,
    dominant_colors JSONB NOT NULL,
    layout JSONB NOT NULL,
    prompt TEXT NOT NULL,
    negative_prompt TEXT NOT NULL,
    prompt_recipe_version TEXT NOT NULL,
    prompt_ingredients JSONB NOT NULL,
    seed BIGINT NOT NULL,
    workflow_version TEXT NOT NULL,
    model_name TEXT,
    steps INTEGER NOT NULL,
    comfy_prompt_id TEXT NOT NULL,
    generation_millis INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tag_images_tag_id ON tag_images (tag_id);
