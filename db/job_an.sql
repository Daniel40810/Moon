-- =====================================================================
-- FMoon  job_an.sql
-- Schaltet FMN_NIGHTLY_JOB ein.
--
-- ZWEI DINGE, DIE MAN VORHER WISSEN MUSS
--
-- 1. Der Job LAESST SICH SCHEITERN. Das ist Absicht: Findet die
--    naechtliche Pruefung Phasen oder Hochwasser ausserhalb der
--    Toleranz, wirft FMN_NIGHTLY einen Fehler, und der Lauf steht
--    danach in USER_SCHEDULER_JOB_RUN_DETAILS als FAILED. Ein Job, der
--    immer gruen ist, wird nicht angesehen. Nach vier gescheiterten
--    Laeufen in Folge schaltet Oracle ihn von selbst wieder ab
--    (MAX_FAILURES) - deshalb steht unten, wie man den Zustand ansieht.
--
-- 2. EIN NEUAUFBAU SCHALTET IHN WIEDER AUS. db_setup.bat spielt
--    09_scheduler.sql ein, und das legt den Job neu an - mit
--    enabled => FALSE. Das ist so gewollt: Ein Aufbaulauf soll nichts
--    anschalten, was nachts von selbst arbeitet. Nach jedem
--    db_setup.bat also wieder job.bat an.
--
-- Aufruf:  job.bat an
-- oder:    sqlplus demo/de@//localhost:1521/PDBORCL @db\job_an.sql
--
-- Rueckgaengig: job.bat aus
-- =====================================================================

SET SERVEROUTPUT ON
SET FEEDBACK OFF

DECLARE
  v_da      NUMBER;
  v_vorher  VARCHAR2(10);
  v_naechst TIMESTAMP WITH TIME ZONE;
BEGIN
  SELECT COUNT(*) INTO v_da FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  IF v_da = 0 THEN
    RAISE_APPLICATION_ERROR(-20030,
      'Job FMN_NIGHTLY_JOB gibt es nicht - erst db\09_scheduler.sql einspielen.');
  END IF;

  SELECT enabled INTO v_vorher FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';

  DBMS_SCHEDULER.enable('FMN_NIGHTLY_JOB');

  -- Nicht der Meldung glauben, sondern nachsehen. Ein eingeschalteter
  -- Job ohne naechsten Termin sieht eingeschaltet aus und laeuft nie.
  SELECT next_run_date INTO v_naechst FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  IF v_naechst IS NULL THEN
    RAISE_APPLICATION_ERROR(-20031,
      'Job ist eingeschaltet, hat aber keinen naechsten Termin - '
      || 'der Zeitplan stimmt nicht.');
  END IF;

  fmn_api.log_run('SCHEDULER',
    'Job FMN_NIGHTLY_JOB eingeschaltet (vorher ' || v_vorher
    || '), naechster Lauf ' || TO_CHAR(v_naechst, 'DD.MM.YYYY HH24:MI'), 1);
  COMMIT;

  DBMS_OUTPUT.put_line('Job FMN_NIGHTLY_JOB ist eingeschaltet (vorher: '
    || v_vorher || ').');
  DBMS_OUTPUT.put_line('Naechster Lauf: '
    || TO_CHAR(v_naechst, 'DD.MM.YYYY HH24:MI:SS TZR'));
END;
/

PROMPT
PROMPT --- ein Lauf sofort von Hand, damit nicht erst morgen frueh
PROMPT --- herauskommt, ob er durchlaeuft ---

DECLARE
  v_status VARCHAR2(30);
  v_info   VARCHAR2(4000);
BEGIN
  -- use_current_session => TRUE laesst den Fehler hier sichtbar werden
  -- statt nur in einer Protokolltabelle.
  DBMS_SCHEDULER.run_job('FMN_NIGHTLY_JOB', use_current_session => TRUE);
  DBMS_OUTPUT.put_line('Lauf von Hand: durchgelaufen.');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.put_line('Lauf von Hand GESCHEITERT:');
    DBMS_OUTPUT.put_line('  ' || SQLERRM);
    DBMS_OUTPUT.put_line('Das ist kein Grund zur Panik - die naechtliche');
    DBMS_OUTPUT.put_line('Pruefung MELDET Auffaelligkeiten so. Was gefunden');
    DBMS_OUTPUT.put_line('wurde, steht in FMN_RUN_LOG (action = NIGHTLY).');
END;
/

PROMPT
@@job_status.sql
