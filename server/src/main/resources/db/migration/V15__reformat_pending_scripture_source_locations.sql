-- raw_source_location for these four providers previously held a bare number/reference (e.g.
-- "97", "3", "1.1", "2:255"); the importers now format it with the source's English citation
-- terminology (e.g. "Verse 97"). Reformat already-staged pending rows the same way, from the
-- structured fields already in raw_payload, so the review queue is consistent for rows staged
-- before and after this change.
UPDATE imported_quotes
SET raw_source_location = 'Chapter ' || (raw_payload ->> 'chapter')
WHERE processing_status = 'pending' AND provider = 'tao-te-ching-legge';

UPDATE imported_quotes
SET raw_source_location = 'Verse ' || (raw_payload ->> 'verse')
WHERE processing_status = 'pending' AND provider = 'dhammapada-sujato';

UPDATE imported_quotes
SET raw_source_location = 'Chapter ' || (raw_payload ->> 'chapter') || ', Verse ' || (raw_payload ->> 'verse')
WHERE processing_status = 'pending' AND provider = 'bhagavad-gita-purohit';

UPDATE imported_quotes
SET raw_source_location = 'Surah ' || split_part(raw_payload ->> 'reference', ':', 1) || ', ' ||
    CASE
        WHEN split_part(raw_payload ->> 'reference', ':', 2) LIKE '%-%'
            THEN 'Ayahs ' || split_part(raw_payload ->> 'reference', ':', 2)
        ELSE 'Ayah ' || split_part(raw_payload ->> 'reference', ':', 2)
    END
WHERE processing_status = 'pending' AND provider = 'alquran-cloud-pickthall';
