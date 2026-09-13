-- =====================================================================
-- FMoon  01_tables.sql
-- Tabellen, Sequenzen, Trigger.
--
-- Konvention des Hauses: Sequence + Trigger, NIEMALS
-- GENERATED AS IDENTITY.
--
-- Bezugsflaeche aller Wasserstaende ist das MITTELWASSER, nicht
-- Seekartennull: Die Rechnung kennt nur den Tidenhub, und Seekartennull
-- haengt an amtlich festgelegten Bezugswerten je Ort, die hier nicht
-- vorliegen.
-- =====================================================================

CREATE TABLE fmn_station (
  station_id        NUMBER(10)      NOT NULL,
  code              VARCHAR2(30)    NOT NULL,
  name              VARCHAR2(80)    NOT NULL,
  water_body        VARCHAR2(20)    NOT NULL,
  latitude_deg      NUMBER(9,6)     NOT NULL,
  longitude_deg     NUMBER(9,6)     NOT NULL,
  elevation_m       NUMBER(7,2)     DEFAULT 0 NOT NULL,
  time_zone_id      VARCHAR2(60)    DEFAULT 'Europe/Berlin' NOT NULL,
  -- Hafenzeit: Abstand zwischen dem OERTLICHEN Meridiandurchgang des
  -- Mondes und dem darauf folgenden Hochwasser. Ausdruecklich auf die
  -- oertliche Kulmination bezogen, nicht auf die in Greenwich - so ist
  -- sie eine Eigenschaft des Ortes allein.
  hw_interval_h     NUMBER(6,3),
  lw_offset_h       NUMBER(6,3),
  spring_range_m    NUMBER(6,3),
  neap_range_m      NUMBER(6,3),
  -- 'J', solange die Kennzahlen gerundete Naeherungen sind. Wer echte
  -- Werte eintraegt, setzt das Kennzeichen auf 'N' - der Java-Teil und
  -- jede Anzeige richten sich danach.
  is_approx         VARCHAR2(1)     DEFAULT 'J' NOT NULL,
  -- Woher die Kennzahlen stammen, und von wann sie sind.
  --
  -- Eine Zahl ohne Herkunft ist in einem Jahr nicht mehr zu beurteilen:
  -- Niemand weiss dann noch, ob sie aus einer Gezeitentafel kam, aus
  -- einer Messung oder aus einer Schaetzung. Deshalb stehen Quelle und
  -- Stand neben dem Wert und nicht in einem Kommentar.
  --
  -- HW_INTERVAL_SD_MIN ist die Streuung der Hafenzeit in Minuten, wenn
  -- sie gemessen wurde - das Mass dafuer, wie gut die eine Zahl den Ort
  -- ueberhaupt beschreiben kann. Sie ist NULL, solange niemand gemessen
  -- hat.
  source            VARCHAR2(200),
  source_date       DATE,
  hw_interval_sd_min NUMBER(6,2),
  remark            VARCHAR2(400),
  geom              SDO_GEOMETRY,
  created_at        TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT fmn_station_pk PRIMARY KEY (station_id),
  CONSTRAINT fmn_station_uk UNIQUE (code),
  CONSTRAINT fmn_station_water_ck
    CHECK (water_body IN ('NORTH_SEA', 'BALTIC_SEA')),
  CONSTRAINT fmn_station_approx_ck CHECK (is_approx IN ('J', 'N')),
  CONSTRAINT fmn_station_lat_ck CHECK (latitude_deg BETWEEN -90 AND 90),
  CONSTRAINT fmn_station_lon_ck CHECK (longitude_deg BETWEEN -180 AND 180),
  CONSTRAINT fmn_station_hwi_ck
    CHECK (hw_interval_h IS NULL OR hw_interval_h BETWEEN 0 AND 12.5),
  CONSTRAINT fmn_station_lwo_ck
    CHECK (lw_offset_h IS NULL OR lw_offset_h BETWEEN 3 AND 9),
  CONSTRAINT fmn_station_range_ck
    CHECK (spring_range_m IS NULL OR neap_range_m IS NULL
           OR spring_range_m >= neap_range_m),
  -- Wer das Naeherungskennzeichen loescht, muss sagen, woher der Wert
  -- kommt. Sonst waere 'N' eine Behauptung ohne Beleg - und genau die
  -- soll dieses Feld verhindern.
  CONSTRAINT fmn_station_source_ck
    CHECK (is_approx = 'J' OR source IS NOT NULL),
  CONSTRAINT fmn_station_sd_ck
    CHECK (hw_interval_sd_min IS NULL OR hw_interval_sd_min >= 0)
)
/

CREATE SEQUENCE fmn_station_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_station_bi
BEFORE INSERT ON fmn_station
FOR EACH ROW
BEGIN
  IF :new.station_id IS NULL THEN
    :new.station_id := fmn_station_seq.NEXTVAL;
  END IF;
  -- Die Geometrie wird aus Laenge und Breite gefuehrt, damit sie nicht
  -- auseinanderlaufen kann. SRID 8307 ist WGS 84; Oracle rechnet damit
  -- geodaetisch, Entfernungen kommen also in echten Metern heraus.
  IF :new.geom IS NULL THEN
    :new.geom := SDO_GEOMETRY(2001, 8307,
                   SDO_POINT_TYPE(:new.longitude_deg, :new.latitude_deg, NULL),
                   NULL, NULL);
  END IF;
END;
/

CREATE OR REPLACE TRIGGER fmn_station_bu
BEFORE UPDATE OF latitude_deg, longitude_deg ON fmn_station
FOR EACH ROW
BEGIN
  :new.geom := SDO_GEOMETRY(2001, 8307,
                 SDO_POINT_TYPE(:new.longitude_deg, :new.latitude_deg, NULL),
                 NULL, NULL);
END;
/

-- ---------------------------------------------------------------------
-- Mondereignisse
--
-- Gefuellt wird diese Tabelle von der JAVA-Seite: Dort stehen die vollen
-- Meeus-Reihen, und sie sollen nicht ein zweites Mal in PL/SQL gepflegt
-- werden. Die Datenbank prueft die Eintraege gegen die MITTLERE Phase
-- (eine Zeile Formel, siehe FMN_API.mean_phase_jde) - das ist eine
-- unabhaengige Kontrolle, ohne eine zweite Wahrheit zu schaffen.
-- ---------------------------------------------------------------------

CREATE TABLE fmn_event (
  event_id      NUMBER(12)    NOT NULL,
  event_type    VARCHAR2(20)  NOT NULL,
  event_at      TIMESTAMP     NOT NULL,
  jde           NUMBER(16,8),
  distance_km   NUMBER(10,1),
  illum_frac    NUMBER(6,5),
  -- Abweichung von der mittleren Phase in Stunden, von der Datenbank
  -- gerechnet. Eine virtuelle Spalte waere schoener, braucht aber eine
  -- deterministische Funktion; hier setzt der Trigger den Wert.
  mean_dev_h    NUMBER(8,3),
  source        VARCHAR2(30)  DEFAULT 'FMOON-JAVA' NOT NULL,
  created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT fmn_event_pk PRIMARY KEY (event_id),
  CONSTRAINT fmn_event_uk UNIQUE (event_type, event_at),
  CONSTRAINT fmn_event_type_ck CHECK (event_type IN
    ('NEW_MOON', 'FIRST_QUARTER', 'FULL_MOON', 'LAST_QUARTER',
     'PERIGEE', 'APOGEE')),
  CONSTRAINT fmn_event_illum_ck
    CHECK (illum_frac IS NULL OR illum_frac BETWEEN 0 AND 1),
  CONSTRAINT fmn_event_dist_ck
    CHECK (distance_km IS NULL OR distance_km BETWEEN 350000 AND 410000)
)
/

CREATE SEQUENCE fmn_event_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_event_bi
BEFORE INSERT ON fmn_event
FOR EACH ROW
BEGIN
  IF :new.event_id IS NULL THEN
    :new.event_id := fmn_event_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Vorausberechnete Gezeiten
--
-- Abgeleitet aus FMN_STATION und den Monddurchgaengen. Java rechnet,
-- die Datenbank haelt das Ergebnis fuer Abfragen bereit.
-- ---------------------------------------------------------------------

CREATE TABLE fmn_tide (
  tide_id         NUMBER(12)    NOT NULL,
  station_id      NUMBER(10)    NOT NULL,
  event_at        TIMESTAMP     NOT NULL,
  is_high_water   VARCHAR2(1)   NOT NULL,
  level_m         NUMBER(6,3)   NOT NULL,
  range_m         NUMBER(6,3)   NOT NULL,
  moon_transit_at TIMESTAMP,
  upper_transit   VARCHAR2(1),
  created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT fmn_tide_pk PRIMARY KEY (tide_id),
  CONSTRAINT fmn_tide_uk UNIQUE (station_id, event_at),
  CONSTRAINT fmn_tide_fk FOREIGN KEY (station_id)
    REFERENCES fmn_station (station_id) ON DELETE CASCADE,
  CONSTRAINT fmn_tide_hw_ck CHECK (is_high_water IN ('J', 'N')),
  CONSTRAINT fmn_tide_ut_ck CHECK (upper_transit IS NULL
    OR upper_transit IN ('J', 'N')),
  CONSTRAINT fmn_tide_range_ck CHECK (range_m >= 0)
)
/

CREATE SEQUENCE fmn_tide_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_tide_bi
BEFORE INSERT ON fmn_tide
FOR EACH ROW
BEGIN
  IF :new.tide_id IS NULL THEN
    :new.tide_id := fmn_tide_seq.NEXTVAL;
  END IF;
END;
/

CREATE INDEX fmn_tide_ix_zeit ON fmn_tide (event_at)
/

-- ---------------------------------------------------------------------
-- Gemessene Pegelstaende
--
-- Nach Jahr partitioniert (Intervallpartitionierung: Oracle legt jede
-- neue Jahresscheibe selbst an). Die Partitionierung ist hier kein
-- Selbstzweck - eine Pegelreihe waechst mit zehn Minuten Takt um rund
-- 52000 Zeilen je Jahr und Station.
-- ---------------------------------------------------------------------

CREATE TABLE fmn_gauge (
  gauge_id      NUMBER(12)    NOT NULL,
  station_id    NUMBER(10)    NOT NULL,
  measured_at   DATE          NOT NULL,
  level_m       NUMBER(7,3)   NOT NULL,
  source        VARCHAR2(40),
  CONSTRAINT fmn_gauge_hw_ck CHECK (level_m BETWEEN -6 AND 6)
)
PARTITION BY RANGE (measured_at)
INTERVAL (NUMTOYMINTERVAL(1, 'YEAR'))
(
  PARTITION fmn_gauge_p0 VALUES LESS THAN (DATE '2020-01-01')
)
/

CREATE SEQUENCE fmn_gauge_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_gauge_bi
BEFORE INSERT ON fmn_gauge
FOR EACH ROW
BEGIN
  IF :new.gauge_id IS NULL THEN
    :new.gauge_id := fmn_gauge_seq.NEXTVAL;
  END IF;
END;
/

-- Der Primaerschluessel einer partitionierten Tabelle bekommt einen
-- globalen Index; der fachliche Schluessel bleibt (Station, Zeitpunkt)
-- und wird lokal indiziert, damit er mit den Partitionen mitwaechst.
CREATE UNIQUE INDEX fmn_gauge_pk_ix ON fmn_gauge (gauge_id)
/

ALTER TABLE fmn_gauge ADD CONSTRAINT fmn_gauge_pk PRIMARY KEY (gauge_id)
/

CREATE UNIQUE INDEX fmn_gauge_uk_ix ON fmn_gauge (station_id, measured_at) LOCAL
/

ALTER TABLE fmn_gauge ADD CONSTRAINT fmn_gauge_fk
  FOREIGN KEY (station_id) REFERENCES fmn_station (station_id) ON DELETE CASCADE
/

-- ---------------------------------------------------------------------
-- Der kalendarische Teil: Vollmondnamen und Brauchtum
-- ---------------------------------------------------------------------

CREATE TABLE fmn_names (
  name_id     NUMBER(10)    NOT NULL,
  month_no    NUMBER(2)     NOT NULL,
  name_de     VARCHAR2(60)  NOT NULL,
  name_en     VARCHAR2(60),
  tradition   VARCHAR2(40),
  note        VARCHAR2(400),
  CONSTRAINT fmn_names_pk PRIMARY KEY (name_id),
  CONSTRAINT fmn_names_uk UNIQUE (month_no, tradition),
  CONSTRAINT fmn_names_month_ck CHECK (month_no BETWEEN 1 AND 12)
)
/

CREATE SEQUENCE fmn_names_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_names_bi
BEFORE INSERT ON fmn_names
FOR EACH ROW
BEGIN
  IF :new.name_id IS NULL THEN
    :new.name_id := fmn_names_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Protokoll der Laeufe: was hat wann wie viele Zeilen geschrieben
-- ---------------------------------------------------------------------

CREATE TABLE fmn_run_log (
  run_id      NUMBER(12)    NOT NULL,
  run_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  action      VARCHAR2(40)  NOT NULL,
  detail      VARCHAR2(400),
  rows_done   NUMBER(10),
  CONSTRAINT fmn_run_log_pk PRIMARY KEY (run_id)
)
/

CREATE SEQUENCE fmn_run_log_seq START WITH 1 INCREMENT BY 1 NOCACHE
/

CREATE OR REPLACE TRIGGER fmn_run_log_bi
BEFORE INSERT ON fmn_run_log
FOR EACH ROW
BEGIN
  IF :new.run_id IS NULL THEN
    :new.run_id := fmn_run_log_seq.NEXTVAL;
  END IF;
END;
/
