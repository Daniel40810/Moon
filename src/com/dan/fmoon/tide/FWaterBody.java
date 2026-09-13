package com.dan.fmoon.tide;

/**
 * Das Gewaesser, an dem eine Station liegt. Es entscheidet darueber, ob eine
 * Gezeitenvorhersage ueberhaupt eine sinnvolle Aussage ist.
 *
 * @author Claude fuer Daniel
 */
public enum FWaterBody {

    /**
     * Nordsee. Die halbtaegige Mondtide ist der bestimmende Vorgang, der
     * Tidenhub liegt je nach Ort zwischen knapp zwei und gut drei Metern.
     * Eine Vorhersage nach der Hafenzeit trifft die Hochwasserzeit auf eine
     * knappe Stunde genau.
     */
    NORTH_SEA("Nordsee", true),

    /**
     * Ostsee. Praktisch tidenfrei: Der astronomische Tidenhub liegt in der
     * Lubecker Bucht in der Groessenordnung von zehn bis fuenfzehn
     * Zentimetern und verschwindet vollstaendig unter dem, was Wind und
     * Luftdruck machen - ein kraeftiger Sturm verschiebt den Wasserstand um
     * mehr als einen Meter, also um das Zehnfache. Dazu kommen die
     * Eigenschwingungen des Beckens mit einer Periode von rund 26 Stunden,
     * die mit dem Mond nichts zu tun haben.
     *
     * <p>Die Rechnung liefert fuer solche Stationen zwar Zahlen, aber sie
     * sind als Vorhersage wertlos. {@link FTideStation#isTidalRangeUseful()}
     * ist deshalb falsch, und jede Anzeige hat das kenntlich zu machen.</p>
     */
    BALTIC_SEA("Ostsee", false);

    private final String displayName;
    private final boolean tidal;

    FWaterBody(String displayName, boolean tidal) {
        this.displayName = displayName;
        this.tidal = tidal;
    }

    /**
     * Anzeigename.
     *
     * @return Name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Ist an diesem Gewaesser eine Gezeitenvorhersage sinnvoll?
     *
     * @return wahr fuer die Nordsee, falsch fuer die Ostsee
     */
    public boolean isTidal() {
        return tidal;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
