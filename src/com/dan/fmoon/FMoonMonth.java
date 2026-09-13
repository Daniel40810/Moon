package com.dan.fmoon;

import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.astro.FRiseSet;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Rechnet einen ganzen Kalendermonat fuer einen Ort durch.
 *
 * <p>Das dauert spuerbar - je Tag werden Auf- und Untergang von Mond und
 * Sonne durch Nullstellensuche bestimmt, und jede Auswertung ist eine volle
 * Reihenrechnung. Deshalb steht die Rechnung in einer eigenen Klasse, die
 * einmal je Monat aufgerufen und dann festgehalten wird, statt beim Zeichnen
 * zu geschehen.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonMonth {

    /**
     * Bezugsstunde des Tages fuer Phase und Bild, in Ortszeit.
     *
     * <p>Gewaehlt ist der Abend und nicht Mitternacht oder Mittag: Der Mond
     * im Kalender soll der sein, den man an diesem Abend sieht. Der
     * Unterschied im beleuchteten Anteil betraegt ueber einen Tag hoechstens
     * vier Prozent, aber er entscheidet, auf welchen der beiden Nachbartage
     * der Vollmond faellt.</p>
     */
    public static final int REFERENCE_HOUR = 21;

    private final int year;
    private final int month;
    private final FGeoLocation location;
    private final FMoonDay[] days;

    /**
     * Rechnet einen Monat durch.
     *
     * @param year     Jahr
     * @param month    Monat, 1 bis 12
     * @param location Beobachtungsort
     */
    public FMoonMonth(int year, int month, FGeoLocation location) {
        this.year = year;
        this.month = month;
        this.location = location;

        TimeZone zone = location.getTimeZone();
        Calendar cal = new GregorianCalendar(zone);
        cal.clear();
        cal.set(year, month - 1, 1);
        int length = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Phasen und Apsiden einmal fuer den ganzen Monat, mit einem Tag
        // Sicherheitsabstand an beiden Enden
        double jdFrom = FAstroTime.julianDay(year, month, 1.0) - 1.5;
        double jdTo = FAstroTime.julianDay(year, month, length + 1.0) + 1.5;
        List<FMoonPhaseTimes.Phase> phases = FMoonPhaseTimes.between(jdFrom, jdTo);
        double[] perigees = FMoonEphemeris.findApsides(jdFrom, jdTo, true);
        double[] apogees = FMoonEphemeris.findApsides(jdFrom, jdTo, false);

        days = new FMoonDay[length];
        for (int d = 1; d <= length; d++) {
            cal.clear();
            cal.set(year, month - 1, d, 0, 0, 0);
            long midnightMillis = cal.getTimeInMillis();
            double midnightJd = FAstroTime.julianDayFromMillis(midnightMillis);

            cal.set(Calendar.HOUR_OF_DAY, REFERENCE_HOUR);
            long refMillis = cal.getTimeInMillis();

            FMoonEphemeris eph = FMoonEphemeris.at(refMillis, location);
            FRiseSet moon = FRiseSet.moonFor(midnightJd, location);
            FRiseSet sun = FRiseSet.sunFor(midnightJd, location);

            long dayStart = midnightMillis;
            long dayEnd = midnightMillis + 86400000L;

            int phaseType = -1;
            long phaseMillis = Long.MIN_VALUE;
            for (int i = 0; i < phases.size(); i++) {
                FMoonPhaseTimes.Phase p = phases.get(i);
                long m = p.millis();
                if (m >= dayStart && m < dayEnd) {
                    phaseType = p.type;
                    phaseMillis = m;
                    break;
                }
            }

            days[d - 1] = new FMoonDay(d, midnightJd, eph,
                    toMillis(moon.riseJdUt), toMillis(moon.setJdUt),
                    toMillis(sun.riseJdUt), toMillis(sun.setJdUt),
                    phaseType, phaseMillis,
                    fallsInto(perigees, dayStart, dayEnd),
                    fallsInto(apogees, dayStart, dayEnd));
        }
    }

    private static boolean fallsInto(double[] jds, long from, long to) {
        for (int i = 0; i < jds.length; i++) {
            long m = FAstroTime.millisFromJulianDay(jds[i]);
            if (m >= from && m < to) {
                return true;
            }
        }
        return false;
    }

    private static long toMillis(double jdUt) {
        return Double.isNaN(jdUt) ? Long.MIN_VALUE : FAstroTime.millisFromJulianDay(jdUt);
    }

    /**
     * Jahr des Monats.
     *
     * @return Jahr
     */
    public int getYear() {
        return year;
    }

    /**
     * Monat, 1 bis 12.
     *
     * @return Monat
     */
    public int getMonth() {
        return month;
    }

    /**
     * Beobachtungsort.
     *
     * @return Ort
     */
    public FGeoLocation getLocation() {
        return location;
    }

    /**
     * Zahl der Tage im Monat.
     *
     * @return Tageszahl
     */
    public int getLength() {
        return days.length;
    }

    /**
     * Ein Tag des Monats.
     *
     * @param dayOfMonth Tag, 1 bis zur Monatslaenge
     * @return Angaben zu diesem Tag
     */
    public FMoonDay getDay(int dayOfMonth) {
        return days[dayOfMonth - 1];
    }

    /**
     * Wochentag des Monatsersten nach ISO (1 = Montag, 7 = Sonntag).
     *
     * @return Wochentag
     */
    public int firstWeekday() {
        Calendar cal = new GregorianCalendar(location.getTimeZone());
        cal.clear();
        cal.set(year, month - 1, 1);
        int dow = cal.get(Calendar.DAY_OF_WEEK);   // Sonntag = 1
        return (dow == Calendar.SUNDAY) ? 7 : dow - 1;
    }

    /**
     * Deutscher Name des Monats.
     *
     * @return Name
     */
    public String monthName() {
        String[] namen = {"Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember"};
        return namen[month - 1];
    }
}
