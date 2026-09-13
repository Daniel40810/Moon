-- =====================================================================
-- FMoon  04_stations.sql
-- Stammdaten der Stationen.
--
-- ALLE Kennzahlen sind gerundete NAEHERUNGSWERTE (IS_APPROX = 'J'),
-- keine amtlichen Angaben.
--
-- DIE HAFENZEITEN DER NORDSEE WURDEN AM 13.09.2026 NEU AUSGERICHTET.
-- Sie standen rund zehn Stunden zu hoch. Aufgefallen ist das erst, als
-- die harmonischen Konstanten fuer Cuxhaven aus com.dan.ftidalgauge ein
-- Jahr lang Hochwasser erzeugten: gemessen 1,45 h statt angenommener
-- 11,90 h. Alle fuenf sind um denselben Betrag verschoben - die
-- Abstaende entlang der Kueste bleiben, der absolute Bezug ist gemessen.
-- Ob die Abstaende stimmen, ist damit NICHT gesagt; eine kurze Reihe
-- beobachteter Hochwasser fuer Buesum legt einen um gut eine Stunde
-- anderen Abstand nahe. Deshalb bleibt IS_APPROX auf 'J'.
--
-- Die Werte geben die Groessenordnung und die zeitliche Abfolge entlang
-- der Kueste wieder - die Tidewelle laeuft von Nordwesten in die
-- Deutsche Bucht hinein und erreicht Helgoland zuerst, Cuxhaven rund
-- eine Stunde spaeter - aber sie ersetzen keine Gezeitentafel.
--
-- DIE OSTSEE BLEIBT UNANGETASTET. Dort ist keine Vorhersage sinnvoll,
-- die Hafenzeit wird nie benutzt, und sie um einen aus der Nordsee
-- gemessenen Betrag zu verschieben hiesse, einer bedeutungslosen Zahl
-- eine Bedeutung zu geben.
--
-- Wer echte Werte hat, traegt sie ein und setzt IS_APPROX auf 'N'. Der
-- Java-Teil und jede Anzeige richten sich danach; am Code ist nichts
-- zu aendern. Von Hand geht das hier, im Betrieb besser ueber
-- FMN_API.SET_HARBOUR_TIME - die Prozedur prueft die Plausibilitaet,
-- verlangt eine Quelle und schreibt den Wechsel ins Protokoll.
--
-- SOURCE ist nicht schmueckendes Beiwerk: Eine Tabellenspalte mit einer
-- Zahl ohne Herkunft ist in einem Jahr nicht mehr zu beurteilen. Die
-- Datenbank besteht deshalb darauf - wer IS_APPROX auf 'N' setzt, muss
-- SOURCE fuellen (CONSTRAINT FMN_STATION_SOURCE_CK).
--
-- Die Werte stimmen mit den Konstanten in FTideStation.java ueberein.
-- Der Durchstich prueft das Paar fuer Paar - zwei Stellen mit denselben
-- Zahlen sind sonst zwei Wahrheiten, sobald jemand eine davon anfasst.
--
-- UMLAUTE IN DEN NAMEN: Die Datei bleibt reines ASCII, damit eine
-- falsch geratene Zeichensatzeinstellung des Treibers aus einem Umlaut
-- kein Fragezeichen machen kann. Der Name soll trotzdem "Buesum" NICHT
-- heissen, denn er steht so in FTideStation.java und so in der Anzeige.
-- UNISTR loest das: im Skript steht der Codepunkt, in der Spalte steht
-- der Umlaut. Die Bemerkungen bleiben umschrieben - sie werden nirgends
-- verglichen.
-- =====================================================================

DELETE FROM fmn_station
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('HELGOLAND', 'Helgoland', 'NORTH_SEA', 54.1797, 7.8859, 5.0,
        0.402, 6.21, 2.6, 1.9, 'J',
        'Abstaende entlang der Kueste geschaetzt; absoluter Bezug '
        || 'gemessen aus den harmonischen Konstanten fuer Cuxhaven '
        || 'in com.dan.ftidalgauge (ein Jahr, 710 Hochwasser). '
        || 'Keine amtliche Gezeitentafel', DATE '2026-09-13',
        'Weit draussen; die Tidewelle erreicht die Insel zuerst.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('LIST_SYLT', 'List auf Sylt', 'NORTH_SEA', 55.0181, 8.4406, 3.0,
        0.752, 6.21, 2.1, 1.5, 'J',
        'Abstaende entlang der Kueste geschaetzt; absoluter Bezug '
        || 'gemessen aus den harmonischen Konstanten fuer Cuxhaven '
        || 'in com.dan.ftidalgauge (ein Jahr, 710 Hochwasser). '
        || 'Keine amtliche Gezeitentafel', DATE '2026-09-13',
        'Im Norden der Deutschen Bucht.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('BUESUM', UNISTR('B\00FCsum'), 'NORTH_SEA', 54.1333, 8.8578, 2.0,
        1.002, 6.25, 3.6, 2.7, 'J',
        'Abstaende entlang der Kueste geschaetzt; absoluter Bezug '
        || 'gemessen aus den harmonischen Konstanten fuer Cuxhaven '
        || 'in com.dan.ftidalgauge (ein Jahr, 710 Hochwasser). '
        || 'Keine amtliche Gezeitentafel', DATE '2026-09-13',
        'Dithmarscher Kueste.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('HUSUM', 'Husum', 'NORTH_SEA', 54.4739, 9.0517, 4.0,
        1.552, 6.30, 3.8, 2.9, 'J',
        'Abstaende entlang der Kueste geschaetzt; absoluter Bezug '
        || 'gemessen aus den harmonischen Konstanten fuer Cuxhaven '
        || 'in com.dan.ftidalgauge (ein Jahr, 710 Hochwasser). '
        || 'Keine amtliche Gezeitentafel', DATE '2026-09-13',
        'Tief in der Bucht; dort laeuft die Tide am hoechsten auf.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('CUXHAVEN', 'Cuxhaven', 'NORTH_SEA', 53.8700, 8.7050, 3.0,
        1.452, 6.35, 3.3, 2.5, 'J',
        'Abstaende entlang der Kueste geschaetzt; absoluter Bezug '
        || 'gemessen aus den harmonischen Konstanten fuer Cuxhaven '
        || 'in com.dan.ftidalgauge (ein Jahr, 710 Hochwasser). '
        || 'Keine amtliche Gezeitentafel', DATE '2026-09-13',
        'An der Elbmuendung.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('TIMMENDORF', 'Timmendorfer Strand', 'BALTIC_SEA', 53.9950, 10.7783, 3.0,
        6.50, 6.21, 0.15, 0.08, 'J',
        'Naeherung: aus der bekannten Abfolge der Tidewelle entlang '
        || 'der Kueste und gerundeten Literaturwerten; keine '
        || 'Gezeitentafel', DATE '2026-09-12',
        'Gezeitenarm: Der Wasserstand wird hier von Wind und Luftdruck '
        || 'bestimmt, nicht vom Mond.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('NIENDORF', 'Niendorf/Ostsee', 'BALTIC_SEA', 53.9806, 10.8156, 2.0,
        6.50, 6.21, 0.15, 0.08, 'J',
        'Naeherung: aus der bekannten Abfolge der Tidewelle entlang '
        || 'der Kueste und gerundeten Literaturwerten; keine '
        || 'Gezeitentafel', DATE '2026-09-12',
        'Gezeitenarm.')
/

INSERT INTO fmn_station (code, name, water_body, latitude_deg, longitude_deg,
                         elevation_m, hw_interval_h, lw_offset_h,
                         spring_range_m, neap_range_m, is_approx,
                         source, source_date, remark)
VALUES ('LUEBECK', UNISTR('L\00FCbeck'), 'BALTIC_SEA', 53.8654, 10.6866, 13.0,
        6.70, 6.21, 0.16, 0.09, 'J',
        'Naeherung: aus der bekannten Abfolge der Tidewelle entlang '
        || 'der Kueste und gerundeten Literaturwerten; keine '
        || 'Gezeitentafel', DATE '2026-09-12',
        'Gezeitenarm.')
/

COMMIT
/

-- Nur fuer Cuxhaven ist die Streuung wirklich gemessen: 28,6 Minuten
-- ueber ein Jahr aus dem harmonischen Modell. Bei den anderen vier waere
-- jede Zahl hier erfunden, also bleibt die Spalte dort leer. Eine leere
-- Spalte sagt "nicht gemessen"; eine gefuellte saehe aus wie Wissen.
UPDATE fmn_station SET hw_interval_sd_min = 28.6 WHERE code = 'CUXHAVEN'
/

COMMIT
/

-- Der Trigger hat die Geometrie aus Laenge und Breite gesetzt. Hier wird
-- nachgesehen, ob das ueberall geklappt hat - eine Station ohne Punkt
-- faellt aus jeder raeumlichen Abfrage heraus, ohne dass es auffaellt.
DECLARE
  v_ohne NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_ohne FROM fmn_station WHERE geom IS NULL;
  IF v_ohne > 0 THEN
    RAISE_APPLICATION_ERROR(-20010,
      v_ohne || ' Station(en) ohne Geometrie - der Trigger hat nicht gegriffen.');
  END IF;
  INSERT INTO fmn_run_log (action, detail, rows_done)
  SELECT 'STATIONEN', 'Stammdaten eingespielt', COUNT(*) FROM fmn_station;
  COMMIT;
END;
/
