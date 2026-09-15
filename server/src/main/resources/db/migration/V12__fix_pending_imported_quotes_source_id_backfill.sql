-- V11's provider -> source backfill matched against the post-refactor (title, translation) pairs
-- (e.g. title "The Bible", translation "King James Version"), but per the prior session's explicit
-- decision not to rewrite historical source rows, the existing sources still hold their
-- pre-refactor values (e.g. title "The Bible (King James Version)", translation NULL) — so V11's
-- backfill matched zero rows. Redo it against what's actually there. V11 is already applied, so
-- this corrects it forward rather than editing an applied migration.
UPDATE imported_quotes iq SET source_id = s.id
FROM sources s
WHERE iq.processing_status = 'pending' AND iq.source_id IS NULL AND (
    (iq.provider = 'bible-api-kjv' AND s.title = 'The Bible (King James Version)') OR
    (iq.provider = 'alquran-cloud-pickthall' AND s.title = 'The Quran') OR
    (iq.provider = 'bhagavad-gita-purohit' AND s.title = 'Bhagavad Gita') OR
    (iq.provider = 'dhammapada-sujato' AND s.title = 'Dhammapada') OR
    (iq.provider = 'tao-te-ching-legge' AND s.title = 'Tao Te Ching')
);
