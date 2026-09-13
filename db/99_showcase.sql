-- =====================================================================
-- FMoon  99_showcase.sql
-- Der Schaukasten: Abfragen zum Ansehen, nichts wird veraendert.
--
-- Gedacht zum Durchklicken im SQL Developer. Jeder Abschnitt steht fuer
-- sich; die Nummern entsprechen der Reihenfolge, in der die Sachen
-- aufeinander aufbauen.
-- =====================================================================

-- --- 1. Die Stationen mit ihren Kennzahlen ---------------------------
SELECT code,
       name,
       gewaesser,
       TO_CHAR(TRUNC(hw_interval_h)) || ' h '
       || LPAD(TO_CHAR(ROUND((hw_interval_h - TRUNC(hw_interval_h)) * 60)),
               2, '0') || ' min'          AS hafenzeit,
       ROUND(mittlerer_hub_m, 2)          AS mittlerer_hub_m,
       tide_sinnvoll,
       is_approx
  FROM fmn_station_v
 ORDER BY water_body, name
/

-- --- 2. Laufzeit der Tidewelle ---------------------------------------
-- Abstand geteilt durch die Differenz der Hafenzeiten. Eine
-- Flachwasserwelle laeuft mit Wurzel(g mal Wassertiefe); in der
-- Deutschen Bucht mit ihren 10 bis 30 Metern sind das 10 bis 17 m/s.
-- Kommt hier etwas voellig anderes heraus, passen die Hafenzeiten
-- nicht zueinander.
SELECT von_name, nach_name, abstand_km, zeitdiff_min, tempo_ms,
       ROUND(tempo_ms * tempo_ms / 9.81, 1) AS entsprechende_tiefe_m
  FROM fmn_wave_v
 ORDER BY abstand_km
/

-- --- 3. Die naechste Station zu einem Punkt --------------------------
-- Wo man am Timmendorfer Strand steht, ist die naechste Station klar.
-- Interessanter: die naechste NORDSEE-Station von dort aus.
SELECT fmn_api.nearest_station(53.995, 10.7783) AS naechste_station,
       ROUND(fmn_api.distance_km('TIMMENDORF', 'BUESUM'), 1) AS ostsee_nach_buesum_km,
       ROUND(fmn_api.distance_km('HELGOLAND', 'CUXHAVEN'), 1) AS helgoland_cuxhaven_km
  FROM dual
/

-- --- 4. Die Mondereignisse eines Jahres ------------------------------
SELECT bezeichnung, TO_CHAR(event_at, 'DD.MM.YYYY HH24:MI') AS zeitpunkt_utc,
       kalenderwoche, ROUND(distance_km) AS entfernung_km,
       supermond, vollmondname,
       ROUND(mean_dev_h, 2) AS abw_mittlere_phase_h
  FROM fmn_event_v
 WHERE event_type = 'FULL_MOON'
   AND event_at >= TIMESTAMP '2026-01-01 00:00:00'
   AND event_at <  TIMESTAMP '2027-01-01 00:00:00'
 ORDER BY event_at
/

-- --- 5. Der synodische Monat aus den eigenen Daten -------------------
-- Der Abstand zweier Neumonde muss 29,53 Tage ergeben. Das prueft die
-- Datenbank an dem, was Java geschrieben hat - ohne selbst zu rechnen.
SELECT event_type,
       COUNT(*) AS anzahl,
       ROUND(AVG(abstand_tage), 4) AS mittlerer_abstand_tage,
       ROUND(MIN(abstand_tage), 3) AS kuerzester,
       ROUND(MAX(abstand_tage), 3) AS laengster
  FROM fmn_event_v
 WHERE abstand_tage IS NOT NULL
 GROUP BY event_type
 ORDER BY event_type
/

-- --- 6. Vollmonde, die auf ein Wochenende fallen ---------------------
-- Eine Frage, die in SQL eine Zeile ist und in Java eine Schleife.
SELECT TO_CHAR(event_at, 'DD.MM.YYYY HH24:MI') AS zeitpunkt_utc,
       TO_CHAR(event_at, 'Day', 'NLS_DATE_LANGUAGE=GERMAN') AS wochentag,
       vollmondname,
       ROUND(distance_km) AS entfernung_km
  FROM fmn_event_v
 WHERE event_type = 'FULL_MOON'
   AND TO_CHAR(event_at, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH') IN ('SAT', 'SUN')
 ORDER BY event_at
/

-- --- 7. Die Gezeitentafel eines Tages --------------------------------
SELECT station_code, TO_CHAR(tag, 'DD.MM.YYYY') AS tag,
       hochwasser, ROUND(hoechster_m, 2) AS hoechster_m,
       ROUND(tiefster_m, 2) AS tiefster_m,
       ROUND(mittlerer_hub_m, 2) AS hub_m, tafel
  FROM fmn_tide_day_v
 WHERE tag BETWEEN TRUNC(SYSDATE) AND TRUNC(SYSDATE) + 2
 ORDER BY station_code, tag
/

-- --- 8. Haelt die Kette? ---------------------------------------------
-- Zu jedem Hochwasser die rueckwaerts gerechnete Hafenzeit gegen den
-- Stammdatenwert. Die Differenz muss null sein - sonst ist unterwegs
-- etwas verrutscht.
SELECT station_code,
       COUNT(*) AS hochwasser,
       ROUND(MAX(ABS(hafenzeit_ist_h - hafenzeit_soll_h)) * 60, 3)
         AS groesste_abweichung_min,
       ROUND(AVG(abstand_h), 4) AS mittlerer_abstand_h
  FROM fmn_tide_v
 WHERE is_high_water = 'J'
   AND hafenzeit_ist_h IS NOT NULL
 GROUP BY station_code
 ORDER BY station_code
/

-- --- 9. Spring- und Nippzeit in den eigenen Daten --------------------
-- Der Tidenhub jedes Hochwassers, daneben der Mondphasenwinkel zum
-- selben Zeitpunkt. Die hohen Huebe muessen bei Voll- und Neumond
-- liegen, mit ein bis zwei Tagen Verspaetung.
SELECT TO_CHAR(t.event_at, 'DD.MM. HH24:MI') AS zeitpunkt,
       ROUND(t.range_m, 2)                  AS hub_m,
       e.bezeichnung                        AS naechste_phase,
       ROUND(CAST(t.event_at AS DATE) - CAST(e.event_at AS DATE), 2)
                                            AS tage_danach
  FROM fmn_tide_v t
  CROSS JOIN LATERAL (
    SELECT v.bezeichnung, v.event_at
      FROM fmn_event_v v
     WHERE v.event_type IN ('NEW_MOON', 'FULL_MOON',
                            'FIRST_QUARTER', 'LAST_QUARTER')
     ORDER BY ABS(CAST(v.event_at AS DATE) - CAST(t.event_at AS DATE))
     FETCH FIRST 1 ROW ONLY
  ) e
 WHERE t.station_code = 'BUESUM'
   AND t.is_high_water = 'J'
   AND t.event_at >= CAST(TRUNC(SYSDATE) AS TIMESTAMP)
   AND t.event_at <  CAST(TRUNC(SYSDATE) + 30 AS TIMESTAMP)
 ORDER BY t.event_at
/

-- --- 10. Gemessen gegen gerechnet ------------------------------------
-- ACHTUNG: Die "Messwerte" in FMN_GAUGE stammen aus 10_import.sql und
-- sind ERFUNDEN (Quelle UEBUNG-ERFUNDEN). Der Vergleich zeigt also
-- nicht, wie gut die Vorhersage in der Wirklichkeit trifft, sondern
-- nur, dass der Weg funktioniert.
SELECT station_code,
       COUNT(*) AS scheitel,
       ROUND(AVG(ABS(zeitdiff_min)), 1) AS mittlere_zeitdiff_min,
       ROUND(AVG(ABS(hoehendiff_m)), 3) AS mittlere_hoehendiff_m,
       ROUND(MAX(ABS(hoehendiff_m)), 3) AS groesste_hoehendiff_m
  FROM fmn_gauge_vs_tide_v
 GROUP BY station_code
 ORDER BY station_code
/

-- --- 11. Nordsee gegen Ostsee in den Messwerten ----------------------
-- Die Standardabweichung des Wasserstands, und wieviel davon die
-- Gezeit erklaert. An der Ostsee bleibt fast alles unerklaert - das
-- ist der Windstau.
SELECT s.code,
       s.water_body,
       COUNT(*) AS messwerte,
       ROUND(STDDEV(g.level_m), 3) AS streuung_gemessen_m,
       ROUND((s.spring_range_m + s.neap_range_m) / 4, 3) AS erwartete_tide_m
  FROM fmn_gauge g
  JOIN fmn_station s ON s.station_id = g.station_id
 GROUP BY s.code, s.water_body, s.spring_range_m, s.neap_range_m
 ORDER BY s.water_body, s.code
/

-- --- 12. Was die Partitionierung gebracht hat ------------------------
SELECT partition_name, high_value, num_rows
  FROM user_tab_partitions
 WHERE table_name = 'FMN_GAUGE'
 ORDER BY partition_position
/

-- --- 13. Die Jahresuebersicht (Materialized View) --------------------
SELECT jahr, monat, event_type, anzahl,
       TO_CHAR(erstes, 'DD.MM. HH24:MI') AS erstes,
       naechster_km, fernster_km
  FROM fmn_year_mv
 WHERE jahr = EXTRACT(YEAR FROM SYSDATE)
 ORDER BY monat, event_type
/

-- --- 14. Das Protokoll der Laeufe ------------------------------------
SELECT TO_CHAR(run_at, 'DD.MM.YYYY HH24:MI:SS') AS wann,
       action, rows_done, detail
  FROM fmn_run_log
 ORDER BY run_id DESC
 FETCH FIRST 25 ROWS ONLY
/

-- --- 15. Objektmethoden in Betrieb -----------------------------------
-- Der Objekttyp rechnet die Hafenzeit aus sich selbst zurueck.
-- ACHTUNG: Objektmethoden in SQL brauchen VALUE(alias), nicht den
-- Alias allein - sonst ORA-00904.
SELECT VALUE(t).to_text() AS ereignis,
       ROUND(VALUE(t).harbour_interval_h(), 4) AS hafenzeit_h
  FROM TABLE(fmn_api.tides('BUESUM',
              CAST(TRUNC(SYSTIMESTAMP) AS TIMESTAMP),
              CAST(TRUNC(SYSTIMESTAMP) + 2 AS TIMESTAMP))) t
/

-- --- 16. Der Zwischenstand der Pruefungen ----------------------------
-- Ohne Toleranzangabe: Sie steht als FMN_API.C_PHASE_TOL_H an genau
-- einer Stelle. Hier stand einmal eine 15, und sie blieb stehen, als der
-- Wert anderswo geaendert wurde - genau die Sorte Abweichung, die nur
-- auffaellt, wenn jemand zufaellig beide Zahlen nebeneinander sieht.
SELECT fmn_api.verify_events     AS phasen_auffaellig,
       fmn_api.verify_tides(1)   AS hochwasser_auffaellig,
       (SELECT COUNT(*) FROM fmn_station) AS stationen,
       (SELECT COUNT(*) FROM fmn_event)   AS ereignisse,
       (SELECT COUNT(*) FROM fmn_tide)    AS gezeiten,
       (SELECT COUNT(*) FROM fmn_gauge)   AS messwerte
  FROM dual
/
