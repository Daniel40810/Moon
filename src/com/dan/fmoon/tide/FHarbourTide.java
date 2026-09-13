package com.dan.fmoon.tide;

import com.dan.fmoon.astro.FAngle;
import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FMoonPosition;
import com.dan.fmoon.astro.FRiseSet;
import com.dan.fmoon.astro.FSun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gezeitenvorhersage nach der Hafenzeit - dem klassischen seemaennischen
 * Verfahren.
 *
 * <h3>Der Gedanke</h3>
 *
 * <p>Das Hochwasser folgt dem Mond. An einem gegebenen Ort tritt es immer im
 * gleichen zeitlichen Abstand zum Meridiandurchgang des Mondes ein; dieser
 * Abstand heisst Hafenzeit oder Lunitidalintervall und ist eine Eigenschaft
 * des Ortes. Weil der Mond zweimal am Tag durch den Meridian geht - oben im
 * Sueden und unten unter dem Horizont - gibt es zwei Hochwasser je Mondtag,
 * also alle 12 Stunden 25 Minuten.</p>
 *
 * <p>Damit ist die Vorhersage eine Kette aus drei Gliedern, die alle sichtbar
 * bleiben: Meridiandurchgang (aus dem Rechenkern), plus Hafenzeit (aus der
 * Station), ergibt Hochwasser. Das ist der Reiz dieses Verfahrens gegenueber
 * der harmonischen Analyse - man sieht dem Mond beim Wassermachen zu.</p>
 *
 * <h3>Was gerechnet wird und was nicht</h3>
 *
 * <p>Die Zeiten stammen aus dem Meridiandurchgang, den
 * {@link FRiseSet#meridianTransit} liefert. Wichtig: Gemeint ist der Durchgang
 * im Sinne des Stundenwinkels, nicht das Hoehenmaximum - beim Mond laufen
 * beide bis zu fuenf Minuten auseinander.</p>
 *
 * <p>Der Tidenhub schwankt mit zwei Einfluessen, die beide eingerechnet sind:
 * der <b>Spring-Nipp-Schwankung</b> (Sonne und Mond ziehen zu Neu- und
 * Vollmond gemeinsam, zu den Vierteln gegeneinander) und der
 * <b>Mondentfernung</b> (die Gezeitenkraft faellt mit der dritten Potenz des
 * Abstands, zwischen Erdnaehe und Erdferne macht das rund vierzig Prozent
 * Unterschied in der Kraft aus).</p>
 *
 * <p>Nicht gerechnet werden: Wind und Luftdruck, die Ungleichheit der beiden
 * Tageshochwasser bei hoher Monddeklination, Flachwasserverformung und
 * Oberwasser der Fluesse. Wer diese Groessen braucht, braucht die harmonische
 * Analyse oder eine Gezeitentafel.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FHarbourTide {

    /**
     * Mittlerer Mondtag in Stunden: die Zeit zwischen zwei oberen
     * Meridiandurchgaengen des Mondes.
     */
    public static final double LUNAR_DAY_HOURS = 24.8412;

    /** Mittlerer Abstand zwischen zwei Hochwassern in Stunden. */
    public static final double TIDE_PERIOD_HOURS = LUNAR_DAY_HOURS / 2.0;

    /** Mittlerer Abstand Erde-Mond in Kilometern. */
    private static final double MEAN_DISTANCE_KM = 385000.56;

    /**
     * Alter der Tide in Grad der Laengendifferenz Mond-Sonne.
     *
     * <p>Die Springtide faellt nicht auf den Vollmond selbst, sondern ein bis
     * zwei Tage danach - das Wasser braucht Zeit, dem Antrieb zu folgen. Die
     * Laengendifferenz waechst um 12,19 Grad je Tag; 18 Grad entsprechen also
     * rund anderthalb Tagen.</p>
     */
    public static final double TIDE_AGE_DEGREES = 18.0;

    private FHarbourTide() {
    }

    /**
     * Berechnet alle Hoch- und Niedrigwasser einer Station in einem Zeitraum.
     *
     * @param station Station
     * @param fromMillis Beginn des Zeitraums
     * @param toMillis   Ende des Zeitraums
     * @return Ereignisse in zeitlicher Reihenfolge
     */
    public static List<FTideMoment> events(FTideStation station,
            long fromMillis, long toMillis) {

        List<FTideMoment> out = new ArrayList<FTideMoment>();

        // Der Zeitraum wird vorne und hinten erweitert: Ein Hochwasser kann
        // aus einem Meridiandurchgang des Vortages stammen, und die Hafenzeit
        // betraegt bis zu zwoelf Stunden.
        double jdFrom = FAstroTime.julianDayFromMillis(fromMillis) - 1.0;
        double jdTo = FAstroTime.julianDayFromMillis(toMillis) + 1.0;

        for (double jd = Math.floor(jdFrom); jd <= jdTo; jd += 1.0) {
            for (int k = 0; k < 2; k++) {
                boolean upper = (k == 0);
                double transit = FRiseSet.meridianTransit(jd, station.getLocation(), upper);
                if (Double.isNaN(transit)) {
                    continue;
                }
                long transitMillis = FAstroTime.millisFromJulianDay(transit);

                double hwJd = transit + station.getHighWaterIntervalHours() / 24.0;
                long hwMillis = FAstroTime.millisFromJulianDay(hwJd);
                double range = rangeAt(station, hwJd);

                if (hwMillis >= fromMillis && hwMillis <= toMillis) {
                    out.add(new FTideMoment(hwMillis, true, range / 2.0, range,
                            transitMillis, upper));
                }

                double lwJd = hwJd + station.getLowWaterOffsetHours() / 24.0;
                long lwMillis = FAstroTime.millisFromJulianDay(lwJd);
                double lwRange = rangeAt(station, lwJd);
                if (lwMillis >= fromMillis && lwMillis <= toMillis) {
                    out.add(new FTideMoment(lwMillis, false, -lwRange / 2.0, lwRange,
                            transitMillis, upper));
                }
            }
        }

        Collections.sort(out);
        return removeDuplicates(out);
    }

    /**
     * Ein Meridiandurchgang kann von zwei aufeinanderfolgenden Tagen aus
     * gefunden werden. Ereignisse, die weniger als drei Stunden auseinander
     * liegen und dieselbe Art haben, sind dasselbe Ereignis.
     */
    private static List<FTideMoment> removeDuplicates(List<FTideMoment> in) {
        List<FTideMoment> out = new ArrayList<FTideMoment>();
        for (int i = 0; i < in.size(); i++) {
            FTideMoment m = in.get(i);
            boolean schonDa = false;
            for (int j = 0; j < out.size(); j++) {
                FTideMoment o = out.get(j);
                if (o.isHighWater() == m.isHighWater()
                        && Math.abs(o.getTimeMillis() - m.getTimeMillis()) < 3 * 3600000L) {
                    schonDa = true;
                    break;
                }
            }
            if (!schonDa) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Der Tidenhub zu einem Zeitpunkt, aus Spring-Nipp-Schwankung und
     * Mondentfernung.
     *
     * @param station Station
     * @param jdUt    julianisches Datum in Weltzeit
     * @return Hub in Metern
     */
    public static double rangeAt(FTideStation station, double jdUt) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        FSun sun = FSun.at(jde);
        FMoonPosition moon = FMoonPosition.at(jde);

        // Laengendifferenz Mond minus Sonne: 0 = Neumond, 180 = Vollmond.
        // Der Gezeitenantrieb ist bei beiden gleich gross, deshalb geht der
        // doppelte Winkel ein - die Schwankung hat die halbe Periode des
        // Monats, also rund vierzehneinhalb Tage.
        double dLon = FAngle.norm360(moon.longitude - sun.apparentLongitude);
        double phase = Math.toRadians(2.0 * (dLon - TIDE_AGE_DEGREES));

        double mittel = 0.5 * (station.getSpringRangeMeters() + station.getNeapRangeMeters());
        double halbeSpanne = 0.5 * (station.getSpringRangeMeters()
                - station.getNeapRangeMeters());
        double range = mittel + halbeSpanne * Math.cos(phase);

        // Mondentfernung: Die Gezeitenkraft faellt mit der dritten Potenz des
        // Abstands. Weil nur der Mondanteil betroffen ist und dieser rund zwei
        // Drittel der Gesamtkraft ausmacht, wird die Schwankung entsprechend
        // gewichtet.
        double kraft = Math.pow(MEAN_DISTANCE_KM / moon.distanceKm, 3.0);
        range *= 1.0 + 0.68 * (kraft - 1.0);

        return Math.max(0.0, range);
    }

    /**
     * Der Wasserstand zu einem beliebigen Zeitpunkt, durch Interpolation
     * zwischen den umgebenden Ereignissen.
     *
     * <p>Interpoliert wird mit einem Kosinus. Das ist genau die alte
     * Zwoelftelregel der Seefahrt - in der ersten Stunde nach dem Niedrigwasser
     * steigt das Wasser um ein Zwoelftel des Hubs, dann um zwei, drei, drei,
     * zwei, eins - nur ohne die Rundung auf Zwoelftel.</p>
     *
     * <p>Diese Fassung rechnet sich ihre Ereignisse selbst und ist damit
     * bequem, aber teuer: Jeder Aufruf sucht acht Meridiandurchgaenge. Wer
     * eine ganze Kurve zeichnet, fragt hundertfach nach derselben Auskunft -
     * dafuer gibt es {@link #levelAt(FTideStation, long, List)}.</p>
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @return Wasserstand ueber Mittelwasser in Metern
     */
    public static double levelAt(FTideStation station, long timeMillis) {
        long fenster = (long) (TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        return levelAt(station, timeMillis,
                events(station, timeMillis - fenster, timeMillis + fenster));
    }

    /**
     * Der Wasserstand zu einem Zeitpunkt aus einer bereits berechneten
     * Ereignisliste.
     *
     * <p>Der teure Teil der Gezeitenrechnung ist der Meridiandurchgang, nicht
     * die Interpolation dazwischen. Wer viele Zeitpunkte desselben Zeitraums
     * braucht - eine Kurve ueber die Breite eines Fensters zum Beispiel -,
     * berechnet die Ereignisse einmal mit {@link #events} und reicht sie hier
     * herein. Das ist derselbe Rechenweg, nur ohne die Wiederholung.</p>
     *
     * <p>Die Liste muss den gesuchten Zeitpunkt <b>umschliessen</b>: Liegt er
     * vor dem ersten oder hinter dem letzten Ereignis, kann nicht interpoliert
     * werden und es kommt der jeweilige Randwert zurueck. Wer eine Kurve von
     * A bis B zeichnet, holt die Ereignisse deshalb von A minus einer Tide bis
     * B plus einer Tide.</p>
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @param um         Ereignisse in zeitlicher Reihenfolge, den Zeitpunkt
     *                   umschliessend
     * @return Wasserstand ueber Mittelwasser in Metern
     */
    public static double levelAt(FTideStation station, long timeMillis,
            List<FTideMoment> um) {
        if (um == null || um.isEmpty()) {
            return 0.0;
        }

        FTideMoment vor = null;
        FTideMoment nach = null;
        for (int i = 0; i < um.size(); i++) {
            FTideMoment m = um.get(i);
            if (m.getTimeMillis() <= timeMillis) {
                vor = m;
            } else {
                nach = m;
                break;
            }
        }
        if (vor == null) {
            return um.get(0).getLevelMeters();
        }
        if (nach == null) {
            return vor.getLevelMeters();
        }

        double t = (timeMillis - vor.getTimeMillis())
                / (double) (nach.getTimeMillis() - vor.getTimeMillis());
        double a = vor.getLevelMeters();
        double b = nach.getLevelMeters();
        return a + (b - a) * 0.5 * (1.0 - Math.cos(Math.PI * t));
    }

    /**
     * Das naechste Hochwasser nach einem Zeitpunkt.
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @return das naechste Hochwasser, oder null
     */
    public static FTideMoment nextHighWater(FTideStation station, long timeMillis) {
        return next(station, timeMillis, true);
    }

    /**
     * Das naechste Niedrigwasser nach einem Zeitpunkt.
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @return das naechste Niedrigwasser, oder null
     */
    public static FTideMoment nextLowWater(FTideStation station, long timeMillis) {
        return next(station, timeMillis, false);
    }

    private static FTideMoment next(FTideStation station, long timeMillis, boolean high) {
        long fenster = (long) (TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        List<FTideMoment> alle = events(station, timeMillis, timeMillis + fenster);
        for (int i = 0; i < alle.size(); i++) {
            FTideMoment m = alle.get(i);
            if (m.isHighWater() == high && m.getTimeMillis() > timeMillis) {
                return m;
            }
        }
        return null;
    }

    /**
     * Steigt oder faellt das Wasser gerade?
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @return wahr, wenn das Wasser steigt (Flut), falsch bei Ebbe
     */
    public static boolean isRising(FTideStation station, long timeMillis) {
        long fenster = (long) (TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        return isRising(station, timeMillis,
                events(station, timeMillis - fenster, timeMillis + fenster));
    }

    /**
     * Steigt oder faellt das Wasser gerade, aus einer bereits berechneten
     * Ereignisliste?
     *
     * @param station    Station
     * @param timeMillis Zeitpunkt
     * @param um         Ereignisse in zeitlicher Reihenfolge
     * @return wahr, wenn das Wasser steigt (Flut), falsch bei Ebbe
     */
    public static boolean isRising(FTideStation station, long timeMillis,
            List<FTideMoment> um) {
        double jetzt = levelAt(station, timeMillis, um);
        double gleich = levelAt(station, timeMillis + 600000L, um);
        return gleich > jetzt;
    }
}
