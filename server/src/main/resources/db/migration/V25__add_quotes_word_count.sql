-- Word count of a quote, computed by the database so the public random-quote query can filter on it:
-- PublicQuoteService skips quotes too long to show in full when they have no accepted excerpt. Runs
-- of whitespace separate words, as in the admin's word counts.
ALTER TABLE quotes
    ADD COLUMN word_count INTEGER GENERATED ALWAYS AS (array_length(regexp_split_to_array(btrim(text), '\s+'), 1)) STORED;
