package com.dan.fmoon.db;

import com.dan.fmoon.tide.FTideStation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Woher FMoon seine Stationen bekommt: aus der Datenbank, wenn sie
 * erreichbar ist, sonst aus den einprogrammierten Konstanten.
 *
 * <p>Der Rueckfall ist kein Notbehelf, sondern Absicht. Die Vorfuehrung
 * soll auf jedem Rechner starten, auch ohne Oracle - und ein Mondkalender,
 * der ohne Datenbank nicht einmal anzeigen kann, wann der Mond aufgeht,
 * waere eine schlechte Bibliothek. Die Datenbank fuegt hinzu, was sie
 * besser kann: Stammdaten pflegen, ohne den Code anzufassen.</p>
 *
 * <p>{@link #isFromDatabase()} sagt, welcher Weg gegriffen hat. Jede
 * Anzeige, die das interessiert, kann es zeigen - verschwiegen wird es
 * nicht.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FStationProvider {

    private final List<FTideStation> stations = new ArrayList<FTideStation>();
    private final List<String> codes = new ArrayList<String>();
    private boolean fromDatabase;
    private String note;

    private FStationProvider() {
    }

    /**
     * Liest die Stationen aus der Datenbank, faellt bei jedem Problem auf
     * die einprogrammierten Konstanten zurueck.
     *
     * <p>Bei jedem Problem heisst: keine Verbindung, kein Treiber, leeres
     * Ergebnis, Fehler in der Abfrage. Der Grund landet in
     * {@link #getNote()}, aber er haelt das Programm nicht an.</p>
     *
     * @param source Verbindung, darf null sein
     * @return Stationsquelle, niemals null und niemals leer
     */
    public static FStationProvider load(FMoonDataSource source) {
        FStationProvider p = new FStationProvider();
        if (source != null) {
            try {
                FMoonDao dao = new FMoonDao(source);
                List<FMoonDao.StationRow> zeilen = dao.loadStations();
                if (!zeilen.isEmpty()) {
                    for (int i = 0; i < zeilen.size(); i++) {
                        p.stations.add(zeilen.get(i).station);
                        p.codes.add(zeilen.get(i).code);
                    }
                    p.fromDatabase = true;
                    p.note = zeilen.size() + " Stationen aus der Datenbank";
                    return p;
                }
                p.note = "Die Datenbank antwortet, hat aber keine Stationen.";
            } catch (Throwable t) {
                // Absichtlich Throwable: Fehlt der JDBC-Treiber, kommt ein
                // NoClassDefFoundError und keine SQLException. Auch der
                // soll nur zum Rueckfall fuehren, nicht zum Absturz.
                p.note = "Datenbank nicht erreichbar (" + kurz(t) + ")";
            }
        } else {
            p.note = "Keine Verbindung angegeben";
        }
        p.useBuiltIn();
        return p;
    }

    /**
     * Nimmt ausschliesslich die einprogrammierten Stationen.
     *
     * @return Stationsquelle
     */
    public static FStationProvider builtIn() {
        FStationProvider p = new FStationProvider();
        p.note = "Einprogrammierte Stationen";
        p.useBuiltIn();
        return p;
    }

    private void useBuiltIn() {
        stations.clear();
        codes.clear();
        for (int i = 0; i < FTideStation.DEFAULTS.length; i++) {
            stations.add(FTideStation.DEFAULTS[i]);
            codes.add(null);
        }
        fromDatabase = false;
    }

    private static String kurz(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        int nl = m.indexOf('\n');
        if (nl > 0) {
            m = m.substring(0, nl);
        }
        return m.length() > 120 ? m.substring(0, 120) + "..." : m;
    }

    /**
     * Die Stationen.
     *
     * @return unveraenderliche Liste, niemals leer
     */
    public List<FTideStation> getStations() {
        return Collections.unmodifiableList(stations);
    }

    /**
     * Die Stationen als Feld, wie es eine FComboBox erwartet.
     *
     * @return Feld der Stationen
     */
    public FTideStation[] toArray() {
        return stations.toArray(new FTideStation[stations.size()]);
    }

    /**
     * Der Datenbank-Code einer Station, oder null bei den
     * einprogrammierten.
     *
     * @param station Station
     * @return Code, oder null
     */
    public String codeOf(FTideStation station) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i) == station) {
                return codes.get(i);
            }
        }
        // Auch nach Namen suchen: Nach einem Neuladen sind es andere
        // Objekte mit demselben Inhalt.
        for (int i = 0; i < stations.size(); i++) {
            if (station != null
                    && stations.get(i).getName().equals(station.getName())) {
                return codes.get(i);
            }
        }
        return null;
    }

    /**
     * Kamen die Stationen aus der Datenbank?
     *
     * @return wahr, wenn ja
     */
    public boolean isFromDatabase() {
        return fromDatabase;
    }

    /**
     * Ein Satz darueber, welcher Weg gegriffen hat und warum.
     *
     * @return Hinweistext
     */
    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return (fromDatabase ? "Datenbank: " : "Eingebaut: ") + note;
    }
}
