-- The TV quality overlay shows several runs side by side instead of one.
CREATE TABLE tv_display_quality_runs (
    run_id INTEGER PRIMARY KEY REFERENCES quote_quality_runs (id) ON DELETE CASCADE
);

INSERT INTO tv_display_quality_runs (run_id)
SELECT quality_run_id FROM tv_display_settings WHERE quality_run_id IS NOT NULL;

ALTER TABLE tv_display_settings DROP COLUMN quality_run_id;
