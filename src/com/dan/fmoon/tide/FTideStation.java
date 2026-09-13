package com.dan.fmoon.tide;

import com.dan.fmoon.astro.FGeoLocation;
import java.io.Serializable;

/**
 * Ein Pegelort mit den Kennzahlen, die die Hafenzeitrechnung braucht.
 *
 * <h3>Die Werte sind Naeherungen</h3>
 *
 * <p>Die hier hinterlegten Hafenzeiten und Tidenhuebe sind <b>gerundete
 * Naeherungswerte</b>, keine amtlichen Angaben. Sie geben die Groessenordnung
 * und vor allem die zeitliche Abfolge entlang der Kueste richtig wieder - die
 * Tidewelle laeuft von Nordwesten in die Deutsche Bucht hinein, erreicht
 * Helgoland zuerst und Cuxhaven zuletzt - aber sie ersetzen keine
 * Gezeitentafel. Wer sie ersetzen will, traegt eigene Werte ein; die Klasse
 * ist dafuer offen.</p>
 *
 * <h3>Der absolute Bezug ist gemessen, die Abstaende sind geschaetzt</h3>
 *
 * <p>Die Hafenzeiten der Nordseestationen standen zuerst rund zehn
 * Stunden zu hoch. Aufgefallen ist das erst, als die harmonischen
 * Konstanten fuer Cuxhaven aus {@code com.dan.ftidalgauge} ein Jahr lang
 * Hochwasser erzeugten und daraus 1,45 h gemessen wurden statt der
 * angenommenen 11,90 h. Alle fuenf Nordseestationen sind deshalb um
 * denselben Betrag verschoben worden: Die Abstaende entlang der Kueste
 * bleiben, wie sie waren, der absolute Bezug kommt jetzt aus der
 * Messung.</p>
 *
 * <p>Was das NICHT heisst: dass die Abstaende stimmen. Eine kurze Reihe
 * beobachteter Hochwasser fuer Buesum legt einen um gut eine Stunde
 * anderen Abstand zu Cuxhaven nahe. Solange keine Gezeitentafel
 * vorliegt, bleibt das offen - und deshalb bleibt auch
 * {@link #isApproximate()} wahr.</p>
 *
 * <p>{@link #isApproximate()} liefert bei allen mitgelieferten Stationen
 * wahr. Jede Anzeige soll das kenntlich machen, statt Zahlen zu zeigen, die
 * genauer aussehen als sie sind.</p>
 *
 * <p>Wer echte Werte hat, traegt sie in der Datenbank ein - dort ueber
 * {@code FMN_API.SET_HARBOUR_TIME}, das eine Quelle verlangt und den Wechsel
 * protokolliert. Von da an liefert {@link #isApproximate()} falsch und
 * {@link #getSource()} die Herkunft; am Code ist nichts zu aendern.</p>
 *
 * <h3>Wie die Hafenzeit gemeint ist</h3>
 *
 * <p>Die Hafenzeit (das Lunitidalintervall) ist der Abstand zwischen dem
 * <b>Meridiandurchgang des Mondes am Ort</b> und dem darauf folgenden
 * Hochwasser. Sie wird hier ausdruecklich auf die oertliche Kulmination
 * bezogen, nicht auf die in Greenwich: So ist sie eine Eigenschaft des Ortes
 * allein und haengt nicht davon ab, auf welchem Laengengrad man sie misst.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FTideStation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final FGeoLocation location;
    private final FWaterBody waterBody;
    private final double highWaterIntervalHours;
    private final double lowWaterOffsetHours;
    private final double springRangeMeters;
    private final double neapRangeMeters;
    private final boolean approximate;
    private final String source;

    /**
     * Legt eine Station an.
     *
     * @param name                   Anzeigename
     * @param location               Ort
     * @param waterBody              Gewaesser
     * @param highWaterIntervalHours Hafenzeit in Stunden nach dem oertlichen
     *                               Meridiandurchgang des Mondes
     * @param lowWaterOffsetHours    Abstand des Niedrigwassers vom Hochwasser
     *                               in Stunden
     * @param springRangeMeters      Tidenhub zur Springzeit in Metern
     * @param neapRangeMeters        Tidenhub zur Nippzeit in Metern
     * @param approximate            wahr, wenn die Werte gerundete Naeherungen sind
     */
    public FTideStation(String name, FGeoLocation location, FWaterBody waterBody,
            double highWaterIntervalHours, double lowWaterOffsetHours,
            double springRangeMeters, double neapRangeMeters, boolean approximate) {
        this(name, location, waterBody, highWaterIntervalHours,
                lowWaterOffsetHours, springRangeMeters, neapRangeMeters,
                approximate, null);
    }

    /**
     * Legt eine Station an und nennt die Herkunft ihrer Kennzahlen.
     *
     * <p>Die Herkunft gehoert zum Wert. Eine Hafenzeit ohne Quelle laesst
     * sich ein Jahr spaeter nicht mehr beurteilen - niemand weiss dann noch,
     * ob sie aus einer Gezeitentafel kam, aus einer Messung oder aus einer
     * Schaetzung. Deshalb traegt die Station sie mit, und
     * {@link #qualifier()} sagt sie an.</p>
     *
     * @param name                   Anzeigename
     * @param location               Ort
     * @param waterBody              Gewaesser
     * @param highWaterIntervalHours Hafenzeit in Stunden nach dem oertlichen
     *                               Meridiandurchgang des Mondes
     * @param lowWaterOffsetHours    Abstand des Niedrigwassers vom Hochwasser
     *                               in Stunden
     * @param springRangeMeters      Tidenhub zur Springzeit in Metern
     * @param neapRangeMeters        Tidenhub zur Nippzeit in Metern
     * @param approximate            wahr, wenn die Werte gerundete Naeherungen sind
     * @param source                 Herkunft der Kennzahlen, darf null sein
     */
    public FTideStation(String name, FGeoLocation location, FWaterBody waterBody,
            double highWaterIntervalHours, double lowWaterOffsetHours,
            double springRangeMeters, double neapRangeMeters,
            boolean approximate, String source) {
        this.source = source;
        this.name = name;
        this.location = location;
        this.waterBody = waterBody;
        this.highWaterIntervalHours = highWaterIntervalHours;
        this.lowWaterOffsetHours = lowWaterOffsetHours;
        this.springRangeMeters = springRangeMeters;
        this.neapRangeMeters = neapRangeMeters;
        this.approximate = approximate;
    }

    // ------------------------------------------------------------------
    // Mitgelieferte Stationen - alle als Naeherung gekennzeichnet
    // ------------------------------------------------------------------

    /** Helgoland, weit draussen; die Tidewelle erreicht die Insel zuerst. */
    public static final FTideStation HELGOLAND = new FTideStation(
            "Helgoland", FGeoLocation.HELGOLAND, FWaterBody.NORTH_SEA,
            0.402, 6.21, 2.6, 1.9, true);

    /** List auf Sylt, im Norden der Deutschen Bucht. */
    public static final FTideStation LIST_SYLT = new FTideStation(
            "List auf Sylt", FGeoLocation.LIST_SYLT, FWaterBody.NORTH_SEA,
            0.752, 6.21, 2.1, 1.5, true);

    /** Buesum an der Dithmarscher Kueste. */
    public static final FTideStation BUESUM = new FTideStation(
            "Büsum", FGeoLocation.BUESUM, FWaterBody.NORTH_SEA,
            1.002, 6.25, 3.6, 2.7, true);

    /** Husum, tief in der Bucht; dort laeuft die Tide am hoechsten auf. */
    public static final FTideStation HUSUM = new FTideStation(
            "Husum", FGeoLocation.HUSUM, FWaterBody.NORTH_SEA,
            1.552, 6.30, 3.8, 2.9, true);

    /** Cuxhaven an der Elbmuendung. */
    public static final FTideStation CUXHAVEN = new FTideStation(
            "Cuxhaven", FGeoLocation.CUXHAVEN, FWaterBody.NORTH_SEA,
            1.452, 6.35, 3.3, 2.5, true);

    /** Timmendorfer Strand an der Lubecker Bucht - gezeitenarm. */
    public static final FTideStation TIMMENDORFER_STRAND = new FTideStation(
            "Timmendorfer Strand", FGeoLocation.TIMMENDORFER_STRAND,
            FWaterBody.BALTIC_SEA, 6.50, 6.21, 0.15, 0.08, true);

    /** Niendorfer Hafen - gezeitenarm. */
    public static final FTideStation NIENDORF = new FTideStation(
            "Niendorf/Ostsee", FGeoLocation.NIENDORF,
            FWaterBody.BALTIC_SEA, 6.50, 6.21, 0.15, 0.08, true);

    /** Luebeck-Travemuende - gezeitenarm. */
    public static final FTideStation LUEBECK = new FTideStation(
            "Lübeck", FGeoLocation.LUEBECK,
            FWaterBody.BALTIC_SEA, 6.70, 6.21, 0.16, 0.09, true);

    /**
     * Alle mitgelieferten Stationen, Nordsee zuerst.
     */
    public static final FTideStation[] DEFAULTS = {
        HELGOLAND, LIST_SYLT, BUESUM, HUSUM, CUXHAVEN,
        TIMMENDORFER_STRAND, NIENDORF, LUEBECK
    };

    // ------------------------------------------------------------------
    // Zugriff
    // ------------------------------------------------------------------

    /**
     * Anzeigename.
     *
     * @return Name
     */
    public String getName() {
        return name;
    }

    /**
     * Ort der Station.
     *
     * @return Ort
     */
    public FGeoLocation getLocation() {
        return location;
    }

    /**
     * Gewaesser.
     *
     * @return Gewaesser
     */
    public FWaterBody getWaterBody() {
        return waterBody;
    }

    /**
     * Hafenzeit: Abstand zwischen dem oertlichen Meridiandurchgang des Mondes
     * und dem darauf folgenden Hochwasser.
     *
     * @return Hafenzeit in Stunden
     */
    public double getHighWaterIntervalHours() {
        return highWaterIntervalHours;
    }

    /**
     * Abstand des Niedrigwassers vom vorangehenden Hochwasser.
     *
     * @return Abstand in Stunden
     */
    public double getLowWaterOffsetHours() {
        return lowWaterOffsetHours;
    }

    /**
     * Tidenhub zur Springzeit.
     *
     * @return Hub in Metern
     */
    public double getSpringRangeMeters() {
        return springRangeMeters;
    }

    /**
     * Tidenhub zur Nippzeit.
     *
     * @return Hub in Metern
     */
    public double getNeapRangeMeters() {
        return neapRangeMeters;
    }

    /**
     * Mittlerer Tidenhub.
     *
     * @return Hub in Metern
     */
    public double getMeanRangeMeters() {
        return 0.5 * (springRangeMeters + neapRangeMeters);
    }

    /**
     * Sind die Kennzahlen gerundete Naeherungen?
     *
     * @return wahr bei allen mitgelieferten Stationen
     */
    public boolean isApproximate() {
        return approximate;
    }

    /**
     * Woher die Kennzahlen stammen.
     *
     * @return Herkunft, oder null wenn keine angegeben ist
     */
    public String getSource() {
        return source;
    }

    /**
     * Ist an dieser Station eine Gezeitenvorhersage sinnvoll? Fuer die Ostsee
     * falsch: Dort ist der Wasserstand wetterbestimmt, und eine Hochwasserzeit
     * anzugeben waere eine Zahl ohne Aussage.
     *
     * @return wahr an der Nordsee
     */
    public boolean isTidalRangeUseful() {
        return waterBody.isTidal();
    }

    /**
     * Ein Satz, der die Aussagekraft der Zahlen dieser Station beschreibt.
     * Gedacht zum unveraenderten Anzeigen unter der Vorhersage.
     *
     * @return Hinweistext
     */
    public String qualifier() {
        if (!isTidalRangeUseful()) {
            return "Gezeitenarm – der Wasserstand wird hier von Wind und "
                    + "Luftdruck bestimmt, nicht vom Mond.";
        }
        if (approximate) {
            return "Hafenzeit und Tidenhub sind gerundete Näherungswerte, "
                    + "keine amtliche Gezeitentafel.";
        }
        // Nicht mehr Näherung - dann gehört gesagt, worauf die Zahl beruht.
        // Ein wortloses Verschwinden des Hinweises wäre die schlechtere
        // Anzeige: Der Leser wüsste nicht, ob jemand echte Werte eingetragen
        // oder nur das Kennzeichen umgestellt hat.
        if (source != null && source.trim().length() > 0) {
            return "Hafenzeit nach " + source.trim() + ".";
        }
        return "";
    }

    @Override
    public String toString() {
        return name;
    }
}
