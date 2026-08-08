ALTER TABLE imported_quotes
    ADD COLUMN source_confidence TEXT
        CHECK (source_confidence IN ('sourced', 'attributed', 'unsourced'));
