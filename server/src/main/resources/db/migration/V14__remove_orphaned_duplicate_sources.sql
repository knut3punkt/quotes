-- Before V13 backfilled translation onto the pre-existing scripture sources, re-running an
-- import for one of them (translation now part of findOrCreateSource's match) failed to find the
-- legacy row and created a second one instead. Nothing was ever staged against the new row (every
-- candidate from that re-run was already staged from the earlier import and skipped as a
-- duplicate), so it's safe to drop: unreferenced, and a strictly-newer duplicate of an
-- already-referenced row with the same (title, type_code, translation).
DELETE FROM sources s
WHERE NOT EXISTS (SELECT 1 FROM imported_quotes iq WHERE iq.source_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM quotes q WHERE q.source_id = s.id)
  AND EXISTS (
      SELECT 1 FROM sources s2
      WHERE s2.title = s.title
        AND s2.type_code = s.type_code
        AND s2.translation IS NOT DISTINCT FROM s.translation
        AND s2.id < s.id
  );
