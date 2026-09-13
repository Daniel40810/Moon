-- =====================================================================
-- FMoon  job_jetzt.sql
-- Laesst die naechtliche Pflege einmal von Hand durchlaufen.
-- Aendert nichts am Ein-/Ausschaltzustand.
--
-- Aufruf:  job.bat jetzt
-- =====================================================================

SET SERVEROUTPUT ON
SET FEEDBACK OFF

BEGIN
  -- Nicht ueber den Scheduler, sondern die Prozedur unmittelbar: Sie
  -- laeuft dann auch, wenn der Job ausgeschaltet ist, und ein Fehler
  -- steht hier auf dem Schirm statt nur in einer Protokolltabelle.
  fmn_nightly;
  DBMS_OUTPUT.put_line('Naechtliche Pflege durchgelaufen, nichts auffaellig.');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.put_line('Naechtliche Pflege meldet:');
    DBMS_OUTPUT.put_line('  ' || SQLERRM);
END;
/

PROMPT
PROMPT --- was dabei herauskam ---
SET FEEDBACK ON
COLUMN detail FORMAT A70
SELECT TO_CHAR(run_at, 'DD.MM.YYYY HH24:MI') AS wann, detail, rows_done
  FROM fmn_run_log
 WHERE action = 'NIGHTLY'
 ORDER BY run_at DESC
 FETCH FIRST 3 ROWS ONLY
/
