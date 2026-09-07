-- Verified against 9 real Wikiquote author pages (Jung, Einstein, Nietzsche, Kierkegaard, Marcus
-- Aurelius, ...): "Disputed" is a real, recurring top-level section distinct from Unsourced —
-- quotes whose attribution/authenticity Wikiquote editors have explicitly flagged as doubtful,
-- not merely uncited. "Misattributed" and "Quotes about X" are the other common top-level sections
-- and are deliberately never imported: they are, respectively, quotes confirmed NOT to be this
-- person's, and quotes about this person said by someone else.
ALTER TABLE imported_quotes DROP CONSTRAINT imported_quotes_source_confidence_check;
ALTER TABLE imported_quotes
    ADD CONSTRAINT imported_quotes_source_confidence_check
        CHECK (source_confidence IN ('sourced', 'attributed', 'unsourced', 'disputed'));
