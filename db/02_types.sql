-- =====================================================================
-- FMoon  02_types.sql
-- Objekttypen.
--
-- ACHTUNG (teuer gelernt bei FCurvedField): DROP TYPE ... FORCE macht
-- jedes Package ungueltig, das den Typ benutzt. Dieses Skript und
-- 06_api.sql/07_api_body.sql gehoeren deshalb immer zusammen eingespielt.
-- Das Skript raeumt vor sich selbst auf und ist damit einzeln
-- wiederholbar.
-- =====================================================================

BEGIN
  FOR t IN (SELECT type_name FROM user_types
             WHERE type_name IN ('T_FMN_TIDE_TAB', 'T_FMN_TIDE_OBJ',
                                 'T_FMN_EVENT_TAB', 'T_FMN_EVENT_OBJ',
                                 'T_FMN_COAST_TAB', 'T_FMN_COAST_OBJ')) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TYPE ' || t.type_name || ' FORCE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

-- Ein Gezeitenereignis als Objekt, mit Rechenvorschriften daran.
CREATE OR REPLACE TYPE t_fmn_tide_obj AS OBJECT (
  station_code    VARCHAR2(30),
  event_at        TIMESTAMP,
  is_high_water   VARCHAR2(1),
  level_m         NUMBER,
  range_m         NUMBER,
  moon_transit_at TIMESTAMP,

  -- Die Hafenzeit, die aus diesem Ereignis folgt: der Abstand zwischen
  -- Monddurchgang und Hochwasser. Rueckwaerts gerechnet - so laesst sich
  -- pruefen, ob die Kette haelt.
  MEMBER FUNCTION harbour_interval_h RETURN NUMBER,

  -- Wasserstand zu einem beliebigen Zeitpunkt zwischen diesem und dem
  -- naechsten Ereignis, nach der Kosinusregel (das ist die alte
  -- Zwoelftelregel der Seefahrt ohne die Rundung auf Zwoelftel).
  MEMBER FUNCTION level_between(p_next IN t_fmn_tide_obj, p_at IN TIMESTAMP)
    RETURN NUMBER,

  MEMBER FUNCTION to_text RETURN VARCHAR2
)
/

CREATE OR REPLACE TYPE BODY t_fmn_tide_obj AS

  MEMBER FUNCTION harbour_interval_h RETURN NUMBER IS
    v_diff INTERVAL DAY TO SECOND;
  BEGIN
    IF moon_transit_at IS NULL OR is_high_water <> 'J' THEN
      RETURN NULL;
    END IF;
    v_diff := event_at - moon_transit_at;
    RETURN EXTRACT(DAY FROM v_diff) * 24
         + EXTRACT(HOUR FROM v_diff)
         + EXTRACT(MINUTE FROM v_diff) / 60
         + EXTRACT(SECOND FROM v_diff) / 3600;
  END harbour_interval_h;

  MEMBER FUNCTION level_between(p_next IN t_fmn_tide_obj, p_at IN TIMESTAMP)
    RETURN NUMBER IS
    v_gesamt NUMBER;
    v_bis    NUMBER;
    v_t      NUMBER;
    v_d1     INTERVAL DAY TO SECOND;
    v_d2     INTERVAL DAY TO SECOND;
  BEGIN
    IF p_next IS NULL OR p_at IS NULL THEN
      RETURN level_m;
    END IF;
    v_d1 := p_next.event_at - event_at;
    v_d2 := p_at - event_at;
    v_gesamt := EXTRACT(DAY FROM v_d1) * 86400 + EXTRACT(HOUR FROM v_d1) * 3600
              + EXTRACT(MINUTE FROM v_d1) * 60 + EXTRACT(SECOND FROM v_d1);
    v_bis    := EXTRACT(DAY FROM v_d2) * 86400 + EXTRACT(HOUR FROM v_d2) * 3600
              + EXTRACT(MINUTE FROM v_d2) * 60 + EXTRACT(SECOND FROM v_d2);
    IF v_gesamt <= 0 THEN
      RETURN level_m;
    END IF;
    v_t := v_bis / v_gesamt;
    IF v_t < 0 THEN
      v_t := 0;
    ELSIF v_t > 1 THEN
      v_t := 1;
    END IF;
    RETURN level_m + (p_next.level_m - level_m)
           * 0.5 * (1 - COS(3.141592653589793 * v_t));
  END level_between;

  MEMBER FUNCTION to_text RETURN VARCHAR2 IS
  BEGIN
    RETURN station_code || ' '
        || CASE is_high_water WHEN 'J' THEN 'HW' ELSE 'NW' END || ' '
        || TO_CHAR(event_at, 'DD.MM.YYYY HH24:MI') || ' '
        || TO_CHAR(level_m, 'S9990.00') || ' m';
  END to_text;

END;
/

CREATE OR REPLACE TYPE t_fmn_tide_tab AS TABLE OF t_fmn_tide_obj
/

-- Ein Mondereignis als Objekt.
CREATE OR REPLACE TYPE t_fmn_event_obj AS OBJECT (
  event_type   VARCHAR2(20),
  event_at     TIMESTAMP,
  jde          NUMBER,
  distance_km  NUMBER,
  illum_frac   NUMBER,

  -- Deutscher Name der Phase, damit die Uebersetzung an einer Stelle steht.
  MEMBER FUNCTION display_name RETURN VARCHAR2,

  -- Ist das ein Supermond? Vollmond naeher als 360000 km - die Grenze ist
  -- Vereinbarung, keine Definition, und steht deshalb hier sichtbar.
  MEMBER FUNCTION is_supermoon RETURN VARCHAR2
)
/

CREATE OR REPLACE TYPE BODY t_fmn_event_obj AS

  MEMBER FUNCTION display_name RETURN VARCHAR2 IS
  BEGIN
    RETURN CASE event_type
             WHEN 'NEW_MOON'      THEN 'Neumond'
             WHEN 'FIRST_QUARTER' THEN 'erstes Viertel'
             WHEN 'FULL_MOON'     THEN 'Vollmond'
             WHEN 'LAST_QUARTER'  THEN 'letztes Viertel'
             WHEN 'PERIGEE'       THEN 'Erdnaehe'
             WHEN 'APOGEE'        THEN 'Erdferne'
             ELSE event_type
           END;
  END display_name;

  MEMBER FUNCTION is_supermoon RETURN VARCHAR2 IS
  BEGIN
    IF event_type = 'FULL_MOON' AND distance_km IS NOT NULL
       AND distance_km < 360000 THEN
      RETURN 'J';
    END IF;
    RETURN 'N';
  END is_supermoon;

END;
/

CREATE OR REPLACE TYPE t_fmn_event_tab AS TABLE OF t_fmn_event_obj
/

-- ---------------------------------------------------------------------
-- Ein Punkt eines Kuestenumrisses
--
-- Bewusst flach: Die Karte bekommt Zahlen, keine Geometrie. So kommt der
-- Java-Teil ohne oracle.*-Klassen aus - der Treiber wird nur zur
-- Laufzeit gebraucht, nicht zum Uebersetzen.
--
-- Der Typ steht HIER und nicht bei der Kueste in 12_coast.sql, obwohl er
-- nur dort gebraucht wird. Grund, teuer gelernt: FMN_API nennt ihn in
-- seiner Spezifikation, und die wird in 06 eingespielt. Ein Typ, der
-- erst in 12 entsteht, kommt sechs Skripte zu spaet - die Spezifikation
-- endet mit PLS-00201, der Rumpf mit PLS-00905, und alles Weitere
-- faellt hinterher. Typen gehoeren in 02, ausnahmslos.
-- ---------------------------------------------------------------------
CREATE OR REPLACE TYPE t_fmn_coast_obj AS OBJECT (
  feature_id NUMBER,
  ring_no    NUMBER,
  point_no   NUMBER,
  lon        NUMBER,
  lat        NUMBER
)
/

CREATE OR REPLACE TYPE t_fmn_coast_tab AS TABLE OF t_fmn_coast_obj
/
