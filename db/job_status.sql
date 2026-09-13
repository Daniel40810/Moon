-- =====================================================================
-- FMoon  job_status.sql
-- Zeigt, wie es um FMN_NIGHTLY_JOB steht. Aendert nichts.
--
-- Diese drei Skripte (job_status, job_an, job_aus) tragen mit Absicht
-- KEINE Nummer: Nummern sagen "gehoert in die Aufbaureihenfolge", und
-- das tun sie nicht. Sie stehen auch nicht in FMoonDbSetup.STANDARD -
-- ein Stapellauf soll nichts einschalten, was von selbst nachts
-- arbeitet.
--
--   job.bat            zeigt diesen Stand
--   job.bat an         schaltet ein
--   job.bat aus        schaltet aus
--   job.bat jetzt      laesst einen Lauf von Hand durchlaufen
-- =====================================================================

SET SERVEROUTPUT ON
SET LINESIZE 200
SET FEEDBACK OFF

DECLARE
  v_da      NUMBER;
  v_an      VARCHAR2(10);
  v_naechst TIMESTAMP WITH TIME ZONE;
  v_intv    VARCHAR2(200);
  v_laeufe  NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_da FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  IF v_da = 0 THEN
    DBMS_OUTPUT.put_line('Job FMN_NIGHTLY_JOB gibt es nicht.');
    DBMS_OUTPUT.put_line('Erst db\09_scheduler.sql einspielen (oder db_setup.bat).');
    RETURN;
  END IF;

  SELECT enabled, next_run_date, repeat_interval
    INTO v_an, v_naechst, v_intv
    FROM user_scheduler_jobs WHERE job_name = 'FMN_NIGHTLY_JOB';

  DBMS_OUTPUT.put_line('Job FMN_NIGHTLY_JOB');
  DBMS_OUTPUT.put_line('  eingeschaltet : ' || v_an);
  DBMS_OUTPUT.put_line('  Zeitplan      : ' || v_intv);
  -- Ein eingeschalteter Job OHNE naechsten Termin ist der Fall, den man
  -- sehen will: Er sieht an jeder Anzeige eingeschaltet aus und laeuft
  -- trotzdem nie.
  IF v_naechst IS NULL THEN
    DBMS_OUTPUT.put_line('  naechster Lauf: KEINER');
    IF v_an = 'TRUE' THEN
      DBMS_OUTPUT.put_line('  ACHTUNG: eingeschaltet, aber ohne Termin.');
    END IF;
  ELSE
    DBMS_OUTPUT.put_line('  naechster Lauf: '
      || TO_CHAR(v_naechst, 'DD.MM.YYYY HH24:MI:SS TZR'));
  END IF;

  SELECT COUNT(*) INTO v_laeufe FROM user_scheduler_job_run_details
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  DBMS_OUTPUT.put_line('  bisherige Laeufe: ' || v_laeufe);
END;
/

PROMPT
PROMPT --- die letzten fuenf Laeufe ---
COLUMN status FORMAT A12
COLUMN wann FORMAT A22
COLUMN dauer FORMAT A12
COLUMN fehler FORMAT A60
SELECT status,
       TO_CHAR(actual_start_date, 'DD.MM.YYYY HH24:MI:SS') AS wann,
       TO_CHAR(run_duration) AS dauer,
       SUBSTR(additional_info, 1, 60) AS fehler
  FROM user_scheduler_job_run_details
 WHERE job_name = 'FMN_NIGHTLY_JOB'
 ORDER BY actual_start_date DESC
 FETCH FIRST 5 ROWS ONLY
/

PROMPT
PROMPT --- die letzten fuenf Protokollzeilen der Pflege ---
COLUMN detail FORMAT A70
SELECT TO_CHAR(run_at, 'DD.MM.YYYY HH24:MI') AS wann, detail, rows_done
  FROM fmn_run_log
 WHERE action = 'NIGHTLY'
 ORDER BY run_at DESC
 FETCH FIRST 5 ROWS ONLY
/

SET FEEDBACK ON
