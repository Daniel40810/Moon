-- =====================================================================
-- FMoon  09_scheduler.sql
-- Der Scheduler: naechtliche Pflege.
--
-- Was der Job NICHT tut: Mondereignisse rechnen. Die vollen Reihen
-- stehen in Java, und sie sollen nicht ein zweites Mal in PL/SQL
-- gepflegt werden. Der Job haelt die Datenbank in Ordnung - er frischt
-- die Jahresuebersicht auf, prueft die eingetragenen Ereignisse gegen
-- die mittlere Phase und raeumt alte Protokollzeilen weg.
--
-- Das Skript raeumt vor sich selbst auf und ist einzeln wiederholbar.
-- =====================================================================

BEGIN
  FOR j IN (SELECT job_name FROM user_scheduler_jobs
             WHERE job_name IN ('FMN_NIGHTLY_JOB')) LOOP
    BEGIN
      DBMS_SCHEDULER.drop_job(j.job_name, force => TRUE);
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
  FOR p IN (SELECT program_name FROM user_scheduler_programs
             WHERE program_name IN ('FMN_NIGHTLY_PRG')) LOOP
    BEGIN
      DBMS_SCHEDULER.drop_program(p.program_name, force => TRUE);
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

CREATE OR REPLACE PROCEDURE fmn_nightly AS
  v_schlecht NUMBER;
  v_tiden    NUMBER;
  v_weg      NUMBER;
BEGIN
  -- 1. Jahresuebersicht auffrischen
  DBMS_MVIEW.refresh('FMN_YEAR_MV', 'C');

  -- 2. Die eingetragenen Phasen gegen die mittlere Phase pruefen.
  --    Das ist die unabhaengige Kontrolle: Java rechnet, die Datenbank
  --    sieht nach, ob das Ergebnis ueberhaupt plausibel ist.
  --
  --    Ohne Argument: Die Toleranz steht als FMN_API.C_PHASE_TOL_H an
  --    genau einer Stelle, mit der Begruendung daneben. Sie hier noch
  --    einmal hinzuschreiben waere die zweite Wahrheit, die in diesem
  --    Projekt schon dreimal Geld gekostet hat.
  v_schlecht := fmn_api.verify_events;

  -- 3. Die Hochwasser gegen die Hafenzeit ihrer Station pruefen.
  v_tiden := fmn_api.verify_tides(1);

  -- 4. Protokollzeilen aelter als ein Jahr wegraeumen
  --
  --    INTERVAL '365' DAY waere falsch: ohne Angabe hat ein Tagesintervall
  --    zwei fuehrende Stellen, 365 passt nicht hinein (ORA-01873). Ein
  --    Jahresintervall sagt ohnehin besser, was gemeint ist.
  DELETE FROM fmn_run_log WHERE run_at < SYSTIMESTAMP - INTERVAL '1' YEAR;
  v_weg := SQL%ROWCOUNT;

  fmn_api.log_run('NIGHTLY',
    'Phasen ausserhalb der Toleranz: ' || v_schlecht
    || ', Hochwasser ausserhalb der Hafenzeit: ' || v_tiden
    || ', alte Protokollzeilen entfernt: ' || v_weg,
    v_schlecht + v_tiden);

  COMMIT;

  -- Auffaelligkeiten sollen nicht nur im Protokoll stehen, sondern den
  -- Job scheitern lassen - ein Job, der immer gruen ist, wird nicht
  -- angesehen.
  IF v_schlecht > 0 OR v_tiden > 0 THEN
    RAISE_APPLICATION_ERROR(-20020,
      'FMoon naechtliche Pruefung: ' || v_schlecht
      || ' Phasen und ' || v_tiden || ' Hochwasser auffaellig.');
  END IF;
END fmn_nightly;
/

BEGIN
  DBMS_SCHEDULER.create_program(
    program_name   => 'FMN_NIGHTLY_PRG',
    program_type   => 'STORED_PROCEDURE',
    program_action => 'FMN_NIGHTLY',
    number_of_arguments => 0,
    enabled        => TRUE,
    comments       => 'FMoon: Jahresuebersicht auffrischen und pruefen');
END;
/

BEGIN
  DBMS_SCHEDULER.create_job(
    job_name        => 'FMN_NIGHTLY_JOB',
    program_name    => 'FMN_NIGHTLY_PRG',
    start_date      => SYSTIMESTAMP + INTERVAL '1' HOUR,
    -- 03:20 Uhr: nach dem gewoehnlichen Sicherungsfenster. Die zwanzig
    -- Minuten sind Absicht - zur vollen Stunde startet in jeder
    -- Datenbank irgendetwas.
    repeat_interval => 'FREQ=DAILY; BYHOUR=3; BYMINUTE=20; BYSECOND=0',
    enabled         => FALSE,
    auto_drop       => FALSE,
    comments        => 'FMoon: naechtliche Pflege');
END;
/

-- Der Job wird hier NICHT eingeschaltet, und das bleibt so. Ein
-- Aufbaulauf soll nichts anschalten, was danach jede Nacht von selbst
-- arbeitet - wer das will, sagt es eigens:
--
--   job.bat an       schaltet ein und laesst gleich einen Lauf durch
--   job.bat aus      schaltet wieder aus
--   job.bat jetzt    ein Lauf von Hand
--   job.bat          zeigt nur den Stand
--
-- Die Kehrseite steht dort auch: Wer db_setup.bat laufen laesst,
-- bekommt diesen Block wieder, und damit steht der Job wieder auf
-- FALSE. Das ist kein Versehen, sondern dieselbe Entscheidung noch
-- einmal.

BEGIN
  fmn_api.log_run('SCHEDULER', 'Job FMN_NIGHTLY_JOB angelegt, nicht eingeschaltet - job.bat an schaltet ein');
  COMMIT;
END;
/
