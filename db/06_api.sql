-- =====================================================================
-- FMoon  06_api.sql
-- Die Schnittstelle: Package-SPEZIFIKATION.
--
-- WICHTIG (teuer gelernt): CREATE OR REPLACE PACKAGE verwirft den
-- zugehoerigen Package Body. Wer nur diese Datei neu einspielt, hat die
-- Implementierung verloren, USER_SOURCE zeigt dann nur noch die Spec und
-- jeder Aufruf endet in ORA-04063. 06 und 07 gehoeren IMMER zusammen.
--
-- ZEITEN: Alle Zeitpunkte in FMN_* stehen in UTC. Das ist Absicht - so
-- gibt es keine Sommerzeitluecke und keine doppelte Stunde im Herbst,
-- und die Umrechnung in Ortszeit geschieht an genau einer Stelle, in der
-- Anzeige. Bei FCurvedField hat das Drehen an Zeitzonen einen ganzen
-- Abend gekostet.
-- =====================================================================

CREATE OR REPLACE PACKAGE fmn_api AS

  -- Laenge des mittleren synodischen Monats in Tagen.
  c_synodic_month CONSTANT NUMBER := 29.530588861;

  -- Julianisches Datum der Unix-Epoche.
  c_jd_unix       CONSTANT NUMBER := 2440587.5;

  -- Toleranz der Phasenpruefung in Stunden.
  --
  -- Die mittlere Phase ist eine Gerade, die wirkliche Phase schwingt um
  -- sie herum. Ueber zehn Jahre gemessen bleiben Voll- und Neumond
  -- innerhalb von 14,2 h; die VIERTEL gehen bis 19,2 h, weil sie bei
  -- Meeus einen zusaetzlichen Term tragen, den Voll- und Neumond nicht
  -- haben. Eine engere Toleranz pruefte gegen die Astronomie statt gegen
  -- einen Fehler; ein wirklich falscher Eintrag liegt Tage daneben.
  --
  -- Der Wert steht NUR hier. Der naechtliche Job und der Durchstich
  -- rufen ohne Argument auf, damit es keine zweite Stelle gibt, an der
  -- er stehen koennte.
  c_phase_tol_h   CONSTANT NUMBER := 24;

  ---------------------------------------------------------------------
  -- Zeitrechnung
  ---------------------------------------------------------------------

  -- Julianisches Datum aus einem UTC-Zeitstempel.
  FUNCTION to_jd(p_ts IN TIMESTAMP) RETURN NUMBER DETERMINISTIC;

  -- UTC-Zeitstempel aus einem julianischen Datum.
  FUNCTION to_ts(p_jd IN NUMBER) RETURN TIMESTAMP DETERMINISTIC;

  ---------------------------------------------------------------------
  -- Die unabhaengige Plausibilitaetspruefung
  --
  -- Die vollen Reihen von Meeus stehen in Java und werden hier NICHT
  -- ein zweites Mal gepflegt - das waeren 300 Glieder an zwei Stellen.
  -- Was die Datenbank aber billig kann, ist die MITTLERE Phase: eine
  -- Zeile Formel (Meeus 49.1), ganz ohne die periodischen Korrekturen.
  -- Sie weicht vom wahren Zeitpunkt um bis zu 0,6 Tage ab - das genuegt
  -- vollauf, um einen falsch eingetragenen oder vertauschten Eintrag zu
  -- erkennen, und schafft keine zweite Wahrheit.
  ---------------------------------------------------------------------

  -- Mittlere Phase zur laufenden Nummer k (ganzzahlig = Neumond,
  -- +0,25 erstes Viertel, +0,5 Vollmond, +0,75 letztes Viertel).
  FUNCTION mean_phase_jde(p_k IN NUMBER) RETURN NUMBER DETERMINISTIC;

  -- Die Phasennummer k, die einem Zeitpunkt am naechsten liegt.
  FUNCTION nearest_k(p_ts IN TIMESTAMP, p_type IN VARCHAR2) RETURN NUMBER;

  -- Abweichung eines Zeitpunktes von der mittleren Phase, in Stunden.
  FUNCTION mean_deviation_h(p_ts IN TIMESTAMP, p_type IN VARCHAR2)
    RETURN NUMBER;

  ---------------------------------------------------------------------
  -- Schreiben (von der Java-Seite aufgerufen)
  ---------------------------------------------------------------------

  PROCEDURE add_event(p_type        IN VARCHAR2,
                      p_at          IN TIMESTAMP,
                      p_jde         IN NUMBER   DEFAULT NULL,
                      p_distance_km IN NUMBER   DEFAULT NULL,
                      p_illum       IN NUMBER   DEFAULT NULL);

  PROCEDURE clear_events(p_from IN TIMESTAMP DEFAULT NULL,
                         p_to   IN TIMESTAMP DEFAULT NULL);

  PROCEDURE add_tide(p_station_code  IN VARCHAR2,
                     p_at            IN TIMESTAMP,
                     p_high_water    IN VARCHAR2,
                     p_level_m       IN NUMBER,
                     p_range_m       IN NUMBER,
                     p_transit_at    IN TIMESTAMP DEFAULT NULL,
                     p_upper_transit IN VARCHAR2  DEFAULT NULL);

  PROCEDURE clear_tides(p_station_code IN VARCHAR2 DEFAULT NULL,
                        p_from         IN TIMESTAMP DEFAULT NULL,
                        p_to           IN TIMESTAMP DEFAULT NULL);

  PROCEDURE log_run(p_action IN VARCHAR2,
                    p_detail IN VARCHAR2 DEFAULT NULL,
                    p_rows   IN NUMBER   DEFAULT NULL);

  ---------------------------------------------------------------------
  -- Lesen
  ---------------------------------------------------------------------

  FUNCTION stations RETURN SYS_REFCURSOR;

  ---------------------------------------------------------------------
  -- Die Kuestenlinie
  --
  -- Liefert die Umrisse als reine Zahlenreihe: je Zeile ein Punkt mit
  -- Flaechennummer, Ringnummer und laufender Nummer. Der Java-Teil
  -- bekommt damit Zahlen statt Geometrie und braucht keine
  -- oracle.*-Klassen zum Uebersetzen.
  --
  -- P_TOLERANCE_M vereinfacht die Umrisse (Douglas-Peucker ueber
  -- SDO_UTIL.SIMPLIFY). 1892 Gemeindeflaechen in voller Aufloesung sind
  -- mehr Punkte, als eine Uebersichtskarte zeigen kann - und jeder davon
  -- kostet beim Zeichnen.
  --
  -- DIE EINHEIT IST METER, nicht Grad. Bei geodaetischen Daten (SRID
  -- 8307) rechnet SDO_UTIL.SIMPLIFY in Metern. Der erste Versuch uebergab
  -- 0,002 in der Meinung, das seien Grad - also zwei Millimeter, und
  -- damit wurde gar nichts vereinfacht: 1352 Ringe mit 433 742 Punkten
  -- kamen heraus, wo 200 m Toleranz einen Bruchteil davon liefern.
  --
  -- Gelesen wird ueber DYNAMISCHES SQL. Das ist kein Zierat: Ein
  -- statischer Verweis auf OGP.GEMEINDEN_SH machte den ganzen
  -- Package-Rumpf ungueltig, sobald das Leserecht fehlt - und damit
  -- auch den Mondkalender, der mit der Karte nichts zu tun hat. So
  -- scheitert nur diese eine Funktion, und die Karte weiss es.
  ---------------------------------------------------------------------
  FUNCTION coast(p_min_lon      IN NUMBER,
                 p_min_lat      IN NUMBER,
                 p_max_lon      IN NUMBER,
                 p_max_lat      IN NUMBER,
                 p_tolerance_m  IN NUMBER DEFAULT 200)
    RETURN t_fmn_coast_tab PIPELINED;

  -- Ist die Kuestenlinie erreichbar? Beantwortet die Frage, ohne dass
  -- der Aufrufer einen Fehlertext auswerten muss.
  FUNCTION coast_available RETURN VARCHAR2;

  ---------------------------------------------------------------------
  -- Echte Werte eintragen
  --
  -- Der einzige vorgesehene Weg, eine Hafenzeit zu aendern. Von Hand
  -- ginge es auch mit einem UPDATE - aber dann fehlt die Pruefung, die
  -- Herkunft und der Eintrag ins Protokoll, und in einem Jahr weiss
  -- niemand mehr, woher die Zahl kam.
  --
  -- P_APPROX steuert das Naeherungskennzeichen. 'N' verlangt eine
  -- Quelle; die Datenbank besteht darauf (FMN_STATION_SOURCE_CK), und
  -- diese Prozedur sagt es mit einer verstaendlichen Meldung, bevor die
  -- Bedingung zuschlaegt.
  --
  -- P_SD_MIN ist die gemessene Streuung in Minuten, falls der Wert aus
  -- einer Messung stammt (FMoonHarbourFit liefert sie mit). Sie ist die
  -- ehrlichste Angabe von allen: Sie sagt, wie gut eine einzige Zahl
  -- diesen Ort ueberhaupt beschreiben kann.
  ---------------------------------------------------------------------
  PROCEDURE set_harbour_time(p_code        IN VARCHAR2,
                             p_hw_interval IN NUMBER,
                             p_lw_offset   IN NUMBER DEFAULT NULL,
                             p_source      IN VARCHAR2,
                             p_source_date IN DATE    DEFAULT SYSDATE,
                             p_approx      IN VARCHAR2 DEFAULT 'N',
                             p_sd_min      IN NUMBER  DEFAULT NULL);

  FUNCTION tides(p_station_code IN VARCHAR2,
                 p_from         IN TIMESTAMP,
                 p_to           IN TIMESTAMP) RETURN t_fmn_tide_tab PIPELINED;

  FUNCTION events(p_from IN TIMESTAMP,
                  p_to   IN TIMESTAMP,
                  p_type IN VARCHAR2 DEFAULT NULL)
    RETURN t_fmn_event_tab PIPELINED;

  -- Naechstes Hoch- oder Niedrigwasser nach einem Zeitpunkt.
  FUNCTION next_tide(p_station_code IN VARCHAR2,
                     p_at           IN TIMESTAMP,
                     p_high_water   IN VARCHAR2 DEFAULT 'J')
    RETURN t_fmn_tide_obj;

  ---------------------------------------------------------------------
  -- Raeumliches
  ---------------------------------------------------------------------

  -- Abstand zweier Stationen in Kilometern, geodaetisch gerechnet.
  FUNCTION distance_km(p_code_a IN VARCHAR2, p_code_b IN VARCHAR2)
    RETURN NUMBER;

  -- Die Station, die einem Punkt am naechsten liegt.
  FUNCTION nearest_station(p_lat IN NUMBER, p_lon IN NUMBER) RETURN VARCHAR2;

  -- Ausbreitungsgeschwindigkeit der Tidewelle zwischen zwei Stationen,
  -- in Metern je Sekunde: Abstand geteilt durch die Differenz der
  -- Hafenzeiten. Eine echte Gegenprobe der Naeherungswerte - das
  -- Ergebnis muss in der Groessenordnung der Flachwasserwelle liegen.
  FUNCTION wave_speed_ms(p_code_a IN VARCHAR2, p_code_b IN VARCHAR2)
    RETURN NUMBER;

  ---------------------------------------------------------------------
  -- Messreihen
  ---------------------------------------------------------------------

  PROCEDURE add_gauge(p_station_code IN VARCHAR2,
                      p_at           IN DATE,
                      p_level_m      IN NUMBER,
                      p_source       IN VARCHAR2 DEFAULT NULL);

  -- Uebernimmt Messwerte aus einer fremden Tabelle oder Sicht.
  -- Bezeichner werden mit DBMS_ASSERT geprueft; eine frei formulierte
  -- WHERE-Bedingung gibt es bewusst NICHT - wer filtern will, legt eine
  -- Sicht an.
  PROCEDURE import_gauge(p_table      IN VARCHAR2,
                         p_code_col   IN VARCHAR2,
                         p_time_col   IN VARCHAR2,
                         p_level_col  IN VARCHAR2,
                         p_from       IN DATE DEFAULT NULL,
                         p_to         IN DATE DEFAULT NULL,
                         p_replace    IN VARCHAR2 DEFAULT 'N',
                         p_report     OUT VARCHAR2);

  ---------------------------------------------------------------------
  -- Pruefen
  ---------------------------------------------------------------------

  -- Prueft alle eingetragenen Mondphasen gegen die mittlere Phase.
  -- Liefert die Zahl der Eintraege, die weiter als p_tol_h danebenliegen.
  FUNCTION verify_events(p_tol_h IN NUMBER DEFAULT c_phase_tol_h) RETURN NUMBER;

  -- Prueft, ob jedes Hochwasser die Hafenzeit seiner Station einhaelt.
  FUNCTION verify_tides(p_tol_min IN NUMBER DEFAULT 1) RETURN NUMBER;

END fmn_api;
/
