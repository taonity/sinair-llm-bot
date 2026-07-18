-- Postgres-only recovery.
--
-- The room_summary.summary column was briefly mapped with @Lob on a String. On Postgres that
-- stores the text as a Large Object and leaves only its OID (a short integer, rendered as a
-- 5-digit number) in the TEXT column, so the console shows the OID instead of the summary.
-- @Lob has since been removed, so new writes store plain text; this migration repairs the rows
-- written while @Lob was active.
--
-- For each affected row (summary is a short all-digit string = a Large Object OID) we:
--   1. read the text back out of the Large Object and store it in the column, then unlink the LO;
--   2. if the Large Object is gone or unreadable, reset the row so the bot rebuilds the summary.
--
-- H2 (local) never stored OIDs, so this migration lives only in the Postgres location.
DO $$
DECLARE
    r         RECORD;
    lo_oid    oid;
    recovered text;
BEGIN
    FOR r IN
        SELECT id, summary
        FROM room_summary
        WHERE summary ~ '^[0-9]+$'
          AND length(summary) <= 12
    LOOP
        BEGIN
            lo_oid := r.summary::oid;
            recovered := convert_from(lo_get(lo_oid), 'UTF8');
        EXCEPTION WHEN OTHERS THEN
            recovered := NULL;
        END;

        IF recovered IS NOT NULL THEN
            UPDATE room_summary SET summary = recovered WHERE id = r.id;
            BEGIN
                PERFORM lo_unlink(lo_oid);
            EXCEPTION WHEN OTHERS THEN
                NULL; -- best-effort cleanup of the now-unreferenced Large Object
            END;
        ELSE
            -- Large Object missing/unreadable: clear the summary and reset the refresh counter
            -- so the next incoming message regenerates a fresh summary from recent transcript.
            UPDATE room_summary SET summary = '', message_count = 0 WHERE id = r.id;
        END IF;
    END LOOP;
END $$;
