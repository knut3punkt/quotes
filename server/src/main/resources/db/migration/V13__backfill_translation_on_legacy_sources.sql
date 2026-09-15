-- These 5 scripture sources were created before the translation column existed, so it was never
-- populated for them (unlike sources created by an import run since then, which set it directly).
-- Fill in the same values the corresponding importer already uses for a fresh import (see
-- bible/BibleImportService.kt, quran/QuranImportService.kt, etc.) so the admin UI's Version column
-- isn't blank for quotes staged against these pre-existing rows. Guarded by "translation IS NULL"
-- so this only touches the untouched legacy row, not any newer duplicate already created since.
UPDATE sources SET translation = 'James Legge' WHERE title = 'Tao Te Ching' AND translation IS NULL;
UPDATE sources SET translation = 'Sujato' WHERE title = 'Dhammapada' AND translation IS NULL;
UPDATE sources SET translation = 'Shri Purohit Swami' WHERE title = 'Bhagavad Gita' AND translation IS NULL;
UPDATE sources SET translation = 'King James Version' WHERE title = 'The Bible (King James Version)' AND translation IS NULL;
UPDATE sources SET translation = 'Pickthall' WHERE title = 'The Quran' AND translation IS NULL;
