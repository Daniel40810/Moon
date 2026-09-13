-- =====================================================================
-- FMoon  05_names.sql
-- Vollmondnamen.
--
-- Zwei Ueberlieferungen nebeneinander, weil sie sich unterscheiden und
-- beide gebraeuchlich sind: die deutschen Monatsnamen des Vollmonds und
-- die im englischen Sprachraum verbreiteten Namen, die auf
-- nordamerikanische Quellen des 18. und 19. Jahrhunderts zurueckgehen.
--
-- Das ist Brauchtum, keine Astronomie. Die Namen sind ueberliefert und
-- je nach Quelle verschieden - deshalb steht die Ueberlieferung als
-- eigene Spalte daneben, statt eine Fassung zur richtigen zu erklaeren.
-- =====================================================================

DELETE FROM fmn_names
/

INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (1, 'Hartung', 'Wolf Moon', 'DE', 'Auch Wolfsmond oder Eismond.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (2, 'Hornung', 'Snow Moon', 'DE', 'Auch Schneemond oder Taumond.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (3, 'Lenzmond', 'Worm Moon', 'DE', 'Auch Fruehlingsmond.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (4, 'Ostermond', 'Pink Moon', 'DE', 'Der erste Vollmond nach dem '
    || 'Fruehlingsanfang bestimmt das Osterdatum.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (5, 'Wonnemond', 'Flower Moon', 'DE', NULL)
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (6, 'Brachmond', 'Strawberry Moon', 'DE', 'Steht am tiefsten am Himmel - '
    || 'zur Sommersonnenwende steht der Vollmond der Sonne gegenueber.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (7, 'Heumond', 'Buck Moon', 'DE', NULL)
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (8, 'Erntemond', 'Sturgeon Moon', 'DE', NULL)
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (9, 'Herbstmond', 'Harvest Moon', 'DE', 'Der Vollmond nahe dem '
    || 'Herbstpunkt geht an mehreren Abenden fast zur gleichen Zeit auf.')
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (10, 'Weinmond', 'Hunter Moon', 'DE', NULL)
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (11, 'Nebelmond', 'Beaver Moon', 'DE', NULL)
/
INSERT INTO fmn_names (month_no, name_de, name_en, tradition, note) VALUES
 (12, 'Julmond', 'Cold Moon', 'DE', 'Steht am hoechsten am Himmel.')
/

COMMIT
/

DECLARE
  v_zahl NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_zahl FROM fmn_names;
  INSERT INTO fmn_run_log (action, detail, rows_done)
  VALUES ('NAMEN', 'Vollmondnamen eingespielt', v_zahl);
  COMMIT;
END;
/
