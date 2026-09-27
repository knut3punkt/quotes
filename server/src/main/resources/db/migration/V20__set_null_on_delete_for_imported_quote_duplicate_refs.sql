-- Deleting an imported quote failed when another row referenced it as its (possible) duplicate.
-- Clear those references instead of blocking the delete.
ALTER TABLE imported_quotes
    DROP CONSTRAINT imported_quotes_duplicate_of_id_fkey,
    ADD CONSTRAINT imported_quotes_duplicate_of_id_fkey
        FOREIGN KEY (duplicate_of_id) REFERENCES imported_quotes (id) ON DELETE SET NULL;

ALTER TABLE imported_quotes
    DROP CONSTRAINT imported_quotes_possible_duplicate_of_id_fkey,
    ADD CONSTRAINT imported_quotes_possible_duplicate_of_id_fkey
        FOREIGN KEY (possible_duplicate_of_id) REFERENCES imported_quotes (id) ON DELETE SET NULL;
