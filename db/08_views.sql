-- =====================================================================
-- FMoon  08_views.sql
-- Sichten und eine Materialized View.
--
-- Hier steckt der Teil, den die Datenbank wirklich besser kann als
-- Java: Fensterfunktionen ueber Messreihen, raeumliche Abstaende und
-- eine vorgehaltene Jahresuebersicht.
--
-- Das Skript raeumt vor sich selbst auf und ist einzeln wiederholbar.
-- =====================================================================

BEGIN
  FOR m IN (SELECT mview_name FROM user_mviews
             WHERE mview_name = 'FMN_YEAR_MV') LOOP
    EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW ' || m.mview_name;
  END LOOP;
  FOR v IN (SELECT view_name FROM user_views
             WHERE view_name IN ('FMN_STATION_V', 'FMN_TIDE_V', 'FMN_EVENT_V',
                                 'FMN_GAUGE_EXTREME_V', 'FMN_WAVE_V',
                                 'FMN_TIDE_DAY_V', 'FMN_GAUGE_VS_TIDE_V')) LOOP
    EXECUTE IMMEDIATE 'DROP VIEW ' || v.view_name;
  END LOOP;
END;
/

-- ---------------------------------------------------------------------
-- Stationen mit abgeleiteten Angaben
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_station_v AS
SELECT s.station_id,
       s.code,
       s.name,
       s.water_body,
       CASE s.water_body WHEN 'NORTH_SEA' THEN 'Nordsee'
                         ELSE 'Ostsee' END           AS gewaesser,
       s.latitude_deg,
       s.longitude_deg,
       s.hw_interval_h,
       s.spring_range_m,
       s.neap_range_m,
       (s.spring_range_m + s.neap_range_m) / 2       AS mittlerer_hub_m,
       s.is_approx,
       s.source,
       s.source_date,
       s.hw_interval_sd_min,
       -- Ein Satz, der die Zahlen dieser Station einordnet - dieselbe
       -- Aussage wie FTideStation.qualifier() in Java, damit ein Blick
       -- in die Datenbank nicht anders klingt als die Anzeige.
       CASE WHEN s.water_body <> 'NORTH_SEA'
            THEN 'Gezeitenarm - Wind und Luftdruck bestimmen den Stand'
            WHEN s.is_approx = 'J'
            THEN 'Gerundete Naeherung, keine Gezeitentafel'
            ELSE 'Nach ' || s.source
                 || NVL2(s.source_date,
                         ' (Stand ' || TO_CHAR(s.source_date, 'DD.MM.YYYY') || ')',
                         '')
       END                                          AS einordnung,
       -- Ist an dieser Station eine Vorhersage ueberhaupt sinnvoll?
       -- Die Grenze bei einem halben Meter ist Vereinbarung und steht
       -- deshalb sichtbar hier, nicht versteckt im Programm.
       CASE WHEN s.water_body = 'NORTH_SEA'
             AND (s.spring_range_m + s.neap_range_m) / 2 >= 0.5
            THEN 'J' ELSE 'N' END                    AS tide_sinnvoll,
       s.remark
  FROM fmn_station s
/

-- ---------------------------------------------------------------------
-- Gezeiten mit der Hafenzeit, die tatsaechlich herauskommt
--
-- Das ist die Gegenprobe zur Kette: Was Java geschrieben hat, wird hier
-- rueckwaerts gerechnet und mit dem Stammdatenwert verglichen.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_tide_v AS
SELECT s.code                                        AS station_code,
       s.name                                        AS station_name,
       t.event_at,
       t.is_high_water,
       CASE t.is_high_water WHEN 'J' THEN 'HW' ELSE 'NW' END AS art,
       t.level_m,
       t.range_m,
       t.moon_transit_at,
       t.upper_transit,
       -- rueckwaerts gerechnete Hafenzeit in Stunden
       CASE WHEN t.is_high_water = 'J' AND t.moon_transit_at IS NOT NULL
            THEN (CAST(t.event_at AS DATE)
                  - CAST(t.moon_transit_at AS DATE)) * 24
       END                                           AS hafenzeit_ist_h,
       s.hw_interval_h                               AS hafenzeit_soll_h,
       -- Abstand zum vorigen Ereignis DERSELBEN ART in Stunden: von
       -- Hochwasser zu Hochwasser, von Niedrigwasser zu Niedrigwasser.
       -- Daraus muessen 12 h 25 min werden.
       --
       -- Das IS_HIGH_WATER gehoert zwingend in die Partitionierung. Ohne
       -- es laeuft das Fenster ueber die gemischte Folge HW, NW, HW, NW
       -- und liefert die halbe Zahl - eine Vorhersage, die sich selbst
       -- bestaetigt haette, weil 6,2 h eben auch plausibel aussieht.
       (CAST(t.event_at AS DATE)
        - CAST(LAG(t.event_at) OVER (PARTITION BY t.station_id,
                                                  t.is_high_water
                                     ORDER BY t.event_at) AS DATE)) * 24
                                                     AS abstand_h,
       -- Und der Abstand zum naechsten Ereignis ueberhaupt: zwischen
       -- Hoch- und Niedrigwasser, also die halbe Tide.
       (CAST(t.event_at AS DATE)
        - CAST(LAG(t.event_at) OVER (PARTITION BY t.station_id
                                     ORDER BY t.event_at) AS DATE)) * 24
                                                     AS abstand_folge_h
  FROM fmn_tide t
  JOIN fmn_station s ON s.station_id = t.station_id
/

-- ---------------------------------------------------------------------
-- Mondereignisse mit Bewertung
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_event_v AS
SELECT e.event_id,
       e.event_type,
       CASE e.event_type
         WHEN 'NEW_MOON'      THEN 'Neumond'
         WHEN 'FIRST_QUARTER' THEN 'erstes Viertel'
         WHEN 'FULL_MOON'     THEN 'Vollmond'
         WHEN 'LAST_QUARTER'  THEN 'letztes Viertel'
         WHEN 'PERIGEE'       THEN 'Erdnaehe'
         WHEN 'APOGEE'        THEN 'Erdferne'
       END                                           AS bezeichnung,
       e.event_at,
       TO_CHAR(e.event_at, 'IW')                     AS kalenderwoche,
       e.distance_km,
       e.illum_frac,
       e.mean_dev_h,
       CASE WHEN e.event_type = 'FULL_MOON' AND e.distance_km < 360000
            THEN 'J' ELSE 'N' END                    AS supermond,
       n.name_de                                     AS vollmondname,
       -- Abstand zum vorigen Ereignis derselben Art in Tagen. Bei Voll-
       -- und Neumond muss daraus der synodische Monat werden.
       CAST(e.event_at AS DATE)
       - CAST(LAG(e.event_at) OVER (PARTITION BY e.event_type
                                    ORDER BY e.event_at) AS DATE)
                                                     AS abstand_tage
  FROM fmn_event e
  LEFT JOIN fmn_names n
         ON n.month_no = EXTRACT(MONTH FROM e.event_at)
        AND n.tradition = 'DE'
        AND e.event_type = 'FULL_MOON'
/

-- ---------------------------------------------------------------------
-- Hoch- und Niedrigwasser IN DEN MESSWERTEN finden
--
-- Hier zeigt sich, wofuer Fensterfunktionen da sind: Ein Messwert ist
-- ein Scheitel, wenn er groesser ist als seine beiden Nachbarn. In Java
-- waere das eine Schleife, in SQL sind es zwei Zeilen.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_gauge_extreme_v AS
SELECT q.station_code,
       q.name,
       q.measured_at,
       q.level_m,
       CASE WHEN q.level_m > q.vorher AND q.level_m >= q.nachher THEN 'HW'
            ELSE 'NW' END                            AS art
  FROM (
    SELECT s.code                                    AS station_code,
           s.name,
           g.measured_at,
           g.level_m,
           LAG(g.level_m)  OVER (PARTITION BY g.station_id
                                 ORDER BY g.measured_at) AS vorher,
           LEAD(g.level_m) OVER (PARTITION BY g.station_id
                                 ORDER BY g.measured_at) AS nachher
      FROM fmn_gauge g
      JOIN fmn_station s ON s.station_id = g.station_id
  ) q
 WHERE q.vorher IS NOT NULL
   AND q.nachher IS NOT NULL
   AND ((q.level_m > q.vorher AND q.level_m >= q.nachher)
     OR (q.level_m < q.vorher AND q.level_m <= q.nachher))
/

-- ---------------------------------------------------------------------
-- Gemessen gegen gerechnet
--
-- Zu jedem gemessenen Scheitel der naechstgelegene gerechnete. Die
-- Differenz ist das, was die Hafenzeit nicht erklaert: an der Nordsee
-- ein Rest, an der Ostsee praktisch alles.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_gauge_vs_tide_v AS
SELECT m.station_code,
       m.measured_at                                 AS gemessen_at,
       m.level_m                                     AS gemessen_m,
       m.art,
       r.event_at                                    AS gerechnet_at,
       r.level_m                                     AS gerechnet_m,
       (m.measured_at - CAST(r.event_at AS DATE)) * 24 * 60
                                                     AS zeitdiff_min,
       m.level_m - r.level_m                         AS hoehendiff_m
  FROM fmn_gauge_extreme_v m
  CROSS JOIN LATERAL (
    SELECT t.event_at, t.level_m
      FROM fmn_tide t
      JOIN fmn_station s ON s.station_id = t.station_id
     WHERE s.code = m.station_code
       AND t.is_high_water = CASE m.art WHEN 'HW' THEN 'J' ELSE 'N' END
     ORDER BY ABS(CAST(t.event_at AS DATE) - m.measured_at)
     FETCH FIRST 1 ROW ONLY
  ) r
/

-- ---------------------------------------------------------------------
-- Laufzeit der Tidewelle zwischen den Nordseestationen
--
-- Die eigentliche Gegenprobe der Naeherungswerte: Abstand geteilt durch
-- die Differenz der Hafenzeiten ergibt eine Geschwindigkeit. Eine
-- Flachwasserwelle laeuft mit Wurzel(g*h); bei 20 m Wassertiefe sind
-- das rund 14 m/s. Kommt etwas voellig anderes heraus, stimmen die
-- Hafenzeiten nicht zueinander.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_wave_v AS
SELECT a.code                                        AS von_code,
       b.code                                        AS nach_code,
       a.name                                        AS von_name,
       b.name                                        AS nach_name,
       ROUND(SDO_GEOM.SDO_DISTANCE(a.geom, b.geom, 0.5, 'unit=KM'), 1)
                                                     AS abstand_km,
       ROUND((b.hw_interval_h - a.hw_interval_h) * 60, 0)
                                                     AS zeitdiff_min,
       ROUND(SDO_GEOM.SDO_DISTANCE(a.geom, b.geom, 0.5, 'unit=M')
             / NULLIF((b.hw_interval_h - a.hw_interval_h) * 3600, 0), 1)
                                                     AS tempo_ms
  FROM fmn_station a
  JOIN fmn_station b
    ON b.water_body = a.water_body
   AND b.station_id <> a.station_id
   AND b.hw_interval_h > a.hw_interval_h
 WHERE a.water_body = 'NORTH_SEA'
/

-- ---------------------------------------------------------------------
-- Die Gezeiten eines Tages je Station, in einer Zeile
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW fmn_tide_day_v AS
SELECT s.code                                        AS station_code,
       TRUNC(CAST(t.event_at AS DATE))               AS tag,
       COUNT(*)                                      AS ereignisse,
       SUM(CASE t.is_high_water WHEN 'J' THEN 1 ELSE 0 END) AS hochwasser,
       MAX(CASE t.is_high_water WHEN 'J' THEN t.level_m END) AS hoechster_m,
       MIN(CASE t.is_high_water WHEN 'N' THEN t.level_m END) AS tiefster_m,
       ROUND(AVG(t.range_m), 3)                      AS mittlerer_hub_m,
       -- LISTAGG: die Zeiten des Tages als Text, wie in einer Tafel
       LISTAGG(CASE t.is_high_water WHEN 'J' THEN 'HW' ELSE 'NW' END
               || ' ' || TO_CHAR(t.event_at, 'HH24:MI'), '  ')
         WITHIN GROUP (ORDER BY t.event_at)          AS tafel
  FROM fmn_tide t
  JOIN fmn_station s ON s.station_id = t.station_id
 GROUP BY s.code, TRUNC(CAST(t.event_at AS DATE))
/

-- ---------------------------------------------------------------------
-- Jahresuebersicht als Materialized View
--
-- Vorgehalten, weil sie ueber alle Ereignisse eines Jahres laeuft und
-- in jedem Kalenderaufruf gebraucht wird. REFRESH ON DEMAND: Die Daten
-- aendern sich nur, wenn jemand neu vorrechnet - dann stoesst der
-- Scheduler-Job (09) die Auffrischung an.
-- ---------------------------------------------------------------------
CREATE MATERIALIZED VIEW fmn_year_mv
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT EXTRACT(YEAR FROM e.event_at)                 AS jahr,
       EXTRACT(MONTH FROM e.event_at)                AS monat,
       e.event_type,
       COUNT(*)                                      AS anzahl,
       MIN(e.event_at)                               AS erstes,
       MAX(e.event_at)                               AS letztes,
       ROUND(MIN(e.distance_km), 0)                  AS naechster_km,
       ROUND(MAX(e.distance_km), 0)                  AS fernster_km
  FROM fmn_event e
 GROUP BY EXTRACT(YEAR FROM e.event_at),
          EXTRACT(MONTH FROM e.event_at),
          e.event_type
/
