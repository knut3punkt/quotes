-- TV display settings (docs/features/quote-quality.md, "TV quality overlay"): at most one row. While
-- show_quality is set, the TV shows the shown quote's or excerpt's verdict and flags in quality_run_id.
-- Diagnostic only; it changes no data.

CREATE TABLE tv_display_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    show_quality BOOLEAN NOT NULL DEFAULT FALSE,
    quality_run_id INTEGER REFERENCES quote_quality_runs (id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
