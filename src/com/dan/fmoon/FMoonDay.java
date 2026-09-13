package com.dan.fmoon;

import com.dan.fmoon.astro.FMoonEphemeris;

/**
 * Alles, was im Kalender an einem Tag steht.
 *
 * <p>Ein Tag ist ein buergerlicher Tag am Beobachtungsort, nicht ein
 * Kalendertag in Weltzeit. Das ist mehr als eine Feinheit: Der Mondaufgang
 * am 1. Juli um 00:30 Ortszeit ist in Weltzeit noch der 30. Juni.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonDay {

    /** Tag im Monat, 1 bis 31. */
    public final int dayOfMonth;

    /** Julianisches Datum der oertlichen Mitternacht in Weltzeit. */
    public final double midnightJdUt;

    /** Angaben zum Bezugszeitpunkt des Tages. */
    public final FMoonEphemeris ephemeris;

    /** Mondaufgang als Zeitstempel, oder Long.MIN_VALUE wenn keiner. */
    public final long moonriseMillis;

    /** Monduntergang als Zeitstempel, oder Long.MIN_VALUE wenn keiner. */
    public final long moonsetMillis;

    /** Sonnenaufgang als Zeitstempel, oder Long.MIN_VALUE wenn keiner. */
    public final long sunriseMillis;

    /** Sonnenuntergang als Zeitstempel, oder Long.MIN_VALUE wenn keiner. */
    public final long sunsetMillis;

    /**
     * Faellt an diesem Tag eine der vier ausgezeichneten Phasen, so steht hier
     * ihre Art nach den Konstanten von
     * {@link com.dan.fmoon.astro.FMoonPhaseTimes}; sonst -1.
     */
    public final int phaseType;

    /** Zeitpunkt dieser Phase, oder Long.MIN_VALUE. */
    public final long phaseMillis;

    /** Faellt an diesem Tag die Erdnaehe? */
    public final boolean perigee;

    /** Faellt an diesem Tag die Erdferne? */
    public final boolean apogee;

    /**
     * Legt einen Tag an.
     *
     * @param dayOfMonth     Tag im Monat
     * @param midnightJdUt   julianisches Datum der oertlichen Mitternacht
     * @param ephemeris      Angaben zum Bezugszeitpunkt
     * @param moonriseMillis Mondaufgang
     * @param moonsetMillis  Monduntergang
     * @param sunriseMillis  Sonnenaufgang
     * @param sunsetMillis   Sonnenuntergang
     * @param phaseType      Art der Phase oder -1
     * @param phaseMillis    Zeitpunkt der Phase
     * @param perigee        Erdnaehe an diesem Tag
     * @param apogee         Erdferne an diesem Tag
     */
    public FMoonDay(int dayOfMonth, double midnightJdUt, FMoonEphemeris ephemeris,
            long moonriseMillis, long moonsetMillis,
            long sunriseMillis, long sunsetMillis,
            int phaseType, long phaseMillis, boolean perigee, boolean apogee) {
        this.dayOfMonth = dayOfMonth;
        this.midnightJdUt = midnightJdUt;
        this.ephemeris = ephemeris;
        this.moonriseMillis = moonriseMillis;
        this.moonsetMillis = moonsetMillis;
        this.sunriseMillis = sunriseMillis;
        this.sunsetMillis = sunsetMillis;
        this.phaseType = phaseType;
        this.phaseMillis = phaseMillis;
        this.perigee = perigee;
        this.apogee = apogee;
    }

    /**
     * Faellt an diesem Tag eine der vier ausgezeichneten Phasen?
     *
     * @return wahr, wenn ja
     */
    public boolean hasPhase() {
        return phaseType >= 0;
    }

    /**
     * Beleuchteter Anteil der Scheibe am Bezugszeitpunkt.
     *
     * @return Anteil, 0 bis 1
     */
    public double illuminatedFraction() {
        return ephemeris.phase.illuminatedFraction;
    }
}
