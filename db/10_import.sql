-- =====================================================================
-- FMoon  10_import.sql
-- Uebungsdaten fuer den Messreihen-Import.
--
-- ACHTUNG, UNMISSVERSTAENDLICH: Die Werte in FMN_GAUGE_RAW sind
-- ERFUNDEN. Es sind keine Messwerte, sie stammen von keiner Behoerde
-- und von keinem Pegel. Sie werden hier gerechnet, damit der Weg
-- "fremde Tabelle -> FMN_GAUGE -> Vergleich mit der Vorhersage"
-- ueberhaupt durchgespielt werden kann.
--
-- Erkennbar ist das an drei Stellen: am Tabellennamen, an der Spalte
-- QUELLE, die bei jeder Zeile 'UEBUNG-ERFUNDEN' traegt, und am Eintrag
-- in FMN_RUN_LOG. Wer echte Pegeldaten hat, laedt sie in dieselbe
-- Tabelle und ruft denselben Import.
--
-- Aufgebaut sind die erfundenen Werte als: halbtaegige Schwingung
-- + Spring-Nipp-Modulation + langsamer "Windstau" + Rauschen. An der
-- Ostseestation faellt der Tidenanteil fast weg und der Windstau
-- bleibt uebrig - genau das, was dort in Wirklichkeit passiert.
-- =====================================================================

BEGIN
  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name = 'FMN_GAUGE_RAW') LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' PURGE';
  END LOOP;
END;
/

CREATE TABLE fmn_gauge_raw (
  station_code  VARCHAR2(30)  NOT NULL,
  gemessen_am   DATE          NOT NULL,
  stand_m       NUMBER(7,3)   NOT NULL,
  quelle        VARCHAR2(40)  DEFAULT 'UEBUNG-ERFUNDEN' NOT NULL
)
/

-- Erfundene Reihe: 20 Tage, 30-Minuten-Takt, drei Stationen.
DECLARE
  v_start      DATE := TRUNC(SYSDATE) - 10;
  v_schritte   PLS_INTEGER := 20 * 48;
  v_t          NUMBER;
  v_tide       NUMBER;
  v_stau       NUMBER;
  v_rauschen   NUMBER;
  v_wert       NUMBER;
  v_zahl       PLS_INTEGER := 0;
  v_pi         CONSTANT NUMBER := 3.141592653589793;
BEGIN
  FOR s IN (SELECT code, water_body,
                   (spring_range_m + neap_range_m) / 2 AS hub,
                   hw_interval_h
              FROM fmn_station
             WHERE code IN ('BUESUM', 'CUXHAVEN', 'TIMMENDORF')) LOOP

    FOR i IN 0 .. v_schritte LOOP
      -- Stunden seit dem Beginn der Reihe
      v_t := i * 0.5;

      -- Halbtaegige Tide mit Spring-Nipp-Schwebung. Die Phase ist so
      -- gelegt, dass sie zur Hafenzeit der Station passt; die
      -- Schwebungsperiode von 354 Stunden sind die 14,77 Tage zwischen
      -- zwei Springzeiten.
      v_tide := (s.hub / 2)
                * (1 + 0.30 * COS(2 * v_pi * v_t / 354.4))
                * COS(2 * v_pi * (v_t - s.hw_interval_h) / 12.4206);

      -- Langsamer Wind- und Luftdruckanteil: zwei ueberlagerte
      -- Schwingungen von etwa zweieinhalb und einem Tag.
      v_stau := 0.22 * SIN(2 * v_pi * v_t / 61.0 + 0.7)
              + 0.13 * SIN(2 * v_pi * v_t / 26.0 + 2.1);

      -- An der Ostsee ist der Stau der bestimmende Anteil.
      IF s.water_body = 'BALTIC_SEA' THEN
        v_stau := v_stau * 2.6;
      END IF;

      -- Rauschen, damit die Reihe nicht wie eine Formel aussieht
      v_rauschen := (DBMS_RANDOM.VALUE(-1, 1)) * 0.035;

      v_wert := ROUND(v_tide + v_stau + v_rauschen, 3);

      INSERT INTO fmn_gauge_raw (station_code, gemessen_am, stand_m)
      VALUES (s.code, v_start + i / 48, v_wert);
      v_zahl := v_zahl + 1;
    END LOOP;
  END LOOP;

  COMMIT;

  INSERT INTO fmn_run_log (action, detail, rows_done)
  VALUES ('UEBUNGSDATEN',
          'FMN_GAUGE_RAW mit ERFUNDENEN Werten gefuellt - keine Messwerte',
          v_zahl);
  COMMIT;
END;
/

-- Der Import selbst. Er laeuft ueber FMN_API und nicht mit einem
-- INSERT ... SELECT von Hand, damit derselbe Weg geprueft wird, den
-- auch die Java-Seite geht.
DECLARE
  v_bericht VARCHAR2(4000);
BEGIN
  fmn_api.import_gauge(
    p_table     => 'FMN_GAUGE_RAW',
    p_code_col  => 'STATION_CODE',
    p_time_col  => 'GEMESSEN_AM',
    p_level_col => 'STAND_M',
    p_replace   => 'J',
    p_report    => v_bericht);
  DBMS_OUTPUT.put_line('Import: ' || v_bericht);
  COMMIT;
END;
/

-- Ein zweiter Lauf mit Zeitfenster: Er darf nur das Fenster anfassen.
-- Das ist die Probe auf die Regel "ein Nachtrag fuer einen Monat
-- loescht nicht die elf anderen".
DECLARE
  v_bericht VARCHAR2(4000);
  v_vorher  NUMBER;
  v_nachher NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_vorher FROM fmn_gauge;

  fmn_api.import_gauge(
    p_table     => 'FMN_GAUGE_RAW',
    p_code_col  => 'STATION_CODE',
    p_time_col  => 'GEMESSEN_AM',
    p_level_col => 'STAND_M',
    p_from      => TRUNC(SYSDATE) - 3,
    p_to        => TRUNC(SYSDATE) - 1,
    p_replace   => 'J',
    p_report    => v_bericht);

  SELECT COUNT(*) INTO v_nachher FROM fmn_gauge;
  DBMS_OUTPUT.put_line('Fensterlauf: ' || v_bericht);
  DBMS_OUTPUT.put_line('Zeilen vorher ' || v_vorher || ', nachher ' || v_nachher);

  IF v_nachher < v_vorher THEN
    RAISE_APPLICATION_ERROR(-20013,
      'Der Fensterlauf hat Zeilen ausserhalb des Fensters entfernt: '
      || v_vorher || ' -> ' || v_nachher);
  END IF;
  COMMIT;
END;
/

-- Und ein verkehrt herum angegebener Zeitraum muss als Fehler kommen,
-- nicht als leeres Ergebnis: Ein Vertipper darf nicht aussehen wie
-- eine leere Quelle.
DECLARE
  v_bericht VARCHAR2(4000);
BEGIN
  fmn_api.import_gauge(
    p_table     => 'FMN_GAUGE_RAW',
    p_code_col  => 'STATION_CODE',
    p_time_col  => 'GEMESSEN_AM',
    p_level_col => 'STAND_M',
    p_from      => TRUNC(SYSDATE),
    p_to        => TRUNC(SYSDATE) - 5,
    p_report    => v_bericht);
  RAISE_APPLICATION_ERROR(-20014,
    'Der verkehrte Zeitraum haette einen Fehler geben muessen.');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -20012 THEN
      DBMS_OUTPUT.put_line('Verkehrter Zeitraum wird erkannt: richtig.');
    ELSE
      RAISE;
    END IF;
END;
/
