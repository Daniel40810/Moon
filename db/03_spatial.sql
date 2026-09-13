-- =====================================================================
-- FMoon  03_spatial.sql
-- Geo-Metadaten und raeumlicher Index fuer FMN_STATION.
--
-- SRID 8307 ist WGS 84. Damit rechnet Oracle GEODAETISCH: Entfernungen
-- kommen in echten Metern heraus, nicht in Gradeinheiten. Genau das
-- wird gebraucht, um die Laufzeit der Tidewelle zwischen zwei Pegeln
-- zu bestimmen.
--
-- Das Skript raeumt vor sich selbst auf und ist einzeln wiederholbar.
-- =====================================================================

BEGIN
  DELETE FROM user_sdo_geom_metadata WHERE table_name = 'FMN_STATION';
  COMMIT;
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX fmn_station_sx';
  EXCEPTION WHEN OTHERS THEN NULL;
  END;
END;
/

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('FMN_STATION', 'GEOM',
        SDO_DIM_ARRAY(
          -- Toleranz 0,5 m: feiner als jede Pegelangabe, grob genug,
          -- dass zwei Messungen desselben Ortes zusammenfallen.
          SDO_DIM_ELEMENT('LONGITUDE', -180, 180, 0.5),
          SDO_DIM_ELEMENT('LATITUDE',   -90,  90, 0.5)),
        8307)
/

COMMIT
/

CREATE INDEX fmn_station_sx ON fmn_station (geom)
  INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2
/
