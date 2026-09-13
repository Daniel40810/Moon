package com.dan.fmoon;

/**
 * Wie die Mondscheibe ausgerichtet wird.
 *
 * @author Claude fuer Daniel
 */
public enum FMoonViewMode {

    /**
     * Wie in Sternkarten und Atlanten: Der Nordpol des Mondes zeigt nach oben,
     * Osten liegt links. Die Darstellung ist vom Ort und von der Uhrzeit
     * unabhaengig und deshalb die richtige Wahl fuer einen Kalender.
     */
    GEOCENTRIC("Kartenlage (Nord oben)"),

    /**
     * Wie am Himmel: Die Scheibe ist um den parallaktischen Winkel gedreht, so
     * dass oben im Bild der Zenit ist. Erst so steht die Sichel wirklich wie
     * eine Schale oder wie ein C, je nach Jahreszeit und Uhrzeit. Nur
     * sinnvoll, solange der Mond ueber dem Horizont steht.
     */
    HORIZON("Himmelslage (Zenit oben)");

    private final String displayName;

    FMoonViewMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Anzeigename fuer Bedienelemente.
     *
     * @return Name
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
