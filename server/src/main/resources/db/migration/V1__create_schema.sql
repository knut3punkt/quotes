CREATE TABLE authors (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    birth_year INTEGER,
    death_year INTEGER
);

CREATE TABLE sources (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title TEXT NOT NULL,
    type TEXT NOT NULL,
    year INTEGER,
    url TEXT
);

CREATE TABLE quotes (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text TEXT NOT NULL,
    author_id INTEGER NOT NULL REFERENCES authors (id),
    source_id INTEGER REFERENCES sources (id),
    source_detail TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_quotes_author_id ON quotes (author_id);
CREATE INDEX idx_quotes_source_id ON quotes (source_id);

CREATE TABLE tags (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE quote_tags (
    quote_id INTEGER NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (quote_id, tag_id)
);
