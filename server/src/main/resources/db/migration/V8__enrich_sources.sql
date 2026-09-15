-- A lookup table, not an enum: this migration is the start of adding many heterogeneous source
-- kinds (scripture, philosophical-work, ...) over time, and a lookup table turns "add a new kind"
-- into a row insert instead of a schema migration.
CREATE TABLE source_types (
    code TEXT PRIMARY KEY,
    description TEXT NOT NULL
);

INSERT INTO source_types (code, description) VALUES
    ('book', 'A published book'),
    ('article', 'An article or essay'),
    ('speech', 'A speech, lecture, or sermon'),
    ('interview', 'An interview'),
    ('film', 'A film or broadcast'),
    ('scripture', 'A religious scripture or canonical text'),
    ('philosophical-work', 'A philosophical treatise or work');

ALTER TABLE sources ADD COLUMN type_code TEXT REFERENCES source_types (code);
UPDATE sources SET type_code = type;
ALTER TABLE sources ALTER COLUMN type_code SET NOT NULL;
ALTER TABLE sources DROP COLUMN type;

-- Advisory only (e.g. "chapter.verse", "surah.ayah", "page") — shown in the admin UI, not enforced
-- against quotes.source_detail.
ALTER TABLE sources ADD COLUMN citation_unit TEXT;

-- License now genuinely varies per source (CC0, CC-BY-SA-4.0, PD, ...) and needs tracking rather
-- than assuming uniform terms across every importer.
ALTER TABLE sources ADD COLUMN license TEXT;
ALTER TABLE sources ADD COLUMN attribution_text TEXT;
