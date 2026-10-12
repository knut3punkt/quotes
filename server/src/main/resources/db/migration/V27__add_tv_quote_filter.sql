-- The TV preview filter (docs/features/quote-quality.md, "TV preview filter"): at most one row, holding the
-- admin's approved-quotes filter as a URL query string. While it exists, GET /api/quotes/random shows only
-- quotes, and excerpts, that match it. Nothing else is changed; clearing the row restores the full pool.

CREATE TABLE tv_quote_filter (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    query TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
