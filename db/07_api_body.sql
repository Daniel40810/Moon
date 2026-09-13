-- =====================================================================
-- FMoon  07_api_body.sql
-- Die Schnittstelle: Package-RUMPF.
--
-- Gehoert immer zusammen mit 06_api.sql eingespielt.
-- =====================================================================

CREATE OR REPLACE PACKAGE BODY fmn_api AS

  ---------------------------------------------------------------------
  -- Zeitrechnung
  ---------------------------------------------------------------------

  FUNCTION to_jd(p_ts IN TIMESTAMP) RETURN NUMBER DETERMINISTIC IS
    v_tage NUMBER;
  BEGIN
    IF p_ts IS NULL THEN
      RETURN NULL;
    END IF;
    -- Tage seit der Unix-Epoche, Sekundenbruchteile eingeschlossen
    v_tage := (CAST(p_ts AS DATE) - DATE '1970-01-01')
            + (EXTRACT(SECOND FROM p_ts) - TRUNC(EXTRACT(SECOND FROM p_ts)))
              / 86400;
    RETURN c_jd_unix + v_tage;
  END to_jd;

  FUNCTION to_ts(p_jd IN NUMBER) RETURN TIMESTAMP DETERMINISTIC IS
  BEGIN
    IF p_jd IS NULL THEN
      RETURN NULL;
    END IF;
    RETURN CAST(DATE '1970-01-01' AS TIMESTAMP)
           + NUMTODSINTERVAL((p_jd - c_jd_unix) * 86400, 'SECOND');
  END to_ts;

  ---------------------------------------------------------------------
  -- Mittlere Phase
  ---------------------------------------------------------------------

  FUNCTION mean_phase_jde(p_k IN NUMBER) RETURN NUMBER DETERMINISTIC IS
    v_t NUMBER;
  BEGIN
    -- Meeus, Astronomical Algorithms, Formel 49.1. Ohne jede der
    -- periodischen Korrekturen - die stehen in Java.
    v_t := p_k / 1236.85;
    RETURN 2451550.09766
         + c_synodic_month * p_k
         + 0.00015437 * v_t * v_t
         - 0.000000150 * v_t * v_t * v_t
         + 0.00000000073 * v_t * v_t * v_t * v_t;
  END mean_phase_jde;

  FUNCTION phase_offset(p_type IN VARCHAR2) RETURN NUMBER IS
  BEGIN
    RETURN CASE p_type
             WHEN 'NEW_MOON'      THEN 0
             WHEN 'FIRST_QUARTER' THEN 0.25
             WHEN 'FULL_MOON'     THEN 0.5
             WHEN 'LAST_QUARTER'  THEN 0.75
             ELSE NULL
           END;
  END phase_offset;

  FUNCTION nearest_k(p_ts IN TIMESTAMP, p_type IN VARCHAR2) RETURN NUMBER IS
    v_off  NUMBER;
    v_jd   NUMBER;
    v_grob NUMBER;
  BEGIN
    v_off := phase_offset(p_type);
    IF v_off IS NULL THEN
      RETURN NULL;
    END IF;
    v_jd := to_jd(p_ts);
    -- k aus der mittleren Phase zurueckgerechnet und auf das Raster
    -- der gesuchten Phasenart gerundet
    v_grob := (v_jd - 2451550.09766) / c_synodic_month;
    RETURN ROUND(v_grob - v_off) + v_off;
  END nearest_k;

  FUNCTION mean_deviation_h(p_ts IN TIMESTAMP, p_type IN VARCHAR2)
    RETURN NUMBER IS
    v_k NUMBER;
  BEGIN
    v_k := nearest_k(p_ts, p_type);
    IF v_k IS NULL THEN
      RETURN NULL;
    END IF;
    RETURN (to_jd(p_ts) - mean_phase_jde(v_k)) * 24;
  END mean_deviation_h;

  ---------------------------------------------------------------------
  -- Schreiben
  ---------------------------------------------------------------------

  PROCEDURE add_event(p_type        IN VARCHAR2,
                      p_at          IN TIMESTAMP,
                      p_jde         IN NUMBER   DEFAULT NULL,
                      p_distance_km IN NUMBER   DEFAULT NULL,
                      p_illum       IN NUMBER   DEFAULT NULL) IS
    v_dev NUMBER;
  BEGIN
    v_dev := mean_deviation_h(p_at, p_type);
    MERGE INTO fmn_event e
    USING (SELECT p_type AS t, p_at AS a FROM dual) q
       ON (e.event_type = q.t AND e.event_at = q.a)
     WHEN MATCHED THEN UPDATE SET e.jde = p_jde,
                                  e.distance_km = p_distance_km,
                                  e.illum_frac = p_illum,
                                  e.mean_dev_h = v_dev
     WHEN NOT MATCHED THEN
       INSERT (event_type, event_at, jde, distance_km, illum_frac, mean_dev_h)
       VALUES (p_type, p_at, p_jde, p_distance_km, p_illum, v_dev);
  END add_event;

  PROCEDURE clear_events(p_from IN TIMESTAMP DEFAULT NULL,
                         p_to   IN TIMESTAMP DEFAULT NULL) IS
  BEGIN
    DELETE FROM fmn_event
     WHERE (p_from IS NULL OR event_at >= p_from)
       AND (p_to   IS NULL OR event_at <= p_to);
  END clear_events;

  FUNCTION station_id_of(p_code IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT station_id INTO v_id FROM fmn_station WHERE code = p_code;
    RETURN v_id;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RAISE_APPLICATION_ERROR(-20011, 'Unbekannte Station: ' || p_code);
  END station_id_of;

  PROCEDURE add_tide(p_station_code  IN VARCHAR2,
                     p_at            IN TIMESTAMP,
                     p_high_water    IN VARCHAR2,
                     p_level_m       IN NUMBER,
                     p_range_m       IN NUMBER,
                     p_transit_at    IN TIMESTAMP DEFAULT NULL,
                     p_upper_transit IN VARCHAR2  DEFAULT NULL) IS
    v_id NUMBER;
  BEGIN
    v_id := station_id_of(p_station_code);
    MERGE INTO fmn_tide t
    USING (SELECT v_id AS s, p_at AS a FROM dual) q
       ON (t.station_id = q.s AND t.event_at = q.a)
     WHEN MATCHED THEN UPDATE SET t.is_high_water = p_high_water,
                                  t.level_m = p_level_m,
                                  t.range_m = p_range_m,
                                  t.moon_transit_at = p_transit_at,
                                  t.upper_transit = p_upper_transit
     WHEN NOT MATCHED THEN
       INSERT (station_id, event_at, is_high_water, level_m, range_m,
               moon_transit_at, upper_transit)
       VALUES (v_id, p_at, p_high_water, p_level_m, p_range_m,
               p_transit_at, p_upper_transit);
  END add_tide;

  PROCEDURE clear_tides(p_station_code IN VARCHAR2 DEFAULT NULL,
                        p_from         IN TIMESTAMP DEFAULT NULL,
                        p_to           IN TIMESTAMP DEFAULT NULL) IS
    v_id NUMBER;
  BEGIN
    IF p_station_code IS NOT NULL THEN
      v_id := station_id_of(p_station_code);
    END IF;
    DELETE FROM fmn_tide
     WHERE (v_id IS NULL OR station_id = v_id)
       AND (p_from IS NULL OR event_at >= p_from)
       AND (p_to   IS NULL OR event_at <= p_to);
  END clear_tides;

  PROCEDURE log_run(p_action IN VARCHAR2,
                    p_detail IN VARCHAR2 DEFAULT NULL,
                    p_rows   IN NUMBER   DEFAULT NULL) IS
  BEGIN
    INSERT INTO fmn_run_log (action, detail, rows_done)
    VALUES (p_action, p_detail, p_rows);
  END log_run;

  ---------------------------------------------------------------------
  -- Lesen
  ---------------------------------------------------------------------

  FUNCTION stations RETURN SYS_REFCURSOR IS
    v_cur SYS_REFCURSOR;
  BEGIN
    OPEN v_cur FOR
      SELECT code, name, water_body, latitude_deg, longitude_deg,
             elevation_m, time_zone_id, hw_interval_h, lw_offset_h,
             spring_range_m, neap_range_m, is_approx, source, source_date,
             hw_interval_sd_min, remark
        FROM fmn_station
       ORDER BY water_body, name;
    RETURN v_cur;
  END stations;

  FUNCTION coast_available RETURN VARCHAR2 IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_objects
     WHERE object_name = 'FMN_COAST_V' AND status = 'VALID';
    IF v_n = 0 THEN
      RETURN 'N';
    END IF;
    -- Vorhanden und gueltig heisst noch nicht lesbar: Ein zurueckgenom-
    -- menes Leserecht macht die Sicht nicht ungueltig, sondern laesst
    -- erst die Abfrage scheitern. Also einmal hineinsehen.
    BEGIN
      EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT 1 FROM fmn_coast_v '
                        || 'WHERE ROWNUM <= 1)' INTO v_n;
      RETURN 'J';
    EXCEPTION WHEN OTHERS THEN
      RETURN 'N';
    END;
  END coast_available;

  FUNCTION coast(p_min_lon      IN NUMBER,
                 p_min_lat      IN NUMBER,
                 p_max_lon      IN NUMBER,
                 p_max_lat      IN NUMBER,
                 p_tolerance_m  IN NUMBER DEFAULT 200)
    RETURN t_fmn_coast_tab PIPELINED IS

    v_cur      SYS_REFCURSOR;
    v_id       NUMBER;
    v_geom     SDO_GEOMETRY;
    v_fenster  SDO_GEOMETRY;
    v_ring     SDO_GEOMETRY;
    v_elem     NUMBER;
    v_punkte   NUMBER;
    v_ausgabe  NUMBER := 0;
  BEGIN
    IF coast_available <> 'J' THEN
      RETURN;
    END IF;

    -- Das Fenster als Rechteck. SDO_FILTER benutzt den raeumlichen
    -- Index von OGP - deshalb wird hier gefiltert und nicht erst in
    -- Java, wenn alle 1892 Flaechen schon ueber die Leitung sind.
    v_fenster := SDO_GEOMETRY(2003, 8307, NULL,
                   SDO_ELEM_INFO_ARRAY(1, 1003, 3),
                   SDO_ORDINATE_ARRAY(p_min_lon, p_min_lat,
                                      p_max_lon, p_max_lat));

    OPEN v_cur FOR
      'SELECT feature_id, geom FROM fmn_coast_v '
      || 'WHERE SDO_FILTER(geom, :1) = ''TRUE'''
      USING v_fenster;

    LOOP
      FETCH v_cur INTO v_id, v_geom;
      EXIT WHEN v_cur%NOTFOUND;
      IF v_geom IS NULL THEN
        CONTINUE;
      END IF;

      BEGIN
        v_elem := SDO_UTIL.GETNUMELEM(v_geom);
      EXCEPTION WHEN OTHERS THEN
        v_elem := 0;
      END;

      FOR i IN 1 .. NVL(v_elem, 0) LOOP
        BEGIN
          -- Ring 1 ist der AEUSSERE Ring des Elements. Die Loecher
          -- bleiben absichtlich weg: Fuer eine Kuestenlinie ist der
          -- aeussere Rand das Gesuchte, und dieselbe Abwaegung steht
          -- schon in FSpatialMap.
          v_ring := SDO_UTIL.EXTRACT(v_geom, i, 1);
          -- Toleranz in METERN: Bei SRID 8307 rechnet SIMPLIFY
          -- geodaetisch. Grad einzusetzen heisst, gar nicht zu
          -- vereinfachen.
          IF p_tolerance_m > 0 THEN
            v_ring := SDO_UTIL.SIMPLIFY(v_ring, p_tolerance_m);
          END IF;
        EXCEPTION WHEN OTHERS THEN
          v_ring := NULL;
        END;

        IF v_ring IS NOT NULL AND v_ring.sdo_ordinates IS NOT NULL THEN
          v_punkte := v_ring.sdo_ordinates.COUNT / 2;
          FOR k IN 1 .. v_punkte LOOP
            PIPE ROW (t_fmn_coast_obj(v_id, i, k,
                        v_ring.sdo_ordinates(2 * k - 1),
                        v_ring.sdo_ordinates(2 * k)));
            v_ausgabe := v_ausgabe + 1;
          END LOOP;
        END IF;
      END LOOP;
    END LOOP;
    CLOSE v_cur;
    RETURN;
  END coast;

  PROCEDURE set_harbour_time(p_code        IN VARCHAR2,
                             p_hw_interval IN NUMBER,
                             p_lw_offset   IN NUMBER DEFAULT NULL,
                             p_source      IN VARCHAR2,
                             p_source_date IN DATE    DEFAULT SYSDATE,
                             p_approx      IN VARCHAR2 DEFAULT 'N',
                             p_sd_min      IN NUMBER  DEFAULT NULL) IS
    v_alt_h      NUMBER;
    v_alt_approx VARCHAR2(1);
    v_name       VARCHAR2(80);
  BEGIN
    -- Erst nachsehen, ob es die Station ueberhaupt gibt. Ein UPDATE auf
    -- einen Tippfehler im Code trifft null Zeilen und meldet nichts -
    -- der Aufrufer glaubt dann, er haette etwas eingetragen.
    BEGIN
      SELECT name, hw_interval_h, is_approx
        INTO v_name, v_alt_h, v_alt_approx
        FROM fmn_station
       WHERE code = p_code;
    EXCEPTION WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20030,
        'Keine Station mit dem Code ' || p_code || '.');
    END;

    IF p_approx NOT IN ('J', 'N') THEN
      RAISE_APPLICATION_ERROR(-20031,
        'P_APPROX muss J oder N sein, nicht ' || p_approx || '.');
    END IF;

    IF p_approx = 'N' AND (p_source IS NULL OR TRIM(p_source) IS NULL) THEN
      RAISE_APPLICATION_ERROR(-20032,
        'Ein Wert ohne Naeherungskennzeichen braucht eine Quelle. '
        || 'Woher stammt die Hafenzeit fuer ' || v_name || '?');
    END IF;

    -- Plausibilitaet. Die Hafenzeit liegt zwischen null und einem halben
    -- Mondtag; alles darueber ist keine Hafenzeit, sondern ein Rechenfehler
    -- mit Vorzeichen oder eine Zeit, die versehentlich auf den Durchgang
    -- in Greenwich bezogen wurde.
    IF p_hw_interval IS NULL
       OR p_hw_interval < 0 OR p_hw_interval > 12.42 THEN
      RAISE_APPLICATION_ERROR(-20033,
        'Hafenzeit ' || p_hw_interval || ' h liegt ausserhalb von 0 bis '
        || '12,42 h (ein halber Mondtag).');
    END IF;

    IF p_lw_offset IS NOT NULL
       AND (p_lw_offset < 3 OR p_lw_offset > 9) THEN
      RAISE_APPLICATION_ERROR(-20034,
        'Niedrigwasserversatz ' || p_lw_offset || ' h ist unglaubwuerdig; '
        || 'erwartet werden 3 bis 9 h.');
    END IF;

    IF p_sd_min IS NOT NULL AND p_sd_min < 0 THEN
      RAISE_APPLICATION_ERROR(-20035, 'Eine Streuung kann nicht negativ sein.');
    END IF;

    UPDATE fmn_station
       SET hw_interval_h      = p_hw_interval,
           lw_offset_h        = NVL(p_lw_offset, lw_offset_h),
           source             = p_source,
           source_date        = p_source_date,
           hw_interval_sd_min = p_sd_min,
           is_approx          = p_approx
     WHERE code = p_code;

    log_run('HAFENZEIT',
      v_name || ': ' || TO_CHAR(v_alt_h) || ' h (' || v_alt_approx || ') -> '
      || TO_CHAR(p_hw_interval) || ' h (' || p_approx || '), Quelle: '
      || SUBSTR(p_source, 1, 120), 1);
  END set_harbour_time;

  FUNCTION tides(p_station_code IN VARCHAR2,
                 p_from         IN TIMESTAMP,
                 p_to           IN TIMESTAMP) RETURN t_fmn_tide_tab PIPELINED IS
  BEGIN
    FOR r IN (SELECT s.code, t.event_at, t.is_high_water, t.level_m,
                     t.range_m, t.moon_transit_at
                FROM fmn_tide t
                JOIN fmn_station s ON s.station_id = t.station_id
               WHERE s.code = p_station_code
                 AND (p_from IS NULL OR t.event_at >= p_from)
                 AND (p_to   IS NULL OR t.event_at <= p_to)
               ORDER BY t.event_at) LOOP
      PIPE ROW (t_fmn_tide_obj(r.code, r.event_at, r.is_high_water,
                               r.level_m, r.range_m, r.moon_transit_at));
    END LOOP;
    RETURN;
  END tides;

  FUNCTION events(p_from IN TIMESTAMP,
                  p_to   IN TIMESTAMP,
                  p_type IN VARCHAR2 DEFAULT NULL)
    RETURN t_fmn_event_tab PIPELINED IS
  BEGIN
    FOR r IN (SELECT event_type, event_at, jde, distance_km, illum_frac
                FROM fmn_event
               WHERE (p_from IS NULL OR event_at >= p_from)
                 AND (p_to   IS NULL OR event_at <= p_to)
                 AND (p_type IS NULL OR event_type = p_type)
               ORDER BY event_at) LOOP
      PIPE ROW (t_fmn_event_obj(r.event_type, r.event_at, r.jde,
                                r.distance_km, r.illum_frac));
    END LOOP;
    RETURN;
  END events;

  FUNCTION next_tide(p_station_code IN VARCHAR2,
                     p_at           IN TIMESTAMP,
                     p_high_water   IN VARCHAR2 DEFAULT 'J')
    RETURN t_fmn_tide_obj IS
    v_obj t_fmn_tide_obj;
  BEGIN
    SELECT t_fmn_tide_obj(s.code, t.event_at, t.is_high_water, t.level_m,
                          t.range_m, t.moon_transit_at)
      INTO v_obj
      FROM fmn_tide t
      JOIN fmn_station s ON s.station_id = t.station_id
     WHERE s.code = p_station_code
       AND t.event_at > p_at
       AND (p_high_water IS NULL OR t.is_high_water = p_high_water)
     ORDER BY t.event_at
     FETCH FIRST 1 ROW ONLY;
    RETURN v_obj;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RETURN NULL;
  END next_tide;

  ---------------------------------------------------------------------
  -- Raeumliches
  ---------------------------------------------------------------------

  FUNCTION distance_km(p_code_a IN VARCHAR2, p_code_b IN VARCHAR2)
    RETURN NUMBER IS
    v_m NUMBER;
  BEGIN
    -- Achtung: Attribute einer Objektspalte brauchen einen Tabellen-
    -- Alias, sonst ORA-00904. Hier werden ganze Geometrien uebergeben,
    -- aber die Aliase a und b stehen aus demselben Grund.
    SELECT SDO_GEOM.SDO_DISTANCE(a.geom, b.geom, 0.5, 'unit=M')
      INTO v_m
      FROM fmn_station a, fmn_station b
     WHERE a.code = p_code_a AND b.code = p_code_b;
    RETURN v_m / 1000;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RETURN NULL;
  END distance_km;

  FUNCTION nearest_station(p_lat IN NUMBER, p_lon IN NUMBER) RETURN VARCHAR2 IS
    v_code VARCHAR2(30);
  BEGIN
    SELECT code INTO v_code FROM (
      SELECT s.code,
             SDO_GEOM.SDO_DISTANCE(s.geom,
               SDO_GEOMETRY(2001, 8307,
                 SDO_POINT_TYPE(p_lon, p_lat, NULL), NULL, NULL),
               0.5, 'unit=M') AS d
        FROM fmn_station s
       ORDER BY d)
     WHERE ROWNUM = 1;
    RETURN v_code;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RETURN NULL;
  END nearest_station;

  FUNCTION wave_speed_ms(p_code_a IN VARCHAR2, p_code_b IN VARCHAR2)
    RETURN NUMBER IS
    v_km  NUMBER;
    v_ha  NUMBER;
    v_hb  NUMBER;
    v_dt  NUMBER;
  BEGIN
    v_km := distance_km(p_code_a, p_code_b);
    SELECT hw_interval_h INTO v_ha FROM fmn_station WHERE code = p_code_a;
    SELECT hw_interval_h INTO v_hb FROM fmn_station WHERE code = p_code_b;
    IF v_km IS NULL OR v_ha IS NULL OR v_hb IS NULL THEN
      RETURN NULL;
    END IF;
    v_dt := ABS(v_hb - v_ha) * 3600;
    IF v_dt <= 0 THEN
      RETURN NULL;
    END IF;
    RETURN (v_km * 1000) / v_dt;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RETURN NULL;
  END wave_speed_ms;

  ---------------------------------------------------------------------
  -- Messreihen
  ---------------------------------------------------------------------

  PROCEDURE add_gauge(p_station_code IN VARCHAR2,
                      p_at           IN DATE,
                      p_level_m      IN NUMBER,
                      p_source       IN VARCHAR2 DEFAULT NULL) IS
    v_id NUMBER;
  BEGIN
    v_id := station_id_of(p_station_code);
    MERGE INTO fmn_gauge g
    USING (SELECT v_id AS s, p_at AS a FROM dual) q
       ON (g.station_id = q.s AND g.measured_at = q.a)
     WHEN MATCHED THEN UPDATE SET g.level_m = p_level_m, g.source = p_source
     WHEN NOT MATCHED THEN
       INSERT (station_id, measured_at, level_m, source)
       VALUES (v_id, p_at, p_level_m, p_source);
  END add_gauge;

  PROCEDURE import_gauge(p_table      IN VARCHAR2,
                         p_code_col   IN VARCHAR2,
                         p_time_col   IN VARCHAR2,
                         p_level_col  IN VARCHAR2,
                         p_from       IN DATE DEFAULT NULL,
                         p_to         IN DATE DEFAULT NULL,
                         p_replace    IN VARCHAR2 DEFAULT 'N',
                         p_report     OUT VARCHAR2) IS
    v_tab    VARCHAR2(128);
    v_code   VARCHAR2(128);
    v_time   VARCHAR2(128);
    v_level  VARCHAR2(128);
    v_von    DATE;
    v_bis    DATE;
    v_quelle VARCHAR2(2000);
    v_zahl   NUMBER := 0;
    v_unbek  NUMBER := 0;
    v_weg    NUMBER := 0;
  BEGIN
    -- Bezeichner pruefen, keine freie WHERE-Bedingung
    v_tab   := DBMS_ASSERT.sql_object_name(p_table);
    v_code  := DBMS_ASSERT.simple_sql_name(p_code_col);
    v_time  := DBMS_ASSERT.simple_sql_name(p_time_col);
    v_level := DBMS_ASSERT.simple_sql_name(p_level_col);

    -- Offene Grenzen werden auf feste Werte gebracht, damit "kein
    -- Fenster" kein Sonderfall ist.
    v_von := NVL(TRUNC(p_from), TO_DATE('-4712-01-01', 'SYYYY-MM-DD'));
    v_bis := NVL(TRUNC(p_to), TO_DATE('9999-12-31', 'SYYYY-MM-DD'));
    IF v_von > v_bis THEN
      RAISE_APPLICATION_ERROR(-20012,
        'Zeitraum verkehrt herum: von ' || TO_CHAR(v_von, 'DD.MM.YYYY')
        || ' bis ' || TO_CHAR(v_bis, 'DD.MM.YYYY'));
    END IF;

    -- Die Bedingung steht in der QUELLABFRAGE, nicht in den Abfragen
    -- darum herum: So sehen Zaehlen, Loeschen und Schreiben denselben
    -- Ausschnitt.
    v_quelle := 'SELECT ' || v_code || ' AS code, ' || v_time || ' AS ts, '
             || v_level || ' AS lvl FROM ' || v_tab
             || ' WHERE ' || v_time || ' IS NOT NULL'
             || ' AND TRUNC(' || v_time || ') BETWEEN :vom AND :bis';

    EXECUTE IMMEDIATE
      'SELECT COUNT(*) FROM (' || v_quelle || ') q '
      || 'WHERE NOT EXISTS (SELECT 1 FROM fmn_station s WHERE s.code = q.code)'
      INTO v_unbek USING v_von, v_bis;

    IF p_replace = 'J' THEN
      -- Nur das Fenster abraeumen: Ein Nachtrag fuer einen Monat darf
      -- nicht die elf anderen loeschen.
      DELETE FROM fmn_gauge
       WHERE TRUNC(measured_at) BETWEEN v_von AND v_bis;
      v_weg := SQL%ROWCOUNT;
    END IF;

    EXECUTE IMMEDIATE
      'MERGE INTO fmn_gauge g USING ('
      || 'SELECT s.station_id AS sid, q.ts AS ts, q.lvl AS lvl FROM ('
      || v_quelle || ') q JOIN fmn_station s ON s.code = q.code'
      || ') src ON (g.station_id = src.sid AND g.measured_at = src.ts) '
      || 'WHEN MATCHED THEN UPDATE SET g.level_m = src.lvl '
      || 'WHEN NOT MATCHED THEN INSERT (station_id, measured_at, level_m, source) '
      || 'VALUES (src.sid, src.ts, src.lvl, ''IMPORT'')'
      USING v_von, v_bis;
    v_zahl := SQL%ROWCOUNT;

    p_report := 'Zeitraum '
      || CASE WHEN p_from IS NULL THEN 'Anfang' ELSE TO_CHAR(v_von, 'DD.MM.YYYY') END
      || ' bis '
      || CASE WHEN p_to IS NULL THEN 'Ende' ELSE TO_CHAR(v_bis, 'DD.MM.YYYY') END
      || ', uebernommen ' || v_zahl
      || ', unbekannte Stationen ' || v_unbek
      || CASE WHEN p_replace = 'J' THEN ', vorher geloescht ' || v_weg ELSE '' END;

    log_run('IMPORT_GAUGE', p_report, v_zahl);
  END import_gauge;

  ---------------------------------------------------------------------
  -- Pruefen
  ---------------------------------------------------------------------

  FUNCTION verify_events(p_tol_h IN NUMBER DEFAULT c_phase_tol_h) RETURN NUMBER IS
    v_zahl NUMBER := 0;
    v_dev  NUMBER;
  BEGIN
    -- Bewusst als Schleife in PL/SQL und nicht als Aggregation mit dem
    -- Funktionsaufruf im WHERE: Eine Package-Funktion aus SQL heraus zu
    -- rufen, waehrend dasselbe Package laeuft, ist zwar erlaubt, aber
    -- eine unnoetige Abhaengigkeit. Die Zahl der Ereignisse ist klein.
    FOR r IN (SELECT event_type, event_at
                FROM fmn_event
               WHERE event_type IN ('NEW_MOON', 'FIRST_QUARTER',
                                    'FULL_MOON', 'LAST_QUARTER')) LOOP
      v_dev := mean_deviation_h(r.event_at, r.event_type);
      IF v_dev IS NOT NULL AND ABS(v_dev) > p_tol_h THEN
        v_zahl := v_zahl + 1;
      END IF;
    END LOOP;
    RETURN v_zahl;
  END verify_events;

  FUNCTION verify_tides(p_tol_min IN NUMBER DEFAULT 1) RETURN NUMBER IS
    v_zahl NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_zahl
      FROM fmn_tide t
      JOIN fmn_station s ON s.station_id = t.station_id
     WHERE t.is_high_water = 'J'
       AND t.moon_transit_at IS NOT NULL
       AND s.hw_interval_h IS NOT NULL
       AND ABS((CAST(t.event_at AS DATE) - CAST(t.moon_transit_at AS DATE)) * 24
               - s.hw_interval_h) * 60 > p_tol_min;
    RETURN v_zahl;
  END verify_tides;

END fmn_api;
/
