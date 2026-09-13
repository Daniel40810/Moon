-- =====================================================================
-- FMoon  00_drop.sql
-- Raeumt AUSSCHLIESSLICH das FMN-Schema ab.
--
-- Im Schema DEMO liegt daneben das FCF-Schema von FCurvedField. Dieses
-- Skript fasst nichts an, was nicht mit FMN_ beginnt.
--
-- Jedes Skript dieser Sammlung wird von FMoonDbSetup an Zeilen getrennt,
-- die NUR einen Schraegstrich enthalten. Deshalb endet hier jeder Block
-- mit einem solchen Schraegstrich, auch einfaches DDL.
-- =====================================================================

BEGIN
  -- Scheduler zuerst: ein laufender Job haelt sonst Objekte fest
  FOR j IN (SELECT job_name FROM user_scheduler_jobs
             WHERE job_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    BEGIN
      DBMS_SCHEDULER.drop_job(j.job_name, force => TRUE);
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;

  FOR p IN (SELECT program_name FROM user_scheduler_programs
             WHERE program_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    BEGIN
      DBMS_SCHEDULER.drop_program(p.program_name, force => TRUE);
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

BEGIN
  -- Materialized Views vor den Tabellen, auf denen sie stehen
  FOR m IN (SELECT mview_name FROM user_mviews
             WHERE mview_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW ' || m.mview_name;
  END LOOP;

  FOR l IN (SELECT log_table FROM user_mview_logs
             WHERE master LIKE 'FMN\_%' ESCAPE '\') LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW LOG ON '
              || REPLACE(l.log_table, 'MLOG$_', '');
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;

  FOR v IN (SELECT view_name FROM user_views
             WHERE view_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP VIEW ' || v.view_name;
  END LOOP;
END;
/

BEGIN
  -- Package, dann Tabellen, dann Sequenzen
  FOR o IN (SELECT object_name, object_type FROM user_objects
             WHERE object_name LIKE 'FMN\_%' ESCAPE '\'
               AND object_type IN ('PACKAGE', 'PROCEDURE', 'FUNCTION')) LOOP
    EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' ' || o.object_name;
  END LOOP;

  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
  END LOOP;

  FOR s IN (SELECT sequence_name FROM user_sequences
             WHERE sequence_name LIKE 'FMN\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
  END LOOP;
END;
/

BEGIN
  -- Objekttypen zuletzt und mit FORCE: Tabellenspalten koennen darauf
  -- aufbauen, und ein Typ mit abhaengigem Typ laesst sich sonst nicht
  -- loeschen (ORA-02303).
  FOR t IN (SELECT type_name FROM user_types
             WHERE type_name LIKE 'T\_FMN\_%' ESCAPE '\'
             ORDER BY typecode DESC) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TYPE ' || t.type_name || ' FORCE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

BEGIN
  -- Geo-Metadaten der eigenen Tabellen
  DELETE FROM user_sdo_geom_metadata WHERE table_name LIKE 'FMN\_%' ESCAPE '\';
  COMMIT;
END;
/
