import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.db.FMoonDao;
import com.dan.fmoon.db.FMoonDataSource;
import com.dan.fmoon.db.FStationProvider;
import com.dan.fmoon.tide.FHarbourTide;
import com.dan.fmoon.tide.FTideMoment;
import com.dan.fmoon.tide.FTideStation;
import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

/**
 * Der Durchstich: fuellt die Datenbank aus dem Java-Rechenkern und prueft
 * anschliessend beide Seiten gegeneinander.
 *
 * <p>Weil die Datenbank von aussen nicht erreichbar ist, ist dieses
 * Protokoll das einzige, was ueber den Zustand des Schemas zu erfahren
 * ist. Deshalb: jede Pruefung mit Namen, Ist- und Sollwert.</p>
 *
 * <p>Ein Auffangnetz liegt um den ganzen Lauf. Eine durchschlagende
 * Ausnahme hat bei FCurvedField einmal das Protokoll mitten im Satz
 * abgeschnitten, und der eigentliche Fehler stand nur im Fenster der
 * Stapeldatei.</p>
 */
public class FMoonDbCheck {

    static PrintWriter log;
    static int pass = 0;
    static int fail = 0;

    static void sag(String s) {
        System.out.println(s);
        if (log != null) {
            log.println(s);
            log.flush();
        }
    }

    static void check(String was, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        sag(String.format("%s %-58s %s", ok ? "[ok]  " : "[FEHL]", was, detail));
    }

    static void checkNum(String was, double ist, double soll, double tol,
            String einheit) {
        boolean ok = Math.abs(ist - soll) <= tol;
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        sag(String.format("%s %-58s ist=%-14.4f soll=%-14.4f %s",
                ok ? "[ok]  " : "[FEHL]", was, ist, soll, einheit));
    }

    public static void main(String[] args) {
        File logDatei = new File("build/db_check.log");
        logDatei.getParentFile().mkdirs();
        FMoonDataSource src = null;
        try {
            log = new PrintWriter(logDatei, "UTF-8");
            sag("FMoon Durchstich");
            sag("=====================================");
            sag(new Date().toString());
            sag("");

            src = new FMoonDataSource();
            sag("Verbindung: " + src.getUrl() + " als " + src.getUser());
            Connection c = src.getConnection();
            sag("Verbindung steht.");
            sag("");

            lauf(src, c);

        } catch (Throwable t) {
            // Das Auffangnetz. Ohne es endet das Protokoll mitten im Satz.
            fail++;
            sag("");
            sag("ABBRUCH durch eine Ausnahme:");
            sag("  " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(8, st.length); i++) {
                sag("   bei " + st[i]);
            }
            Throwable u = t.getCause();
            while (u != null) {
                sag("  verursacht durch: " + u);
                u = u.getCause();
            }
        } finally {
            sag("");
            sag("=====================================");
            sag("Ergebnis: " + pass + " von " + (pass + fail) + " Pruefungen gruen");
            if (fail > 0) {
                sag("ES GIBT FEHLER.");
            }
            if (log != null) {
                log.close();
            }
            if (src != null) {
                src.close();
            }
            System.exit(fail == 0 ? 0 : 1);
        }
    }

    static void lauf(FMoonDataSource src, Connection c) throws Exception {
        FMoonDao dao = new FMoonDao(src);

        // ---------------------------------------------------------------
        sag("--- 1. Schema steht ---");
        check("Tabelle FMN_STATION vorhanden", tabelleDa(c, "FMN_STATION"), "");
        check("Tabelle FMN_EVENT vorhanden", tabelleDa(c, "FMN_EVENT"), "");
        check("Tabelle FMN_TIDE vorhanden", tabelleDa(c, "FMN_TIDE"), "");
        check("Tabelle FMN_GAUGE vorhanden", tabelleDa(c, "FMN_GAUGE"), "");
        check("Package FMN_API gueltig",
                objektGueltig(c, "FMN_API", "PACKAGE")
                && objektGueltig(c, "FMN_API", "PACKAGE BODY"), "");
        check("Keine ungueltigen FMN-Objekte", zaehl(c,
                "SELECT COUNT(*) FROM user_objects WHERE object_name LIKE 'FMN%' "
                + "AND status <> 'VALID'") == 0, "");

        // Die Hauskonvention: Sequence + Trigger, nie IDENTITY
        int identity = zaehl(c,
            "SELECT COUNT(*) FROM user_tab_identity_cols WHERE table_name LIKE 'FMN\\_%' ESCAPE '\\'");
        check("Keine IDENTITY-Spalte (Hauskonvention Sequence+Trigger)",
                identity == 0, identity + " gefunden");
        int seq = zaehl(c,
            "SELECT COUNT(*) FROM user_sequences WHERE sequence_name LIKE 'FMN\\_%' ESCAPE '\\'");
        check("Sequenzen angelegt", seq >= 5, seq + " Sequenzen");

        // FCF darf nicht angefasst worden sein
        int fcf = zaehl(c,
            "SELECT COUNT(*) FROM user_tables WHERE table_name LIKE 'FCF\\_%' ESCAPE '\\'");
        check("FCurvedField-Tabellen unberuehrt", fcf > 0,
                fcf + " FCF-Tabellen noch da");

        // ---------------------------------------------------------------
        sag("");
        sag("--- 2. Stationen ---");
        int stationen = dao.count("FMN_STATION");
        check("Acht Stationen eingespielt", stationen == 8, stationen + " Stueck");
        int ohneGeom = zaehl(c, "SELECT COUNT(*) FROM fmn_station WHERE geom IS NULL");
        check("Jede Station hat eine Geometrie", ohneGeom == 0,
                ohneGeom + " ohne");
        int naeherung = zaehl(c,
                "SELECT COUNT(*) FROM fmn_station WHERE is_approx = 'J'");
        check("Alle Stationen als Naeherung gekennzeichnet",
                naeherung == stationen, naeherung + " von " + stationen);

        // Die Zahlen in der Datenbank muessen mit denen im Java-Code
        // uebereinstimmen - sonst gibt es zwei Wahrheiten.
        List<FMoonDao.StationRow> zeilen = dao.loadStations();
        check("Stationen ueber FMN_API lesbar", zeilen.size() == 8,
                zeilen.size() + " gelesen");
        int gleich = 0;
        int verglichen = 0;
        for (int i = 0; i < zeilen.size(); i++) {
            FTideStation ausDb = zeilen.get(i).station;
            FTideStation ausCode = null;
            for (int j = 0; j < FTideStation.DEFAULTS.length; j++) {
                if (FTideStation.DEFAULTS[j].getName().equals(ausDb.getName())) {
                    ausCode = FTideStation.DEFAULTS[j];
                }
            }
            if (ausCode == null) {
                continue;
            }
            verglichen++;
            if (Math.abs(ausCode.getHighWaterIntervalHours()
                         - ausDb.getHighWaterIntervalHours()) < 1e-6
                && Math.abs(ausCode.getSpringRangeMeters()
                         - ausDb.getSpringRangeMeters()) < 1e-6
                && Math.abs(ausCode.getNeapRangeMeters()
                         - ausDb.getNeapRangeMeters()) < 1e-6
                && ausCode.getWaterBody() == ausDb.getWaterBody()) {
                gleich++;
            } else {
                sag("       Abweichung bei " + ausDb.getName()
                    + ": Hafenzeit " + ausCode.getHighWaterIntervalHours()
                    + " gegen " + ausDb.getHighWaterIntervalHours());
            }
        }
        check("Datenbank und Java-Konstanten stimmen ueberein",
                verglichen == 8 && gleich == 8, gleich + " von " + verglichen);

        // ---------------------------------------------------------------
        // Der Weg fuer echte Werte
        //
        // Noch liegen keine amtlichen Hafenzeiten vor. Was hier geprueft
        // wird, ist deshalb nicht ein Wert, sondern der WEG: dass echte
        // Werte eingetragen werden koennen, dass die Datenbank Unsinn
        // abweist, dass die Herkunft mitkommt und dass die Anzeige es
        // merkt. Am Ende steht wieder der Ausgangszustand.
        int ohneQuelle = zaehl(c,
            "SELECT COUNT(*) FROM fmn_station WHERE source IS NULL");
        check("Jede Station sagt, woher ihre Zahlen stammen",
                ohneQuelle == 0, ohneQuelle + " ohne Quelle");

        // Als Zahl lesen, nicht als Text: TO_CHAR haengt an der
        // NLS-Einstellung, und "11,45" mit Komma laesst sich hinterher
        // nicht mehr in ein double zurueckverwandeln.
        double vorher = zahl(c,
            "SELECT hw_interval_h FROM fmn_station WHERE code = 'BUESUM'");
        dao.setHarbourTime("BUESUM", 11.4200, -1.0,
                "Durchstich: erfundener Probewert, gleich wieder zurueckgenommen",
                false, 7.5);
        FTideStation nachher = null;
        for (FMoonDao.StationRow r : dao.loadStations()) {
            if ("BUESUM".equals(r.code)) {
                nachher = r.station;
            }
        }
        check("Eingetragener Wert kommt zurueck",
                nachher != null
                && Math.abs(nachher.getHighWaterIntervalHours() - 11.42) < 1e-6,
                nachher == null ? "nicht gefunden"
                        : String.valueOf(nachher.getHighWaterIntervalHours()));
        check("Das Naeherungskennzeichen faellt",
                nachher != null && !nachher.isApproximate(), "");
        check("Und die Anzeige nennt statt des Vorbehalts die Quelle",
                nachher != null && nachher.qualifier().startsWith("Hafenzeit nach"),
                nachher == null ? "" : nachher.qualifier());

        check("Eine unglaubwuerdige Hafenzeit wird abgewiesen",
                weistAb(dao, "BUESUM", 19.0, "Irgendwas", false),
                "19 h sind mehr als ein halber Mondtag");
        check("Ohne Quelle kein Wert ohne Naeherungskennzeichen",
                weistAb(dao, "BUESUM", 11.4, null, false), "");
        check("Ein unbekannter Stationscode wird abgewiesen",
                weistAb(dao, "GIBTSNICHT", 11.4, "Irgendwas", false), "");

        // Zurueck auf den Ausgangszustand. Ein Durchstich, der die
        // Stammdaten veraendert zuruecklaesst, waere beim naechsten Lauf
        // die Ursache eines Fehlers, den niemand versteht.
        dao.setHarbourTime("BUESUM", vorher, -1.0,
                "Naeherung: aus der bekannten Abfolge der Tidewelle entlang "
                + "der Kueste und gerundeten Literaturwerten; keine "
                + "Gezeitentafel", true, -1.0);
        String kennzeichen = text(c,
            "SELECT is_approx FROM fmn_station WHERE code = 'BUESUM'");
        double zurueck = zahl(c,
            "SELECT hw_interval_h FROM fmn_station WHERE code = 'BUESUM'");
        check("Der Ausgangszustand ist wiederhergestellt",
                "J".equals(kennzeichen) && Math.abs(zurueck - vorher) < 1e-9,
                kennzeichen + " " + zurueck);

        // ---------------------------------------------------------------
        sag("");
        sag("--- 3. Rueckfall auf die Konstanten ---");
        FStationProvider ausDb = FStationProvider.load(src);
        check("Stationsquelle nimmt die Datenbank", ausDb.isFromDatabase(),
                ausDb.getNote());
        FStationProvider ohneDb = FStationProvider.load(null);
        check("Ohne Verbindung faellt sie auf die Konstanten zurueck",
                !ohneDb.isFromDatabase() && ohneDb.getStations().size() == 8,
                ohneDb.getNote());
        FMoonDataSource kaputt = new FMoonDataSource(
                "kein.rechner.example", 1521, "NICHTDA", "NIEMAND", "falsch");
        FStationProvider trotzdem = FStationProvider.load(kaputt);
        check("Auch bei falscher Verbindung kommt eine Liste heraus",
                trotzdem.getStations().size() == 8, trotzdem.getNote());

        // ---------------------------------------------------------------
        sag("");
        sag("--- 4. Mondereignisse schreiben ---");
        // Zehn Jahre, nicht eines. Der synodische Monat weiter unten wird
        // aus den Abstaenden gemittelt, und ein einzelner Neumond kann um
        // einen Viertel Tag von der Mittelbewegung abweichen: ueber zwoelf
        // Abstaende bleibt davon ein halber Tag durch zwoelf stehen, also
        // mehr als die Toleranz. Dieselbe Lehre wie beim anomalistischen
        // Monat in Stufe 1 - ein Fenster in der Groesse der Periode misst
        // das Fenster.
        double von = FAstroTime.julianDay(2026, 1, 1.0);
        double bis = FAstroTime.julianDay(2036, 1, 1.0);
        dao.clearEvents(Long.MIN_VALUE, Long.MAX_VALUE);
        int geschrieben = dao.writeEvents(von, bis);
        sag("       " + geschrieben + " Ereignisse geschrieben");
        int inDb = dao.count("FMN_EVENT");
        check("Ereignisse in der Datenbank", inDb == geschrieben,
                inDb + " Zeilen");

        int vollmonde = zaehl(c,
            "SELECT COUNT(*) FROM fmn_event WHERE event_type = 'FULL_MOON' "
            + "AND EXTRACT(YEAR FROM event_at) = 2026");
        check("Zwoelf oder dreizehn Vollmonde im Jahr 2026",
                vollmonde >= 12 && vollmonde <= 13, vollmonde + " Stueck");

        // Die unabhaengige Kontrolle der Datenbank - ohne Toleranzangabe,
        // damit hier dieselbe gilt wie im naechtlichen Job. Sie steht als
        // FMN_API.C_PHASE_TOL_H an genau einer Stelle, mit ihrer
        // Begruendung daneben (24 h, weil die Viertel bis 19,2 h von der
        // mittleren Phase abweichen). Ein wirklich falscher Eintrag liegt
        // Tage daneben; der Nachweis dafuer steht gleich darunter.
        int auffaellig = dao.verifyEvents();
        check("Keine Phase weicht von der mittleren Phase ab",
                auffaellig == 0, auffaellig + " auffaellig");

        // Die mittlere Phase muss aber ueberhaupt etwas pruefen: Ein
        // absichtlich falscher Eintrag muss auffallen. Sonst waere die
        // Pruefung gruen, ohne etwas zu zeigen.
        Statement st = c.createStatement();
        st.execute("INSERT INTO fmn_event (event_type, event_at, mean_dev_h) "
                 + "VALUES ('FULL_MOON', TIMESTAMP '2026-06-15 12:00:00', NULL)");
        c.commit();
        int mitFalschem = dao.verifyEvents();
        st.execute("DELETE FROM fmn_event WHERE event_at = "
                 + "TIMESTAMP '2026-06-15 12:00:00' AND jde IS NULL");
        c.commit();
        st.close();
        check("Ein falscher Eintrag wird von der Datenbank erkannt",
                mitFalschem > auffaellig,
                mitFalschem + " statt " + auffaellig);

        // Der synodische Monat aus den geschriebenen Daten
        double mittel = zahl(c,
            "SELECT AVG(abstand_tage) FROM fmn_event_v "
            + "WHERE event_type = 'NEW_MOON' AND abstand_tage IS NOT NULL");
        checkNum("Synodischer Monat aus der Datenbank", mittel, 29.5306, 0.05, "Tage");

        // ---------------------------------------------------------------
        sag("");
        sag("--- 5. Gezeiten schreiben ---");
        long tVon = FAstroTime.millisFromJulianDay(FAstroTime.julianDay(2026, 9, 1.0));
        long tBis = FAstroTime.millisFromJulianDay(FAstroTime.julianDay(2026, 10, 1.0));
        dao.clearTides(null, Long.MIN_VALUE, Long.MAX_VALUE);
        int summe = 0;
        for (int i = 0; i < zeilen.size(); i++) {
            FMoonDao.StationRow r = zeilen.get(i);
            summe += dao.writeTides(r.station, r.code, tVon, tBis);
        }
        sag("       " + summe + " Gezeitenereignisse geschrieben");
        check("Gezeiten in der Datenbank", dao.count("FMN_TIDE") == summe,
                dao.count("FMN_TIDE") + " Zeilen");

        int schlechteTide = dao.verifyTides(1);
        check("Jedes Hochwasser haelt die Hafenzeit seiner Station",
                schlechteTide == 0, schlechteTide + " auffaellig");

        double abstand = zahl(c,
            "SELECT AVG(abstand_h) FROM fmn_tide_v WHERE station_code = 'BUESUM' "
            + "AND is_high_water = 'J' AND abstand_h IS NOT NULL");
        checkNum("Mittlerer Abstand zweier Hochwasser", abstand, 12.4206, 0.1, "Stunden");

        // Was Java gerechnet hat und was in der Datenbank steht, muss
        // Zeile fuer Zeile dasselbe sein.
        FTideStation bue = null;
        String bueCode = null;
        for (int i = 0; i < zeilen.size(); i++) {
            if (zeilen.get(i).station.getName().startsWith("B")) {
                bue = zeilen.get(i).station;
                bueCode = zeilen.get(i).code;
            }
        }
        List<FTideMoment> javaSeite = FHarbourTide.events(bue, tVon, tBis);
        int dbSeite = zaehl(c,
            "SELECT COUNT(*) FROM fmn_tide t JOIN fmn_station s "
            + "ON s.station_id = t.station_id WHERE s.code = '" + bueCode + "'");
        check("Java und Datenbank haben gleich viele Ereignisse",
                javaSeite.size() == dbSeite,
                javaSeite.size() + " gegen " + dbSeite);

        // ---------------------------------------------------------------
        sag("");
        sag("--- 6. Raeumliches ---");
        double helCux = dao.distanceKm("HELGOLAND", "CUXHAVEN");
        checkNum("Abstand Helgoland-Cuxhaven", helCux, 63.0, 8.0, "km");
        double tsBue = dao.distanceKm("TIMMENDORF", "BUESUM");
        checkNum("Abstand Timmendorf-Buesum", tsBue, 125.0, 15.0, "km");

        String naechste = null;
        Statement st2 = c.createStatement();
        ResultSet rs2 = st2.executeQuery(
            "SELECT fmn_api.nearest_station(54.18, 7.89) FROM dual");
        if (rs2.next()) {
            naechste = rs2.getString(1);
        }
        rs2.close();
        st2.close();
        check("Naechste Station zum Punkt von Helgoland ist Helgoland",
                "HELGOLAND".equals(naechste), String.valueOf(naechste));

        // Die eigentliche Gegenprobe der Naeherungswerte: Die Tidewelle
        // muss mit einer plausiblen Geschwindigkeit laufen. Eine
        // Flachwasserwelle bei 10 bis 30 m Tiefe schafft 10 bis 17 m/s.
        double tempo = dao.waveSpeedMs("HELGOLAND", "CUXHAVEN");
        check("Tidewelle Helgoland-Cuxhaven laeuft plausibel schnell",
                tempo > 5.0 && tempo < 40.0,
                String.format("%.1f m/s, entspricht %.0f m Wassertiefe",
                        tempo, tempo * tempo / 9.81));

        int wellen = zaehl(c, "SELECT COUNT(*) FROM fmn_wave_v");
        check("Sicht FMN_WAVE_V liefert Strecken", wellen > 0,
                wellen + " Strecken");

        // Der Klick auf die Karte fragt die Datenbank, nicht Java.
        String getroffen = dao.nearestStation(8.8578, 54.1333);
        check("Ein Punkt bei Buesum trifft ueber SDO_NN Buesum",
                "BUESUM".equals(getroffen), String.valueOf(getroffen));

        // Die Kuestenlinie. Sie stammt aus einem fremden Schema, und ob
        // das Leserecht da ist, entscheidet nicht dieser Durchstich -
        // deshalb wird hier NICHT geprueft, ob sie da ist, sondern ob
        // sich die Datenbank in beiden Faellen richtig verhaelt.
        boolean kueste = dao.isCoastAvailable();
        sag("       Kuestenlinie erreichbar: " + (kueste ? "ja" : "nein"));
        if (kueste) {
            FMoonDao.CoastRings erg =
                    dao.loadCoastRings(7.5, 53.5, 11.5, 55.5, 200.0);
            List<double[]> ringe = erg.rings;
            int punkte = 0;
            double minLon = 999;
            double maxLon = -999;
            double minLat = 999;
            double maxLat = -999;
            int verdreht = 0;
            for (int i = 0; i < ringe.size(); i++) {
                double[] r = ringe.get(i);
                punkte += r.length / 2;
                for (int k = 0; k < r.length; k += 2) {
                    minLon = Math.min(minLon, r[k]);
                    maxLon = Math.max(maxLon, r[k]);
                    minLat = Math.min(minLat, r[k + 1]);
                    maxLat = Math.max(maxLat, r[k + 1]);
                    // In Schleswig-Holstein ist die Breite immer groesser
                    // als die Laenge (53 bis 55 gegen 7 bis 12). Ein Paar,
                    // bei dem das nicht gilt, ist vertauscht - und genau
                    // das faellt auf der Karte sonst nur als leerer
                    // Ausschnitt auf.
                    if (r[k + 1] <= r[k]) {
                        verdreht++;
                    }
                }
            }
            check("Die Kuestenlinie liefert Umrisse", ringe.size() > 10,
                    ringe.size() + " Ringe, " + punkte + " Punkte");

            // Die Vereinfachung rechnet in Metern. Ohne sie kamen 433 742
            // Punkte heraus - mehr, als eine Uebersichtskarte zeichnen
            // kann. Diese Pruefung haelt fest, dass die Einheit stimmt.
            check("Die Vereinfachung greift", punkte < 60000,
                    punkte + " Punkte bei 200 m Toleranz");

            sag(String.format("       Ausschnitt der Umrisse: "
                    + "Laenge %.4f bis %.4f, Breite %.4f bis %.4f",
                    minLon, maxLon, minLat, maxLat));

            check("Laenge und Breite sind nicht vertauscht", verdreht == 0,
                    verdreht + " Paare mit Breite <= Laenge");

            // Kaputte Ringe in den OSM-Daten: Sie werden uebersprungen,
            // und das gehoert gesagt. Ein stilles Weglassen waere eine
            // Anzeige, die etwas verschweigt.
            if (erg.skipped > 0 || erg.outside > 0) {
                sag(String.format("       Weggelassen: %d zu gross (groesste "
                        + "%.2f Grad, Flaechen %s), %d ausserhalb des Fensters",
                        erg.skipped, erg.worstSpanDeg, erg.skippedIds,
                        erg.outside));
            }
            check("Hoechstens vereinzelte Ringe sind unbrauchbar",
                    erg.skipped <= 20,
                    erg.skipped + " zu gross von "
                    + (ringe.size() + erg.skipped + erg.outside));

            // Jetzt laesst sich scharf pruefen, und zwar gegen das
            // Fenster statt gegen ein geratenes Rechteck: Jeder Ring
            // beruehrt das Fenster (7,5 bis 11,5 / 53,5 bis 55,5) und ist
            // hoechstens 0,6 Grad gross, also kann er hoechstens um diese
            // 0,6 Grad ueberstehen. Alles andere waere ein Ring, der
            // durch beide Regeln geschluepft ist.
            boolean plausibel = minLon > 7.5 - 0.6 && maxLon < 11.5 + 0.6
                             && minLat > 53.5 - 0.6 && maxLat < 55.5 + 0.6;
            check("Die Umrisse liegen im Fenster plus einer Gemeindebreite",
                    plausibel, plausibel ? "" : "Ausschnitt siehe oben");
        } else {
            // Ohne Leserecht muss die Abfrage leer zurueckkommen und
            // nicht scheitern - die Karte soll einen Zustand sehen,
            // keinen Fehler.
            List<double[]> ringe = dao.loadCoast(7.5, 53.5, 11.5, 55.5, 200.0);
            check("Ohne Leserecht kommt die Kueste leer zurueck, ohne Fehler",
                    ringe.isEmpty(), ringe.size() + " Ringe");
            check("Der Hinweis steht im Protokoll", zaehl(c,
                    "SELECT COUNT(*) FROM fmn_run_log WHERE action = 'KUESTE'") > 0,
                    "11_grant_ogp.sql als OGP einspielen");
        }

        // ---------------------------------------------------------------
        sag("");
        sag("--- 7. Messreihen und Analytics ---");
        int messwerte = dao.count("FMN_GAUGE");
        check("Uebungsmesswerte eingespielt", messwerte > 2000,
                messwerte + " Zeilen");
        int erfunden = zaehl(c,
            "SELECT COUNT(*) FROM fmn_gauge_raw WHERE quelle = 'UEBUNG-ERFUNDEN'");
        check("Die Uebungsdaten sind als erfunden gekennzeichnet",
                erfunden > 0, erfunden + " Zeilen");

        int scheitel = zaehl(c, "SELECT COUNT(*) FROM fmn_gauge_extreme_v");
        check("Fensterfunktion findet Scheitel in den Messwerten",
                scheitel > 50, scheitel + " Scheitel");

        // An der Nordsee muss die Gezeit den groessten Teil der Streuung
        // erklaeren, an der Ostsee fast nichts. Genau das war der fachliche
        // Kernpunkt des ganzen Vorhabens.
        double streuungNord = zahl(c,
            "SELECT STDDEV(g.level_m) FROM fmn_gauge g JOIN fmn_station s "
            + "ON s.station_id = g.station_id WHERE s.code = 'BUESUM'");
        double streuungOst = zahl(c,
            "SELECT STDDEV(g.level_m) FROM fmn_gauge g JOIN fmn_station s "
            + "ON s.station_id = g.station_id WHERE s.code = 'TIMMENDORF'");
        check("Nordsee streut viel staerker als Ostsee",
                streuungNord > streuungOst * 2.0,
                String.format("%.3f m gegen %.3f m", streuungNord, streuungOst));

        int partitionen = zaehl(c,
            "SELECT COUNT(*) FROM user_tab_partitions WHERE table_name = 'FMN_GAUGE'");
        check("Messreihen sind partitioniert", partitionen >= 1,
                partitionen + " Partitionen");

        // ---------------------------------------------------------------
        sag("");
        sag("--- 8. Sichten, Objekttypen, Scheduler ---");
        // Die Jahresuebersicht ist REFRESH COMPLETE ON DEMAND. Was daran
        // zu pruefen ist, ist nicht ihr Inhalt zu einem beliebigen
        // Zeitpunkt - wann sie zuletzt aufgefrischt wurde, haengt vom
        // Ablauf ab und ist kein Merkmal der Datenbank. Zu pruefen ist
        // das VERHALTEN, und zwar mit einer Aenderung, die dieser
        // Durchstich selbst macht: ein zusaetzliches Ereignis einfuegen,
        // nachsehen, ob die Uebersicht es noch nicht kennt, auffrischen,
        // nachsehen, ob sie es jetzt kennt. Danach wird es wieder
        // weggeraeumt - noch vor dem naechtlichen Lauf weiter unten, der
        // sonst darueber stolpern wuerde.
        //
        // Der Ausgangszustand wird dabei HERGESTELLT, nicht angenommen.
        // Beim ersten Versuch stand hier die Erwartung, die Uebersicht
        // sei um genau eine Zeile im Rueckstand - und sie stand in Wahr-
        // heit auf null, weil sie seit dem Einspielen nie aufgefrischt
        // worden war. Das war derselbe Fehler wie zuvor, nur eine Stufe
        // feiner: eine Annahme ueber einen Zustand, den niemand gesetzt
        // hat. Also erst auffrischen, dann messen.
        final String mvProbe = "TIMESTAMP '2031-03-11 04:00:00'";
        Statement st3 = c.createStatement();
        st3.execute("BEGIN DBMS_MVIEW.refresh('FMN_YEAR_MV', 'C'); END;");
        c.commit();
        st3.execute("INSERT INTO fmn_event (event_type, event_at, mean_dev_h) "
                  + "VALUES ('FULL_MOON', " + mvProbe + ", NULL)");
        c.commit();
        int inTabelle = dao.count("FMN_EVENT");
        int summeVeraltet = zaehl(c, "SELECT NVL(SUM(anzahl), 0) FROM fmn_year_mv");
        check("Jahresuebersicht kennt eine neue Zeile noch nicht",
                summeVeraltet == inTabelle - 1,
                summeVeraltet + " gegen " + inTabelle + " in der Tabelle");

        st3.execute("BEGIN DBMS_MVIEW.refresh('FMN_YEAR_MV', 'C'); END;");
        c.commit();
        int summeFrisch = zaehl(c, "SELECT NVL(SUM(anzahl), 0) FROM fmn_year_mv");
        check("Auffrischen bringt sie auf den Stand der Tabelle",
                summeFrisch == inTabelle, summeFrisch + " gegen " + inTabelle);

        st3.execute("DELETE FROM fmn_event WHERE event_at = " + mvProbe
                  + " AND jde IS NULL");
        c.commit();
        st3.execute("BEGIN DBMS_MVIEW.refresh('FMN_YEAR_MV', 'C'); END;");
        c.commit();
        st3.close();

        // Objektmethode: die Hafenzeit aus dem Objekt zurueckgerechnet
        double ausObjekt = zahl(c,
            "SELECT MAX(ABS(VALUE(t).harbour_interval_h() - 1.002)) "
            + "FROM TABLE(fmn_api.tides('BUESUM', "
            + "CAST(TIMESTAMP '2026-09-05 00:00:00' AS TIMESTAMP), "
            + "CAST(TIMESTAMP '2026-09-08 00:00:00' AS TIMESTAMP))) t "
            + "WHERE VALUE(t).harbour_interval_h() IS NOT NULL");
        checkNum("Objektmethode rechnet die Hafenzeit zurueck",
                ausObjekt, 0.0, 1e-6, "Stunden");

        int job = zaehl(c,
            "SELECT COUNT(*) FROM user_scheduler_jobs WHERE job_name = 'FMN_NIGHTLY_JOB'");
        check("Scheduler-Job angelegt", job == 1, job + " gefunden");
        // Ob der Job eingeschaltet ist, entscheidet Daniel mit job.bat -
        // beides ist ein richtiger Zustand, und eine Pruefung, die den
        // einen davon zum Fehler erklaert, prueft eine Vorliebe statt
        // einer Eigenschaft. Was wirklich falsch sein KANN, ist die
        // Verbindung aus Schalter und Termin: Ein eingeschalteter Job
        // ohne naechsten Lauf sieht an jeder Anzeige eingeschaltet aus
        // und laeuft trotzdem nie; ein ausgeschalteter mit Termin waere
        // das Gegenstueck.
        String jobAn = text(c,
            "SELECT enabled FROM user_scheduler_jobs WHERE job_name = 'FMN_NIGHTLY_JOB'");
        String naechst = text(c,
            "SELECT TO_CHAR(next_run_date, 'DD.MM.YYYY HH24:MI') FROM user_scheduler_jobs"
            + " WHERE job_name = 'FMN_NIGHTLY_JOB'");
        boolean an = "TRUE".equalsIgnoreCase(jobAn);
        boolean hatTermin = naechst != null && naechst.trim().length() > 0;
        check("Schalter und Termin passen zusammen", an == hatTermin,
                "eingeschaltet=" + jobAn + ", naechster Lauf="
                + (hatTermin ? naechst : "keiner"));
        sag("       (Der Job ist " + (an ? "EINGESCHALTET" : "ausgeschaltet")
                + " - beides ist erlaubt, job.bat schaltet um.)");

        // Der Job muss laufen, wenn man ihn von Hand anstoesst
        Statement st4 = c.createStatement();
        try {
            st4.execute("BEGIN fmn_nightly; END;");
            check("Naechtliche Pflege laeuft durch", true, "");
        } catch (SQLException e) {
            check("Naechtliche Pflege laeuft durch", false,
                    ersteZeile(e.getMessage()));
        } finally {
            st4.close();
        }

        int protokoll = zaehl(c, "SELECT COUNT(*) FROM fmn_run_log");
        check("Laufprotokoll wird geschrieben", protokoll >= 3,
                protokoll + " Zeilen");

        // ---------------------------------------------------------------
        sag("");
        sag("--- 9. Vollmondnamen ---");
        int namen = dao.count("FMN_NAMES");
        check("Zwoelf Vollmondnamen", namen == 12, namen + " Stueck");
        String september = text(c,
            "SELECT name_de FROM fmn_names WHERE month_no = 9 AND tradition = 'DE'");
        check("September heisst Herbstmond", "Herbstmond".equals(september),
                String.valueOf(september));
        // JEDER Vollmond muss seinen Namen bekommen, nicht mindestens
        // zwoelf: Die Bedingung "mindestens zwoelf" war gruen, als der
        // Durchstich auf zehn Jahre ging und daneben "124 von 13" stand -
        // eine Zahl, die niemand mehr gelesen haette.
        int vollmondeGesamt = zaehl(c,
            "SELECT COUNT(*) FROM fmn_event WHERE event_type = 'FULL_MOON'");
        int mitNamen = zaehl(c,
            "SELECT COUNT(*) FROM fmn_event_v WHERE event_type = 'FULL_MOON' "
            + "AND vollmondname IS NOT NULL");
        check("Jeder Vollmond bekommt seinen Namen",
                mitNamen == vollmondeGesamt && vollmondeGesamt > 100,
                mitNamen + " von " + vollmondeGesamt);

        // ---------------------------------------------------------------
        sag("");
        sag("--- 10. Zeiten stehen in UTC ---");
        // Ein bekannter Zeitpunkt wird geschrieben und zurueckgelesen.
        // Stimmt die Zeitzone nicht, kommt er um Stunden verschoben an.
        FMoonPhaseTimes.Phase probe = FMoonPhaseTimes.next(
                FAstroTime.julianDay(2026, 3, 1.0), FMoonPhaseTimes.FULL_MOON);
        long sollMillis = probe.millis();
        double ausDbJd = zahl(c,
            "SELECT MIN(fmn_api.to_jd(event_at)) FROM fmn_event "
            + "WHERE event_type = 'FULL_MOON' "
            + "AND event_at >= TIMESTAMP '2026-03-01 00:00:00'");
        long ausDbMillis = FAstroTime.millisFromJulianDay(ausDbJd);
        double abweichungMin = Math.abs(ausDbMillis - sollMillis) / 60000.0;
        checkNum("Geschriebener Zeitpunkt kommt unverschoben zurueck",
                abweichungMin, 0.0, 1.0, "Minuten");
    }

    // ------------------------------------------------------------------

    /**
     * Ruft set_harbour_time auf und meldet, ob die Datenbank den Wert
     * zurueckgewiesen hat. Eine Pruefung, die eine Ablehnung erwartet,
     * muss die Ausnahme abfangen - sonst bricht der ganze Durchstich ab,
     * obwohl gerade das richtige passiert ist.
     */
    static boolean weistAb(FMoonDao dao, String code, double h,
            String quelle, boolean approx) {
        try {
            dao.setHarbourTime(code, h, -1.0, quelle, approx, -1.0);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static boolean tabelleDa(Connection c, String name) throws SQLException {
        return zaehl(c, "SELECT COUNT(*) FROM user_tables WHERE table_name = '"
                + name + "'") == 1;
    }

    static boolean objektGueltig(Connection c, String name, String typ)
            throws SQLException {
        return zaehl(c, "SELECT COUNT(*) FROM user_objects WHERE object_name = '"
                + name + "' AND object_type = '" + typ + "' AND status = 'VALID'") == 1;
    }

    static int zaehl(Connection c, String sql) throws SQLException {
        Statement st = c.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql);
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
    }

    static double zahl(Connection c, String sql) throws SQLException {
        Statement st = c.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql);
            try {
                return rs.next() ? rs.getDouble(1) : Double.NaN;
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
    }

    static String text(Connection c, String sql) throws SQLException {
        Statement st = c.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql);
            try {
                return rs.next() ? rs.getString(1) : null;
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
    }

    static String ersteZeile(String s) {
        if (s == null) {
            return "(ohne Meldung)";
        }
        int nl = s.indexOf('\n');
        return (nl > 0) ? s.substring(0, nl) : s;
    }
}
