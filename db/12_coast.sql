-- =====================================================================
-- FMoon  12_coast.sql
-- Die Kuestenlinie - geliehen, nicht kopiert.
--
-- Die Umrisse stammen aus OGP.GEMEINDEN_SH, den OpenStreetMap-Daten
-- fuer Schleswig-Holstein. Der Aussenrand dieser Gemeindeflaechen IST
-- die Kueste; eine eigene Kuestentabelle braucht es deshalb nicht, und
-- sie waere die schlechtere Loesung: Zwei Kopien derselben Linie sind
-- zwei Wahrheiten, und die Kopie ist veraltet, sobald jemand die
-- OSM-Daten neu einspielt.
--
-- ES WIRD NICHTS KOPIERT. Hier steht eine Sicht und eine Funktion, die
-- die Punkte liefert. Wenn das Leserecht fehlt (11_grant_ogp.sql wurde
-- nicht eingespielt) oder das Schema OGP gar nicht da ist, bleibt die
-- Sicht ungueltig - das ist Absicht und kein Fehler. Die Karte faengt
-- das ab und zeigt Stationen und Gradnetz, so wie FStationProvider auf
-- die einprogrammierten Stationen zurueckfaellt.
--
-- WAS NICHT GEZEICHNET WIRD: die Loecher. SDO_UTIL.EXTRACT(g, i, 1)
-- holt den AEUSSEREN Ring jedes Elements; Binnenseen und Enklaven
-- fallen weg. Genau dieselbe Abwaegung wurde in FSpatialMap schon
-- einmal getroffen und dort ausdruecklich hingeschrieben: Darstellung
-- vor Vollstaendigkeit. Fuer eine Kuestenlinie ist der aeussere Ring
-- ohnehin das Gesuchte.
--
-- Das Skript raeumt vor sich selbst auf und ist einzeln wiederholbar.
-- =====================================================================

-- Die Typen T_FMN_COAST_OBJ und T_FMN_COAST_TAB stehen in 02_types.sql.
-- Sie hier anzulegen war der erste Versuch und ein Fehlschlag: FMN_API
-- nennt sie in der Spezifikation, und die wird in 06 eingespielt - sechs
-- Skripte vor diesem hier.
BEGIN
  FOR v IN (SELECT view_name FROM user_views
             WHERE view_name IN ('FMN_COAST_V', 'FMN_COAST_READY_V')) LOOP
    EXECUTE IMMEDIATE 'DROP VIEW ' || v.view_name;
  END LOOP;
END;
/

-- Die Sicht auf die geliehenen Flaechen. Sie ist die einzige Stelle, an
-- der FMoon den Namen des fremden Schemas nennt.
--
-- Angelegt wird sie ueber EXECUTE IMMEDIATE in einem Block, der den
-- Fehler abfaengt. Grund: Ohne das Leserecht endet ein gewoehnliches
-- CREATE VIEW mit ORA-00942, und db_setup.bat meldete einen Fehler,
-- obwohl gar keiner vorliegt - die Kueste ist eben nur ein Zusatz. So
-- steht das Ergebnis im Protokoll, und der Aufbau laeuft weiter.
DECLARE
  v_ok    VARCHAR2(1) := 'N';
  v_grund VARCHAR2(300);
  v_probe NUMBER;
BEGIN
  BEGIN
    EXECUTE IMMEDIATE
      'CREATE OR REPLACE VIEW fmn_coast_v AS '
      || 'SELECT ROWNUM AS feature_id, g.geom FROM ogp.gemeinden_sh g';
    -- Anlegen allein genuegt nicht: Eine Sicht auf eine Tabelle ohne
    -- Leserecht laesst sich unter Umstaenden anlegen und scheitert erst
    -- beim Lesen. Also einmal wirklich hineinsehen.
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT 1 FROM fmn_coast_v '
                      || 'WHERE ROWNUM <= 1)' INTO v_probe;
    v_ok := 'J';
    v_grund := 'Kuestenlinie aus OGP.GEMEINDEN_SH erreichbar';
  EXCEPTION WHEN OTHERS THEN
    v_grund := SUBSTR(SQLERRM, 1, 200);
  END;
  fmn_api.log_run('KUESTE',
    CASE v_ok WHEN 'J' THEN v_grund
              ELSE 'Keine Kuestenlinie: ' || v_grund
                   || ' - 11_grant_ogp.sql als OGP einspielen' END,
    CASE v_ok WHEN 'J' THEN 1 ELSE 0 END);
  COMMIT;
  DBMS_OUTPUT.PUT_LINE(v_grund);
END;
/

-- Eine Sicht, die immer geht: Sie sagt, ob die Kueste erreichbar ist.
-- Ohne sie muesste die Karte den Fehlertext einer fehlgeschlagenen
-- Abfrage auswerten, und das ist ein schlechter Weg, eine Frage zu
-- stellen.
CREATE OR REPLACE VIEW fmn_coast_ready_v AS
SELECT CASE WHEN EXISTS (SELECT 1 FROM user_objects
                          WHERE object_name = 'FMN_COAST_V'
                            AND status = 'VALID')
            THEN 'J' ELSE 'N' END AS erreichbar
  FROM dual
/
