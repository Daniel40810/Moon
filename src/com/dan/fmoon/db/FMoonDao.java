package com.dan.fmoon.db;

import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.astro.FMoonPhase;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.astro.FMoonPosition;
import com.dan.fmoon.tide.FHarbourTide;
import com.dan.fmoon.tide.FTideMoment;
import com.dan.fmoon.tide.FTideStation;
import com.dan.fmoon.tide.FWaterBody;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Liest und schreibt die FMN-Tabellen. Ausschliesslich ueber das Package
 * {@code FMN_API} und die Sichten - kein Programm greift auf die Tabellen
 * unmittelbar zu.
 *
 * <h3>Zeiten</h3>
 *
 * <p>Alle Zeitpunkte in den FMN-Tabellen stehen in <b>UTC</b>. Weil
 * {@link Timestamp} beim Schreiben und Lesen die Zeitzone der
 * Java-Umgebung heranzieht, wird hier ueberall mit einem Kalender in UTC
 * gearbeitet. Ohne das verschiebt sich jeder Wert um die
 * Zeitzonendifferenz, und im Herbst stehen zwei Ereignisse auf derselben
 * Stunde.</p>
 *
 * @author Claude fuer Daniel
 */
public class FMoonDao {

    private final FMoonDataSource source;

    /**
     * Kalender in UTC. Wird jedem Zeitstempel mitgegeben, damit die
     * Zeitzone der Java-Umgebung keine Rolle spielt.
     */
    private final Calendar utc =
            new GregorianCalendar(TimeZone.getTimeZone("UTC"));

    /**
     * Legt den Zugriff an.
     *
     * @param source Verbindung
     */
    public FMoonDao(FMoonDataSource source) {
        this.source = source;
    }

    /**
     * Die Verbindung, ueber die gearbeitet wird.
     *
     * @return Verbindung
     */
    public FMoonDataSource getSource() {
        return source;
    }

    private Timestamp ts(long millis) {
        return new Timestamp(millis);
    }

    // ------------------------------------------------------------------
    // Stationen
    // ------------------------------------------------------------------

    /**
     * Eine Station, wie sie in der Datenbank steht: der Code, unter dem
     * sie dort gefuehrt wird, und die Station selbst.
     *
     * <p>Die beiden gehoeren zusammen und werden deshalb zusammen
     * gereicht. Sie in zwei Listen gleicher Laenge zu fuehren waere eine
     * Verabredung, die beim ersten Sortieren bricht.</p>
     */
    public static final class StationRow {

        /** Code in der Datenbank, etwa "BUESUM". */
        public final String code;

        /** Die Station mit allen Kennzahlen. */
        public final FTideStation station;

        StationRow(String code, FTideStation station) {
            this.code = code;
            this.station = station;
        }

        @Override
        public String toString() {
            return code + " (" + station.getName() + ")";
        }
    }

    /**
     * Liest alle Stationen aus der Datenbank.
     *
     * @return Stationen mit ihren Codes, moeglicherweise leer
     * @throws SQLException bei Fehlern der Datenbank
     */
    public List<StationRow> loadStations() throws SQLException {
        List<StationRow> out = new ArrayList<StationRow>();
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall("{ ? = call fmn_api.stations() }");
        try {
            cs.registerOutParameter(1, Types.REF_CURSOR);
            cs.execute();
            ResultSet rs = (ResultSet) cs.getObject(1);
            try {
                while (rs.next()) {
                    out.add(readStation(rs));
                }
            } finally {
                rs.close();
            }
        } finally {
            cs.close();
        }
        return out;
    }

    private StationRow readStation(ResultSet rs) throws SQLException {
        String code = rs.getString("code");
        String name = rs.getString("name");
        String water = rs.getString("water_body");
        double lat = rs.getDouble("latitude_deg");
        double lon = rs.getDouble("longitude_deg");
        double ele = rs.getDouble("elevation_m");
        String zone = rs.getString("time_zone_id");

        double hwi = rs.getDouble("hw_interval_h");
        double lwo = rs.getDouble("lw_offset_h");
        double spring = rs.getDouble("spring_range_m");
        double neap = rs.getDouble("neap_range_m");
        boolean approx = "J".equals(rs.getString("is_approx"));
        // Die Herkunft kommt mit. Sie ist der Grund, warum das
        // Naeherungskennzeichen ueberhaupt umgestellt werden darf, und die
        // Anzeige soll sie nennen koennen, statt den Hinweis nur wegfallen
        // zu lassen.
        String quelle = rs.getString("source");

        FGeoLocation loc = new FGeoLocation(name, lat, lon, ele, zone);
        FWaterBody body = "BALTIC_SEA".equals(water)
                ? FWaterBody.BALTIC_SEA : FWaterBody.NORTH_SEA;
        return new StationRow(code, new FTideStation(name, loc, body,
                hwi, lwo, spring, neap, approx, quelle));
    }

    // ------------------------------------------------------------------
    // Mondereignisse schreiben
    // ------------------------------------------------------------------

    /**
     * Rechnet alle Mondphasen und Apsiden eines Zeitraums und schreibt sie
     * in die Datenbank.
     *
     * <p>Gerechnet wird in Java - dort stehen die vollen Reihen. Die
     * Datenbank prueft die Eintraege anschliessend gegen die mittlere
     * Phase; das ist eine unabhaengige Kontrolle, ohne eine zweite
     * Wahrheit zu schaffen.</p>
     *
     * @param fromJdUt Beginn als julianisches Datum in UT
     * @param toJdUt   Ende als julianisches Datum in UT
     * @return Zahl der geschriebenen Ereignisse
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int writeEvents(double fromJdUt, double toJdUt) throws SQLException {
        Connection c = source.getConnection();
        int zahl = 0;

        CallableStatement cs = c.prepareCall(
                "{ call fmn_api.add_event(?, ?, ?, ?, ?) }");
        try {
            List<FMoonPhaseTimes.Phase> phasen =
                    FMoonPhaseTimes.between(fromJdUt, toJdUt);
            for (int i = 0; i < phasen.size(); i++) {
                FMoonPhaseTimes.Phase p = phasen.get(i);
                double jde = p.jde;
                FMoonPosition pos = FMoonPosition.at(jde);
                FMoonPhase ph = FMoonPhase.at(jde);
                cs.setString(1, typeName(p.type));
                cs.setTimestamp(2, ts(p.millis()), utc);
                cs.setDouble(3, jde);
                cs.setDouble(4, pos.distanceKm);
                cs.setDouble(5, ph.illuminatedFraction);
                cs.execute();
                zahl++;
            }

            double[] peri = FMoonEphemeris.findApsides(fromJdUt, toJdUt, true);
            zahl += writeApsides(cs, peri, "PERIGEE");
            double[] apo = FMoonEphemeris.findApsides(fromJdUt, toJdUt, false);
            zahl += writeApsides(cs, apo, "APOGEE");
        } finally {
            cs.close();
        }
        c.commit();
        return zahl;
    }

    private int writeApsides(CallableStatement cs, double[] jds, String type)
            throws SQLException {
        for (int i = 0; i < jds.length; i++) {
            double jdUt = jds[i];
            double jde = jdUt + FAstroTime.deltaTDays(jdUt);
            FMoonPosition pos = FMoonPosition.at(jde);
            FMoonPhase ph = FMoonPhase.at(jde);
            cs.setString(1, type);
            cs.setTimestamp(2, ts(FAstroTime.millisFromJulianDay(jdUt)), utc);
            cs.setDouble(3, jde);
            cs.setDouble(4, pos.distanceKm);
            cs.setDouble(5, ph.illuminatedFraction);
            cs.execute();
        }
        return jds.length;
    }

    private static String typeName(int phaseType) {
        switch (phaseType) {
            case FMoonPhaseTimes.NEW_MOON:
                return "NEW_MOON";
            case FMoonPhaseTimes.FIRST_QUARTER:
                return "FIRST_QUARTER";
            case FMoonPhaseTimes.FULL_MOON:
                return "FULL_MOON";
            default:
                return "LAST_QUARTER";
        }
    }

    /**
     * Loescht Mondereignisse eines Zeitraums.
     *
     * @param fromMillis Beginn, oder Long.MIN_VALUE fuer offen
     * @param toMillis   Ende, oder Long.MAX_VALUE fuer offen
     * @throws SQLException bei Fehlern der Datenbank
     */
    public void clearEvents(long fromMillis, long toMillis) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall("{ call fmn_api.clear_events(?, ?) }");
        try {
            if (fromMillis == Long.MIN_VALUE) {
                cs.setNull(1, Types.TIMESTAMP);
            } else {
                cs.setTimestamp(1, ts(fromMillis), utc);
            }
            if (toMillis == Long.MAX_VALUE) {
                cs.setNull(2, Types.TIMESTAMP);
            } else {
                cs.setTimestamp(2, ts(toMillis), utc);
            }
            cs.execute();
        } finally {
            cs.close();
        }
        c.commit();
    }

    // ------------------------------------------------------------------
    // Gezeiten schreiben
    // ------------------------------------------------------------------

    /**
     * Rechnet die Gezeiten einer Station und schreibt sie in die Datenbank.
     *
     * @param station    Station
     * @param code       Code der Station in der Datenbank
     * @param fromMillis Beginn
     * @param toMillis   Ende
     * @return Zahl der geschriebenen Ereignisse
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int writeTides(FTideStation station, String code,
            long fromMillis, long toMillis) throws SQLException {
        List<FTideMoment> ereignisse =
                FHarbourTide.events(station, fromMillis, toMillis);
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
                "{ call fmn_api.add_tide(?, ?, ?, ?, ?, ?, ?) }");
        try {
            for (int i = 0; i < ereignisse.size(); i++) {
                FTideMoment m = ereignisse.get(i);
                cs.setString(1, code);
                cs.setTimestamp(2, ts(m.getTimeMillis()), utc);
                cs.setString(3, m.isHighWater() ? "J" : "N");
                cs.setDouble(4, m.getLevelMeters());
                cs.setDouble(5, m.getRangeMeters());
                cs.setTimestamp(6, ts(m.getMoonTransitMillis()), utc);
                cs.setString(7, m.isUpperTransit() ? "J" : "N");
                cs.execute();
            }
        } finally {
            cs.close();
        }
        c.commit();
        return ereignisse.size();
    }

    /**
     * Loescht vorausberechnete Gezeiten.
     *
     * @param code       Code der Station, oder null fuer alle
     * @param fromMillis Beginn, oder Long.MIN_VALUE fuer offen
     * @param toMillis   Ende, oder Long.MAX_VALUE fuer offen
     * @throws SQLException bei Fehlern der Datenbank
     */
    public void clearTides(String code, long fromMillis, long toMillis)
            throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
                "{ call fmn_api.clear_tides(?, ?, ?) }");
        try {
            if (code == null) {
                cs.setNull(1, Types.VARCHAR);
            } else {
                cs.setString(1, code);
            }
            if (fromMillis == Long.MIN_VALUE) {
                cs.setNull(2, Types.TIMESTAMP);
            } else {
                cs.setTimestamp(2, ts(fromMillis), utc);
            }
            if (toMillis == Long.MAX_VALUE) {
                cs.setNull(3, Types.TIMESTAMP);
            } else {
                cs.setTimestamp(3, ts(toMillis), utc);
            }
            cs.execute();
        } finally {
            cs.close();
        }
        c.commit();
    }

    // ------------------------------------------------------------------
    // Lesen und Pruefen
    // ------------------------------------------------------------------

    /**
     * Zaehlt die Zeilen einer FMN-Tabelle.
     *
     * @param table Tabellenname, muss mit FMN_ beginnen
     * @return Zeilenzahl
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int count(String table) throws SQLException {
        if (table == null || !table.toUpperCase().startsWith("FMN_")) {
            throw new IllegalArgumentException("Nur FMN-Tabellen: " + table);
        }
        Connection c = source.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM " + table.toUpperCase());
        try {
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    /**
     * Fragt die Datenbank, wie viele eingetragene Mondphasen weiter als
     * die angegebene Toleranz von der mittleren Phase abweichen.
     *
     * @param toleranceHours Toleranz in Stunden
     * @return Zahl der auffaelligen Eintraege
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int verifyEvents(double toleranceHours) throws SQLException {
        return callNumber("{ ? = call fmn_api.verify_events(?) }", toleranceHours);
    }

    // ------------------------------------------------------------------
    // Die Kuestenlinie
    // ------------------------------------------------------------------

    /**
     * Ist eine Kuestenlinie erreichbar?
     *
     * <p>Sie stammt aus einem fremden Schema (OGP, die OSM-Daten), und
     * das Leserecht dafuer wird von Hand gegeben. Fehlt es, ist das kein
     * Fehler, sondern ein Zustand - die Karte zeigt dann Stationen und
     * Gradnetz.</p>
     *
     * @return wahr, wenn Umrisse gelesen werden koennen
     */
    public boolean isCoastAvailable() {
        try {
            Connection c = source.getConnection();
            CallableStatement cs = c.prepareCall(
                "{ ? = call fmn_api.coast_available() }");
            try {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                return "J".equals(cs.getString(1));
            } finally {
                cs.close();
            }
        } catch (Throwable t) {
            // Auch ein fehlendes Package oder ein fehlender Treiber
            // heisst hier nur: keine Kueste.
            return false;
        }
    }

    /**
     * Das Ergebnis einer Kuestenabfrage: die Ringe und was dabei
     * aussortiert wurde.
     *
     * <p>Die zweite Zahl ist wichtiger, als sie aussieht. Wer nur die
     * Ringe zurueckgibt, verschweigt, dass etwas weggelassen wurde - und
     * genau das darf eine Anzeige nicht.</p>
     */
    public static final class CoastRings {

        /** Je Ring ein Feld aus abwechselnd Laenge und Breite. */
        public final List<double[]> rings = new ArrayList<double[]>();

        /** Die Flaechennummer zu jedem Ring, zum Nachschlagen. */
        public final List<Long> featureIds = new ArrayList<Long>();

        /** Ringe, die als unglaubwuerdig uebersprungen wurden. */
        public int skipped;

        /** Ringe, die ganz ausserhalb des Fensters lagen. */
        public int outside;

        /** Die Flaechennummern der uebersprungenen Ringe, hoechstens zehn. */
        public final List<Long> skippedIds = new ArrayList<Long>();

        /** Groesste Ausdehnung eines uebersprungenen Rings in Grad. */
        public double worstSpanDeg;
    }

    /**
     * Liest die Umrisse in einem Ausschnitt.
     *
     * <p>Die Datenbank liefert reine Zahlen - je Zeile ein Punkt mit
     * Flaechen-, Ring- und Punktnummer. Hier werden daraus geschlossene
     * Ringe: je Ring ein Feld aus abwechselnd Laenge und Breite, so wie
     * es {@code FGeoFeature.polygon} erwartet.</p>
     *
     * @param minLon        westlicher Rand
     * @param minLat        suedlicher Rand
     * @param maxLon        oestlicher Rand
     * @param maxLat        noerdlicher Rand
     * @param toleranceMeters Vereinfachung in METERN; 0 fuer volle
     *                      Aufloesung. Bei geodaetischen Daten rechnet
     *                      Oracle in Metern, nicht in Grad - 200 m sind
     *                      fuer eine Uebersichtskarte reichlich fein.
     * @return Liste der Ringe, moeglicherweise leer
     * @throws SQLException bei Fehlern der Datenbank
     */
    public List<double[]> loadCoast(double minLon, double minLat,
            double maxLon, double maxLat, double toleranceMeters)
            throws SQLException {
        return loadCoastRings(minLon, minLat, maxLon, maxLat,
                toleranceMeters).rings;
    }

    /**
     * Wie {@link #loadCoast}, aber mit Auskunft darueber, was aussortiert
     * wurde.
     *
     * <h3>Warum ueberhaupt aussortiert wird, und nach welcher Regel</h3>
     *
     * <p>Zwei Gruende, und nur der erste braucht eine Zahl.</p>
     *
     * <p><b>Ausserhalb des Fensters.</b> {@code SDO_FILTER} arbeitet mit
     * dem raeumlichen Index, also mit dem umschliessenden Rechteck der
     * GANZEN Flaeche. Besteht eine Flaeche aus mehreren Teilen, koennen
     * einzelne Teile weit weg liegen und trotzdem mitkommen. Ein Ring,
     * dessen Rechteck das angefragte Fenster gar nicht beruehrt, gehoert
     * nicht in dieses Bild - das ist keine Schaetzung, sondern folgt aus
     * der Anfrage.</p>
     *
     * <p><b>Unglaubwuerdig gross.</b> Die OSM-Daten enthalten vereinzelt
     * Flaechen mit einem verirrten Stuetzpunkt, wie er in Relationen
     * vorkommt; gemessen wurde ein Ring von Helgoland bis Brandenburg.
     * Die Grenze liegt bei 0,6 Grad, und das ist keine gegriffene Zahl:
     * Die flaechengroesste Gemeinde hier ist Fehmarn mit rund 20 km in
     * Ost-West-Richtung, also gut 0,3 Grad. Wer doppelt so gross ist wie
     * die groesste, ist keine Gemeinde.</p>
     *
     * <p>Aussortiert wird NICHT in der Datenbank: Die OSM-Daten gehoeren
     * OGP und bleiben, wie sie sind. Das Weglassen ist eine Entscheidung
     * der Anzeige, sie faellt hier, und sie wird mitgeteilt statt
     * verschwiegen.</p>
     *
     * @param minLon        westlicher Rand
     * @param minLat        suedlicher Rand
     * @param maxLon        oestlicher Rand
     * @param maxLat        noerdlicher Rand
     * @param toleranceMeters Vereinfachung in Metern
     * @return Ringe und Auskunft ueber das Aussortierte
     * @throws SQLException bei Fehlern der Datenbank
     */
    public CoastRings loadCoastRings(double minLon, double minLat,
            double maxLon, double maxLat, double toleranceMeters)
            throws SQLException {
        final double maxSpan = 0.6;
        CoastRings out = new CoastRings();
        List<double[]> ringe = out.rings;
        Connection c = source.getConnection();
        PreparedStatement ps = c.prepareStatement(
            "SELECT feature_id, ring_no, lon, lat FROM TABLE("
            + "fmn_api.coast(?, ?, ?, ?, ?)) ORDER BY feature_id, ring_no, point_no");
        try {
            // Die Voreinstellung von zehn Zeilen je Rundreise ist bei
            // Zehntausenden Punkten der Unterschied zwischen einer
            // Sekunde und einer Minute. Dieselbe Lehre wie in
            // FSpatialMap.
            ps.setFetchSize(500);
            ps.setDouble(1, minLon);
            ps.setDouble(2, minLat);
            ps.setDouble(3, maxLon);
            ps.setDouble(4, maxLat);
            ps.setDouble(5, toleranceMeters);
            ResultSet rs = ps.executeQuery();
            try {
                long aktuellFeature = Long.MIN_VALUE;
                long aktuellRing = Long.MIN_VALUE;
                List<Double> punkte = new ArrayList<Double>();
                while (rs.next()) {
                    long f = rs.getLong(1);
                    long r = rs.getLong(2);
                    if (f != aktuellFeature || r != aktuellRing) {
                        schliesseRing(out, punkte, aktuellFeature, maxSpan,
                                minLon, minLat, maxLon, maxLat);
                        punkte = new ArrayList<Double>();
                        aktuellFeature = f;
                        aktuellRing = r;
                    }
                    punkte.add(Double.valueOf(rs.getDouble(3)));
                    punkte.add(Double.valueOf(rs.getDouble(4)));
                }
                schliesseRing(out, punkte, aktuellFeature, maxSpan,
                        minLon, minLat, maxLon, maxLat);
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
        return out;
    }

    private void schliesseRing(CoastRings out, List<Double> punkte,
            long featureId, double maxSpan,
            double fensterMinLon, double fensterMinLat,
            double fensterMaxLon, double fensterMaxLat) {
        // Unter drei Punkten ist es keine Flaeche, sondern ein Rest.
        if (punkte.size() < 6) {
            return;
        }
        double[] feld = new double[punkte.size()];
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        for (int i = 0; i < punkte.size(); i++) {
            feld[i] = punkte.get(i).doubleValue();
            if ((i % 2) == 0) {
                minLon = Math.min(minLon, feld[i]);
                maxLon = Math.max(maxLon, feld[i]);
            } else {
                minLat = Math.min(minLat, feld[i]);
                maxLat = Math.max(maxLat, feld[i]);
            }
        }

        // Beruehrt der Ring das angefragte Fenster ueberhaupt? Wenn nicht,
        // ist er ein Mitbringsel des Index und gehoert nicht ins Bild.
        boolean trifft = maxLon >= fensterMinLon && minLon <= fensterMaxLon
                      && maxLat >= fensterMinLat && minLat <= fensterMaxLat;
        if (!trifft) {
            out.outside++;
            return;
        }

        double span = Math.max(maxLon - minLon, maxLat - minLat);
        if (span > maxSpan) {
            out.skipped++;
            out.worstSpanDeg = Math.max(out.worstSpanDeg, span);
            if (out.skippedIds.size() < 10) {
                out.skippedIds.add(Long.valueOf(featureId));
            }
            return;
        }
        out.rings.add(feld);
        out.featureIds.add(Long.valueOf(featureId));
    }

    /**
     * Fragt die Datenbank nach der Station, die einem Punkt am
     * naechsten liegt.
     *
     * <p>Gerechnet wird mit {@code SDO_NN} ueber den raeumlichen Index,
     * nicht in Java. Das ist der Sinn der Uebung: Bei acht Stationen
     * waere beides gleich schnell, aber die Abfrage bleibt richtig, wenn
     * es achthundert werden.</p>
     *
     * @param lon Laenge
     * @param lat Breite
     * @return Stationscode, oder null
     * @throws SQLException bei Fehlern der Datenbank
     */
    public String nearestStation(double lon, double lat) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
            "{ ? = call fmn_api.nearest_station(?, ?) }");
        try {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.setDouble(2, lat);
            cs.setDouble(3, lon);
            cs.execute();
            return cs.getString(1);
        } finally {
            cs.close();
        }
    }

    /**
     * Traegt eine gemessene oder nachgeschlagene Hafenzeit ein.
     *
     * <p>Geht ausschliesslich ueber {@code FMN_API.SET_HARBOUR_TIME}. Die
     * Prozedur prueft die Plausibilitaet, verlangt eine Quelle, sobald das
     * Naeherungskennzeichen fallen soll, und schreibt den Wechsel ins
     * Protokoll - ein nacktes UPDATE taete nichts davon.</p>
     *
     * @param code            Stationscode
     * @param hwIntervalHours Hafenzeit in Stunden nach dem oertlichen
     *                        Meridiandurchgang
     * @param lwOffsetHours   Niedrigwasserversatz, oder ein Wert kleiner null,
     *                        um den bisherigen stehen zu lassen
     * @param sourceText      Herkunft; darf nicht leer sein, wenn approximate
     *                        falsch ist
     * @param approximate     wahr, solange der Wert eine Naeherung bleibt
     * @param sdMinutes       gemessene Streuung in Minuten, oder ein Wert
     *                        kleiner null, wenn nicht gemessen wurde
     * @throws SQLException wenn die Datenbank den Wert zurueckweist
     */
    public void setHarbourTime(String code, double hwIntervalHours,
            double lwOffsetHours, String sourceText, boolean approximate,
            double sdMinutes) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
            "{ call fmn_api.set_harbour_time(?, ?, ?, ?, ?, ?, ?) }");
        try {
            cs.setString(1, code);
            cs.setDouble(2, hwIntervalHours);
            if (lwOffsetHours < 0) {
                cs.setNull(3, Types.NUMERIC);
            } else {
                cs.setDouble(3, lwOffsetHours);
            }
            cs.setString(4, sourceText);
            cs.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
            cs.setString(6, approximate ? "J" : "N");
            if (sdMinutes < 0) {
                cs.setNull(7, Types.NUMERIC);
            } else {
                cs.setDouble(7, sdMinutes);
            }
            cs.execute();
            c.commit();
        } finally {
            cs.close();
        }
    }

    /**
     * Wie {@link #verifyEvents(double)}, aber mit der Toleranz, die die
     * Datenbank selbst fuer richtig haelt.
     *
     * <p>Der Wert steht als {@code FMN_API.C_PHASE_TOL_H} an genau einer
     * Stelle, mit seiner Begruendung daneben. Wer ihn hier noch einmal
     * hinschriebe, haette zwei Wahrheiten - und der naechtliche Job und
     * die Anzeige koennten ueber dieselben Daten verschieden urteilen.</p>
     *
     * @return Zahl der auffaelligen Eintraege
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int verifyEvents() throws SQLException {
        return callNumber("{ ? = call fmn_api.verify_events() }");
    }

    /**
     * Fragt die Datenbank, wie viele Hochwasser die Hafenzeit ihrer
     * Station nicht einhalten.
     *
     * @param toleranceMinutes Toleranz in Minuten
     * @return Zahl der auffaelligen Eintraege
     * @throws SQLException bei Fehlern der Datenbank
     */
    public int verifyTides(double toleranceMinutes) throws SQLException {
        return callNumber("{ ? = call fmn_api.verify_tides(?) }", toleranceMinutes);
    }

    private int callNumber(String call, double arg) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(call);
        try {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setDouble(2, arg);
            cs.execute();
            return cs.getInt(1);
        } finally {
            cs.close();
        }
    }

    private int callNumber(String call) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(call);
        try {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.execute();
            return cs.getInt(1);
        } finally {
            cs.close();
        }
    }

    /**
     * Abstand zweier Stationen in Kilometern, von der Datenbank
     * geodaetisch gerechnet.
     *
     * @param codeA erste Station
     * @param codeB zweite Station
     * @return Abstand in km
     * @throws SQLException bei Fehlern der Datenbank
     */
    public double distanceKm(String codeA, String codeB) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
                "{ ? = call fmn_api.distance_km(?, ?) }");
        try {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, codeA);
            cs.setString(3, codeB);
            cs.execute();
            return cs.getDouble(1);
        } finally {
            cs.close();
        }
    }

    /**
     * Ausbreitungsgeschwindigkeit der Tidewelle zwischen zwei Stationen.
     *
     * @param codeA erste Station
     * @param codeB zweite Station
     * @return Geschwindigkeit in Metern je Sekunde
     * @throws SQLException bei Fehlern der Datenbank
     */
    public double waveSpeedMs(String codeA, String codeB) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
                "{ ? = call fmn_api.wave_speed_ms(?, ?) }");
        try {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, codeA);
            cs.setString(3, codeB);
            cs.execute();
            return cs.getDouble(1);
        } finally {
            cs.close();
        }
    }

    /**
     * Schreibt eine Zeile ins Laufprotokoll.
     *
     * @param action Kennwort der Handlung
     * @param detail Beschreibung
     * @param rows   Zahl betroffener Zeilen
     * @throws SQLException bei Fehlern der Datenbank
     */
    public void log(String action, String detail, int rows) throws SQLException {
        Connection c = source.getConnection();
        CallableStatement cs = c.prepareCall(
                "{ call fmn_api.log_run(?, ?, ?) }");
        try {
            cs.setString(1, action);
            cs.setString(2, detail);
            cs.setInt(3, rows);
            cs.execute();
        } finally {
            cs.close();
        }
        c.commit();
    }
}
