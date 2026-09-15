ALTER TABLE imported_quotes ADD COLUMN source_id INTEGER REFERENCES sources (id);

-- Backfill: existing pending scripture rows were staged before this column existed. Match each
-- by provider to the exact (title, translation) its importer creates today (see
-- bible/BibleImportService.kt, quran/QuranImportService.kt, etc.) so today's review queue is
-- immediately bulk-approvable instead of only rows staged after this ships.
UPDATE imported_quotes iq SET source_id = s.id
FROM sources s
WHERE iq.processing_status = 'pending' AND iq.source_id IS NULL AND (
    (iq.provider = 'bible-api-kjv' AND s.title = 'The Bible' AND s.translation = 'King James Version') OR
    (iq.provider = 'alquran-cloud-pickthall' AND s.title = 'The Quran' AND s.translation = 'Pickthall') OR
    (iq.provider = 'bhagavad-gita-purohit' AND s.title = 'Bhagavad Gita' AND s.translation = 'Shri Purohit Swami') OR
    (iq.provider = 'dhammapada-sujato' AND s.title = 'Dhammapada' AND s.translation = 'Sujato') OR
    (iq.provider = 'tao-te-ching-legge' AND s.title = 'Tao Te Ching' AND s.translation = 'James Legge')
);

-- These three providers now stage rawAuthor = NULL (the book is the source, not the author);
-- pending rows staged before that change still have the book name as raw_author. Clear it now
-- that source_id carries the book association instead.
UPDATE imported_quotes SET raw_author = NULL
WHERE processing_status = 'pending'
  AND provider IN ('bible-api-kjv', 'alquran-cloud-pickthall', 'bhagavad-gita-purohit');

-- Backfill raw_source_location the same way the updated importers now set it, from data already
-- present in raw_payload, so old pending rows get the same source-detail auto-fill new ones do.
UPDATE imported_quotes SET raw_source_location = raw_payload->>'reference'
WHERE processing_status = 'pending' AND raw_source_location IS NULL
  AND provider IN ('bible-api-kjv', 'alquran-cloud-pickthall');

UPDATE imported_quotes SET raw_source_location = (raw_payload->>'chapter') || '.' || (raw_payload->>'verse')
WHERE processing_status = 'pending' AND raw_source_location IS NULL
  AND provider = 'bhagavad-gita-purohit';

UPDATE imported_quotes SET raw_source_location = raw_payload->>'verse'
WHERE processing_status = 'pending' AND raw_source_location IS NULL
  AND provider = 'dhammapada-sujato';

UPDATE imported_quotes SET raw_source_location = raw_payload->>'chapter'
WHERE processing_status = 'pending' AND raw_source_location IS NULL
  AND provider = 'tao-te-ching-legge';
