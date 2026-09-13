package com.dan.fmoon.tide;

import java.io.Serializable;

/**
 * Ein Hoch- oder Niedrigwasser mit Zeitpunkt und Hoehe.
 *
 * <p>Die Hoehe wird in Metern <b>ueber Mittelwasser</b> angegeben, nicht ueber
 * Seekartennull. Das ist die ehrlichere Bezugsflaeche fuer eine Rechnung, die
 * nur den Tidenhub kennt: Seekartennull haengt an amtlich festgelegten
 * Bezugswerten je Ort, die hier nicht vorliegen.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FTideMoment implements Serializable, Comparable<FTideMoment> {

    private static final long serialVersionUID = 1L;

    private final long timeMillis;
    private final boolean highWater;
    private final double levelMeters;
    private final double rangeMeters;
    private final long moonTransitMillis;
    private final boolean upperTransit;

    /**
     * Legt ein Gezeitenereignis an.
     *
     * @param timeMillis        Zeitpunkt
     * @param highWater         wahr fuer Hochwasser
     * @param levelMeters       Wasserstand ueber Mittelwasser in Metern
     * @param rangeMeters       zugehoeriger Tidenhub in Metern
     * @param moonTransitMillis der Meridiandurchgang, aus dem dieses Ereignis
     *                          abgeleitet wurde
     * @param upperTransit      wahr, wenn es die obere Kulmination war
     */
    public FTideMoment(long timeMillis, boolean highWater, double levelMeters,
            double rangeMeters, long moonTransitMillis, boolean upperTransit) {
        this.timeMillis = timeMillis;
        this.highWater = highWater;
        this.levelMeters = levelMeters;
        this.rangeMeters = rangeMeters;
        this.moonTransitMillis = moonTransitMillis;
        this.upperTransit = upperTransit;
    }

    /**
     * Zeitpunkt des Ereignisses.
     *
     * @return Millisekunden seit 1970-01-01
     */
    public long getTimeMillis() {
        return timeMillis;
    }

    /**
     * Hochwasser oder Niedrigwasser?
     *
     * @return wahr bei Hochwasser
     */
    public boolean isHighWater() {
        return highWater;
    }

    /**
     * Wasserstand ueber Mittelwasser.
     *
     * @return Hoehe in Metern
     */
    public double getLevelMeters() {
        return levelMeters;
    }

    /**
     * Der Tidenhub, aus dem dieses Ereignis stammt.
     *
     * @return Hub in Metern
     */
    public double getRangeMeters() {
        return rangeMeters;
    }

    /**
     * Der Meridiandurchgang des Mondes, aus dem dieses Ereignis abgeleitet
     * wurde. Damit laesst sich die Kette Mond - Hafenzeit - Hochwasser in der
     * Anzeige sichtbar machen.
     *
     * @return Millisekunden seit 1970-01-01
     */
    public long getMoonTransitMillis() {
        return moonTransitMillis;
    }

    /**
     * War es die obere Kulmination (Mond im Sueden) oder die untere?
     *
     * @return wahr bei der oberen
     */
    public boolean isUpperTransit() {
        return upperTransit;
    }

    /**
     * Kurzbezeichnung.
     *
     * @return "HW" oder "NW"
     */
    public String shortLabel() {
        return highWater ? "HW" : "NW";
    }

    /**
     * Deutscher Name.
     *
     * @return "Hochwasser" oder "Niedrigwasser"
     */
    public String displayName() {
        return highWater ? "Hochwasser" : "Niedrigwasser";
    }

    @Override
    public int compareTo(FTideMoment other) {
        return (timeMillis < other.timeMillis) ? -1
                : ((timeMillis > other.timeMillis) ? 1 : 0);
    }

    @Override
    public String toString() {
        return String.format("%s %+.2f m", shortLabel(), levelMeters);
    }
}
