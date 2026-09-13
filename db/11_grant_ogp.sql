-- =====================================================================
-- FMoon  11_grant_ogp.sql
--
-- ACHTUNG: Dieses Skript laeuft NICHT als DEMO, sondern als OGP.
--
-- Es ist deshalb auch NICHT in der Liste, die db_setup.bat einspielt -
-- ein Skript, das sich still an einem fremden Schema zu schaffen macht,
-- gehoert nicht in einen Stapellauf. Wer die Kuestenlinie auf der Karte
-- haben will, spielt es von Hand ein:
--
--   sqlplus ogp/ogp@//localhost:1521/PDBORCL @db/11_grant_ogp.sql
--
-- Was es tut: ein Leserecht, sonst nichts. Es legt nichts an, aendert
-- nichts und loescht nichts in OGP. Die OSM-Daten bleiben, wo sie sind;
-- FMoon kopiert sie ausdruecklich NICHT in das eigene Schema, sondern
-- liest sie ueber eine Sicht. Zwei Kopien derselben Kuestenlinie waeren
-- zwei Wahrheiten, und die groessere davon waere auch noch veraltet,
-- sobald jemand die OSM-Daten neu einspielt.
--
-- Zurueckgenommen wird es mit:
--   REVOKE SELECT ON gemeinden_sh FROM demo;
-- Danach zeigt die Karte Stationen und Gradnetz und sagt, warum die
-- Kueste fehlt - sie bricht nicht.
-- =====================================================================

GRANT SELECT ON gemeinden_sh TO demo
/

-- Die Seen sind kein Muss, machen die Landflaeche aber lesbarer -
-- besonders am Ostseerand, wo sonst nur eine Kante steht.
GRANT SELECT ON seen TO demo
/

-- Nachsehen, ob es gegriffen hat. Ohne diese Ausgabe weiss der
-- Aufrufer nicht, ob er gerade etwas erreicht hat.
SET SERVEROUTPUT ON
/

DECLARE
  v_n NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_n
    FROM user_tab_privs_made
   WHERE grantee = 'DEMO'
     AND privilege = 'SELECT'
     AND table_name IN ('GEMEINDEN_SH', 'SEEN');
  DBMS_OUTPUT.PUT_LINE('Leserechte fuer DEMO: ' || v_n || ' von 2');
  IF v_n < 2 THEN
    DBMS_OUTPUT.PUT_LINE('Mindestens eines fehlt - laeuft dieses Skript '
      || 'wirklich als OGP?');
  END IF;
END;
/
