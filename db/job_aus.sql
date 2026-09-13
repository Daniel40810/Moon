-- =====================================================================
-- FMoon  job_aus.sql
-- Schaltet FMN_NIGHTLY_JOB wieder aus.
--
-- Der Job bleibt angelegt, er laeuft nur nicht mehr von selbst. Von
-- Hand geht er weiterhin:  job.bat jetzt
--
-- Aufruf:  job.bat aus
-- oder:    sqlplus demo/de@//localhost:1521/PDBORCL @db\job_aus.sql
-- =====================================================================

SET SERVEROUTPUT ON
SET FEEDBACK OFF

DECLARE
  v_da     NUMBER;
  v_vorher VARCHAR2(10);
  v_nach   VARCHAR2(10);
BEGIN
  SELECT COUNT(*) INTO v_da FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  IF v_da = 0 THEN
    DBMS_OUTPUT.put_line('Job FMN_NIGHTLY_JOB gibt es nicht - nichts zu tun.');
    RETURN;
  END IF;

  SELECT enabled INTO v_vorher FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';

  -- force => TRUE, damit auch ein gerade laufender Job abgeschaltet
  -- werden kann; sonst bleibt genau der Fall haengen, in dem man es
  -- wirklich eilig hat.
  DBMS_SCHEDULER.disable('FMN_NIGHTLY_JOB', force => TRUE);

  SELECT enabled INTO v_nach FROM user_scheduler_jobs
   WHERE job_name = 'FMN_NIGHTLY_JOB';
  IF v_nach <> 'FALSE' THEN
    RAISE_APPLICATION_ERROR(-20032,
      'Abschalten hat nicht gegriffen - Job steht weiter auf ' || v_nach || '.');
  END IF;

  fmn_api.log_run('SCHEDULER',
    'Job FMN_NIGHTLY_JOB ausgeschaltet (vorher ' || v_vorher || ')', 1);
  COMMIT;

  DBMS_OUTPUT.put_line('Job FMN_NIGHTLY_JOB ist ausgeschaltet (vorher: '
    || v_vorher || ').');
  DBMS_OUTPUT.put_line('Er bleibt angelegt; von Hand: job.bat jetzt');
END;
/

PROMPT
@@job_status.sql
