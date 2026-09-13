import com.dan.fmoon.astro.*;

/**
 * Prueft den Rechenkern gegen die veroeffentlichten Beispiele bei Meeus.
 * Die Sollwerte stammen aus <i>Astronomical Algorithms</i>, 2. Auflage,
 * und nicht aus eigenen Rechnungen - das ist der ganze Sinn der Uebung.
 */
public class FMoonAstroTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String what, double actual, double expected, double tol, String unit) {
        double diff = Math.abs(actual - expected);
        boolean ok = diff <= tol;
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-46s ist=%-16.6f soll=%-16.6f diff=%.2e %s%n",
                ok ? "  OK  " : " FEHL ", what, actual, expected, diff, unit);
    }

    /**
     * Sucht den Augenblick, in dem die Laengendifferenz Mond minus Sonne einen
     * vorgegebenen Wert annimmt - ausschliesslich aus den Positionsreihen der
     * Kapitel 25 und 47, ohne die Phasenreihe aus Kapitel 49 zu benutzen.
     */
    static double nullstelleDerLaengendifferenz(double jdNahe, double zielGrad) {
        double lo = jdNahe - 0.5;
        double hi = jdNahe + 0.5;
        for (int i = 0; i < 40; i++) {
            double mid = 0.5 * (lo + hi);
            if (abstandVomZiel(lo, zielGrad) * abstandVomZiel(mid, zielGrad) <= 0.0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static double kleinsterAbstand(double[] t) {
        double min = Double.MAX_VALUE;
        for (int i = 1; i < t.length; i++) {
            min = Math.min(min, t[i] - t[i - 1]);
        }
        return min;
    }

    static double groessterAbstand(double[] t) {
        double max = 0.0;
        for (int i = 1; i < t.length; i++) {
            max = Math.max(max, t[i] - t[i - 1]);
        }
        return max;
    }

    static double abstandVomZiel(double jdUt, double zielGrad) {
        double jde = jdUt + FAstroTime.deltaTDays(jdUt);
        double diff = FMoonPosition.at(jde).longitude - FSun.at(jde).apparentLongitude;
        return FAngle.norm180(diff - zielGrad);
    }

    public static void main(String[] args) {
        System.out.println("=== Julianisches Datum (Meeus Kapitel 7) ===");
        check("1957 Okt 4,81 -> JD", FAstroTime.julianDay(1957, 10, 4.81), 2436116.31, 1e-6, "d");
        check("2000 Jan 1,5 -> JD", FAstroTime.julianDay(2000, 1, 1.5), 2451545.0, 1e-9, "d");
        check("1987 Jan 27,0 -> JD", FAstroTime.julianDay(1987, 1, 27.0), 2446822.5, 1e-9, "d");
        check("333 Jan 27,5 -> JD (julian.)", FAstroTime.julianDay(333, 1, 27.5), 1842713.0, 1e-9, "d");
        check("-4712 Jan 1,5 -> JD", FAstroTime.julianDay(-4712, 1, 1.5), 0.0, 1e-6, "d");

        int[] cal = FAstroTime.calendarOf(2436116.31);
        check("Rueckrechnung Jahr", cal[0], 1957, 0, "");
        check("Rueckrechnung Monat", cal[1], 10, 0, "");
        check("Rueckrechnung Tag", cal[2], 4, 0, "");

        System.out.println();
        System.out.println("=== Schiefe der Ekliptik (Meeus Beispiel 22.a) ===");
        // 1987 April 10, 0h TD -> JDE 2446895.5
        double jdeObl = 2446895.5;
        double tObl = FAstroTime.centuries(jdeObl);
        check("mittlere Schiefe eps0", FAstroTime.meanObliquity(tObl),
                23.0 + 26.0 / 60.0 + 27.407 / 3600.0, 1e-6, "Grad");
        check("Nutation Delta psi", FAstroTime.nutationInLongitude(tObl) * 3600.0,
                -3.788, 0.02, "Bogensek");
        check("Nutation Delta eps", FAstroTime.nutationInObliquity(tObl) * 3600.0,
                9.443, 0.02, "Bogensek");
        check("wahre Schiefe eps", FAstroTime.trueObliquity(tObl),
                23.0 + 26.0 / 60.0 + 36.850 / 3600.0, 1e-4, "Grad");

        System.out.println();
        System.out.println("=== Sternzeit (Meeus Beispiele 12.a und 12.b) ===");
        // 1987 April 10, 0h UT -> JD 2446895.5, mittlere Sternzeit 13h10m46,3668s
        double theta0 = FAstroTime.meanSiderealTime(2446895.5);
        check("mittlere Sternzeit 1987-04-10 0h", theta0 / 15.0,
                13.0 + 10.0 / 60.0 + 46.3668 / 3600.0, 1e-6, "Stunden");
        // 1987 April 10, 19h21m00s UT -> JD 2446896.30625, 8h34m57,0896s
        double theta1 = FAstroTime.meanSiderealTime(2446896.30625);
        check("mittlere Sternzeit 1987-04-10 19h21m", theta1 / 15.0,
                8.0 + 34.0 / 60.0 + 57.0896 / 3600.0, 1e-5, "Stunden");

        System.out.println();
        System.out.println("=== Sonne, volle VSOP87-Reihe (Meeus Beispiel 25.b) ===");
        // 1992 Oktober 13, 0h TD -> JDE 2448908.5.
        // Geprueft werden die drei Groessen, die Meeus dort unveraendert aus
        // der VSOP87-Reihe abdruckt - also ohne FK5, Nutation und Aberration,
        // deren Betraege hinterher einzeln nachgewiesen werden.
        FSun sun = FSun.at(2448908.5);
        check("heliozentrische Laenge L der Erde", sun.heliocentricLongitude, 19.907372, 1e-6, "Grad");
        check("heliozentrische Breite B der Erde", sun.heliocentricLatitude, -0.000179, 1e-6, "Grad");
        check("Radiusvektor R", sun.distanceAu, 0.99760775, 1e-8, "AE");
        // geozentrische Laenge = L + 180, danach vier dokumentierte Schritte
        double theta = FAngle.norm360(sun.heliocentricLongitude + 180.0);
        check("geozentrische Laenge Theta = L + 180", theta, 199.907372, 1e-6, "Grad");
        double korrektur = FAngle.norm180(sun.apparentLongitude - theta) * 3600.0;
        check("Summe FK5 + Nutation + Aberration", korrektur, -4.7, 0.5, "Bogensek");

        // Umkehrprobe: aus Rektaszension und Deklination zurueck auf die Ekliptik
        double epsRueck = Math.toRadians(FAstroTime.trueObliquity(FAstroTime.centuries(2448908.5)));
        double raR = Math.toRadians(sun.rightAscension);
        double decR = Math.toRadians(sun.declination);
        double lamZurueck = Math.toDegrees(Math.atan2(
                Math.sin(raR) * Math.cos(epsRueck) + Math.tan(decR) * Math.sin(epsRueck),
                Math.cos(raR)));
        check("Umkehrprobe RA/Dec -> lambda", FAngle.norm360(lamZurueck),
                sun.apparentLongitude, 1e-8, "Grad");

        // unabhaengige Fixpunkte: zur Sonnenwende steht die Sonne in der Schiefe,
        // zur Tagundnachtgleiche im Himmelsaequator
        FSun sonnenwende = FSun.at(FAstroTime.julianDay(2026, 6, 21.0 + 8.0 / 24.0));
        check("Deklination zur Sommersonnenwende", sonnenwende.declination, 23.44, 0.02, "Grad");
        FSun tagnacht = FSun.at(FAstroTime.julianDay(2026, 3, 20.0 + 14.0 / 24.0));
        check("Deklination zur Tagundnachtgleiche", tagnacht.declination, 0.0, 0.05, "Grad");

        System.out.println();
        System.out.println("=== Mond, Tabellen 47.A und 47.B (Meeus Beispiel 47.a) ===");
        // 1992 April 12, 0h TD -> JDE 2448724.5
        FMoonPosition moon = FMoonPosition.at(2448724.5);
        check("mittlere Laenge L'", moon.meanLongitude, 134.290182, 1e-5, "Grad");
        check("mittlere Elongation D", moon.meanElongation, 113.842304, 1e-5, "Grad");
        check("mittlere Anomalie Sonne M", 0.0, 0.0, 1.0, "");
        check("mittlere Anomalie Mond M'", moon.meanAnomaly, 5.150833, 1e-5, "Grad");
        check("Argument der Breite F", moon.argumentOfLatitude, 219.889721, 1e-5, "Grad");
        check("geometrische Laenge lambda", moon.longitudeWithoutNutation, 133.162655, 1e-5, "Grad");
        check("Breite beta", moon.latitude, -3.229126, 1e-5, "Grad");
        check("Abstand Delta", moon.distanceKm, 368409.7, 0.1, "km");
        check("Parallaxe pi", moon.parallax, 0.991990, 1e-5, "Grad");
        check("scheinbare Laenge (mit Nutation)", moon.longitude, 133.167265, 1e-4, "Grad");
        // Der Knoten wird an Meeus Beispiel 22.a geprueft (1987 April 10,0 TD),
        // wo Omega = 11,2531 Grad abgedruckt ist - Beispiel 47.a nennt ihn nicht.
        check("Knoten Omega (Beispiel 22.a)", FMoonPosition.at(2446895.5).ascendingNode,
                11.2531, 2e-3, "Grad");

        System.out.println();
        System.out.println("=== Scheinbare Groesse des Mondes ===");
        // Perigaeum rund 356500 km, Apogaeum rund 406700 km
        check("Durchmesser bei 356500 km", 2.0 * Math.toDegrees(Math.asin(1737.4 / 356500.0)) * 60.0,
                33.5, 0.2, "Bogenmin");
        check("Durchmesser bei 406700 km", 2.0 * Math.toDegrees(Math.asin(1737.4 / 406700.0)) * 60.0,
                29.4, 0.2, "Bogenmin");

        System.out.println();
        System.out.println("=== Beleuchtung (Meeus Beispiel 48.a) ===");
        FMoonPhase phase = FMoonPhase.at(2448724.5);
        check("Phasenwinkel i", phase.phaseAngle, 69.0756, 1e-4, "Grad");
        check("beleuchteter Anteil k", phase.illuminatedFraction, 0.6786, 1e-4, "");
        check("k aus i zurueckgerechnet",
                (1.0 + Math.cos(Math.toRadians(phase.phaseAngle))) / 2.0,
                phase.illuminatedFraction, 1e-12, "");

        System.out.println();
        System.out.println("=== Libration (Meeus Beispiel 53.a) ===");
        FLibration lib = FLibration.at(2448724.5);
        check("Libration in Laenge l'", lib.longitude, -1.206, 1e-3, "Grad");
        check("Libration in Breite b'", lib.latitude, 4.194, 1e-3, "Grad");

        System.out.println();
        System.out.println("=== Phasenzeitpunkte (Meeus Beispiele 49.a und 49.b) ===");
        // Endwerte, nicht die mittleren Phasen: Meeus druckt beide ab.
        check("Neumond Februar 1977 (k = -283)",
                FMoonPhaseTimes.phaseAt(-283.0).jde, 2443192.65118, 1e-5, "d");
        check("letztes Viertel Januar 2044 (k = 544,75)",
                FMoonPhaseTimes.phaseAt(544.75).jde, 2467636.49186, 1e-5, "d");

        System.out.println();
        System.out.println("=== Kreuzprobe: zwei unabhaengige Wege zum selben Vollmond ===");
        // Kapitel 49 hat eine eigene Reihe fuer die Phasenzeitpunkte, die mit
        // den Positionsreihen aus Kapitel 25 und 47 nichts zu tun hat. Wenn
        // beide denselben Augenblick liefern, stimmen sehr wahrscheinlich beide.
        for (int monat = 1; monat <= 12; monat += 3) {
            double start = FAstroTime.julianDay(2026, monat, 1.0);
            FMoonPhaseTimes.Phase voll = FMoonPhaseTimes.next(start, FMoonPhaseTimes.FULL_MOON);
            double ausReihe = nullstelleDerLaengendifferenz(voll.jdUt, 180.0);
            double abweichungMinuten = Math.abs(ausReihe - voll.jdUt) * 1440.0;
            check("Vollmond nach " + 2026 + "-" + monat + ": beide Wege",
                    abweichungMinuten, 0.0, 1.2, "Minuten");
        }
        for (int monat = 2; monat <= 11; monat += 3) {
            double start = FAstroTime.julianDay(2026, monat, 1.0);
            FMoonPhaseTimes.Phase neu = FMoonPhaseTimes.next(start, FMoonPhaseTimes.NEW_MOON);
            double ausReihe = nullstelleDerLaengendifferenz(neu.jdUt, 0.0);
            double abweichungMinuten = Math.abs(ausReihe - neu.jdUt) * 1440.0;
            check("Neumond nach " + 2026 + "-" + monat + ": beide Wege",
                    abweichungMinuten, 0.0, 1.2, "Minuten");
        }

        System.out.println();
        System.out.println("=== Synodischer Monat aus den gerechneten Neumonden ===");
        // Die periodischen Korrekturen erreichen 0,6 Tage; erst ueber viele
        // tausend Umlaeufe mitteln sie sich heraus. Ueber hundert Umlaeufe
        // bliebe ein Rest von mehreren Minuten stehen - dann misst man die
        // Korrekturen und nicht die mittlere Laenge.
        double ersterNeumond = FMoonPhaseTimes.phaseAt(0.0).jde;
        double neumondSpaeter = FMoonPhaseTimes.phaseAt(12000.0).jde;
        check("mittlere Laenge aus 12000 Umlaeufen",
                (neumondSpaeter - ersterNeumond) / 12000.0, 29.530588861, 5e-4, "Tage");

        // Der anomalistische Monat (Perigaeum zu Perigaeum) betraegt
        // 27,554550 Tage - eine unabhaengige veroeffentlichte Konstante.
        // Der einzelne Abstand schwankt durch die Evektion zwischen 24,6 und
        // 28,6 Tagen, und diese Schwankung hat selbst eine Periode von rund
        // 206 Tagen. Ueber zwei Jahre gemittelt bleibt davon noch ein Zehntel
        // Tag stehen; erst ueber zwei Jahrzehnte ist der Mittelwert belastbar.
        double[] perigaeen = FMoonEphemeris.findApsides(
                FAstroTime.julianDay(2020, 1, 1.0), FAstroTime.julianDay(2040, 1, 1.0), true);
        check("Zahl der Perigaeen in zwanzig Jahren", perigaeen.length, 265, 3, "");
        check("kuerzester Abstand zweier Perigaeen",
                kleinsterAbstand(perigaeen), 24.6, 0.6, "Tage");
        check("laengster Abstand zweier Perigaeen",
                groessterAbstand(perigaeen), 28.6, 0.4, "Tage");
        double mittel = (perigaeen[perigaeen.length - 1] - perigaeen[0])
                / (perigaeen.length - 1);
        check("anomalistischer Monat, ueber 20 Jahre gemittelt",
                mittel, 27.554550, 0.01, "Tage");

        System.out.println();
        System.out.println("=== Auf- und Untergang, Timmendorfer Strand ===");
        FGeoLocation ts = FGeoLocation.TIMMENDORFER_STRAND;
        // Zur Tagundnachtgleiche ist der lichte Tag ueberall rund zwoelf Stunden.
        double tagnachtJd = FAstroTime.julianDay(2026, 3, 20.0) - 1.0 / 24.0;
        FRiseSet sonneTagnacht = FRiseSet.sunFor(tagnachtJd, ts);
        double tageslaenge = (sonneTagnacht.setJdUt - sonneTagnacht.riseJdUt) * 24.0;
        check("Tageslaenge zur Tagundnachtgleiche", tageslaenge, 12.17, 0.2, "Stunden");

        // Zur Sommersonnenwende in 54 Grad Nord rund 17 Stunden.
        double sonnwendJd = FAstroTime.julianDay(2026, 6, 21.0) - 2.0 / 24.0;
        FRiseSet sonneSonnwend = FRiseSet.sunFor(sonnwendJd, ts);
        check("Tageslaenge zur Sommersonnenwende",
                (sonneSonnwend.setJdUt - sonneSonnwend.riseJdUt) * 24.0, 17.1, 0.4, "Stunden");

        // Zur Wintersonnenwende rund sieben Stunden.
        double winterJd = FAstroTime.julianDay(2026, 12, 21.0) - 1.0 / 24.0;
        FRiseSet sonneWinter = FRiseSet.sunFor(winterJd, ts);
        check("Tageslaenge zur Wintersonnenwende",
                (sonneWinter.setJdUt - sonneWinter.riseJdUt) * 24.0, 7.1, 0.4, "Stunden");

        // Der Mond geht im Mittel knapp 50 Minuten je Tag spaeter auf.
        double summe = 0.0;
        int zaehler = 0;
        double vorher = Double.NaN;
        for (int tag = 0; tag < 28; tag++) {
            double jd = FAstroTime.julianDay(2026, 9, 1.0 + tag) - 2.0 / 24.0;
            FRiseSet m = FRiseSet.moonFor(jd, ts);
            if (m.hasRise()) {
                if (!Double.isNaN(vorher)) {
                    double delta = (m.riseJdUt - vorher) * 1440.0;
                    if (delta > 0.0 && delta < 2880.0) {
                        summe += delta - 1440.0;
                        zaehler++;
                    }
                }
                vorher = m.riseJdUt;
            }
        }
        check("mittlere taegliche Verspaetung des Mondaufgangs",
                summe / zaehler, 50.0, 8.0, "Minuten");

        // Der Vollmond geht auf, wenn die Sonne untergeht - beide stehen sich
        // gegenueber. Zulaessig ist eine Stunde, weil die Bahnneigung den
        // Zeitpunkt je nach Jahreszeit verschiebt.
        FMoonPhaseTimes.Phase vollSept = FMoonPhaseTimes.next(
                FAstroTime.julianDay(2026, 9, 1.0), FMoonPhaseTimes.FULL_MOON);
        double mitternachtVoll = Math.floor(vollSept.jdUt - 0.5) + 0.5 - 2.0 / 24.0;
        FRiseSet mondVoll = FRiseSet.moonFor(mitternachtVoll, ts);
        FRiseSet sonneVoll = FRiseSet.sunFor(mitternachtVoll, ts);
        if (mondVoll.hasRise() && sonneVoll.hasSet()) {
            check("Vollmondaufgang gegen Sonnenuntergang",
                    Math.abs(mondVoll.riseJdUt - sonneVoll.setJdUt) * 24.0, 0.0, 1.0, "Stunden");
        }

        // Die Kulmination liegt zwischen Auf- und Untergang.
        FRiseSet heute = FRiseSet.moonFor(FAstroTime.julianDay(2026, 9, 12.0) - 2.0 / 24.0, ts);
        if (heute.hasRise() && heute.hasSet() && heute.hasTransit()
                && heute.riseJdUt < heute.setJdUt) {
            boolean dazwischen = heute.transitJdUt > heute.riseJdUt
                    && heute.transitJdUt < heute.setJdUt;
            check("Kulmination liegt zwischen Auf- und Untergang", dazwischen ? 1 : 0, 1, 0, "");
        }

        System.out.println();
        System.out.println("=== Entfernung und scheinbare Groesse ===");
        double perigaeum = FMoonEphemeris.findApsis(
                FAstroTime.julianDay(2026, 9, 1.0), FAstroTime.julianDay(2026, 10, 1.0), true);
        double apogaeum = FMoonEphemeris.findApsis(
                FAstroTime.julianDay(2026, 9, 1.0), FAstroTime.julianDay(2026, 10, 1.0), false);
        double dPeri = FMoonPosition.at(perigaeum + FAstroTime.deltaTDays(perigaeum)).distanceKm;
        double dApo = FMoonPosition.at(apogaeum + FAstroTime.deltaTDays(apogaeum)).distanceKm;
        // Das Perigaeum selbst schwankt von Monat zu Monat zwischen rund
        // 356400 und 370400 km, das Apogaeum zwischen 404000 und 406700 km.
        // Geprueft wird deshalb die Zugehoerigkeit zu diesem Bereich, nicht
        // ein einzelner Wert.
        check("Perigaeum im bekannten Bereich",
                (dPeri > 356000.0 && dPeri < 371000.0) ? 1 : 0, 1, 0, "");
        check("Apogaeum im bekannten Bereich",
                (dApo > 403500.0 && dApo < 407000.0) ? 1 : 0, 1, 0, "");
        check("Perigaeum liegt naeher als Apogaeum", dApo - dPeri > 30000.0 ? 1 : 0, 1, 0, "");

        System.out.println();
        System.out.println("=== Neigung der Sichel am Himmel ===");
        // Der parallaktische Winkel verschwindet beim MERIDIANDURCHGANG, also
        // bei Stundenwinkel null - dort steht der Himmelsnordpol senkrecht
        // ueber dem Gestirn. Er verschwindet NICHT beim Hoehenmaximum: Weil
        // sich die Deklination des Mondes um bis zu 0,2 Grad je Stunde
        // aendert, liegen beide Augenblicke bis zu fuenf Minuten auseinander.
        double tagJd = FAstroTime.julianDay(2026, 9, 12.0) - 2.0 / 24.0;
        double meridian = FRiseSet.meridianTransit(tagJd, ts, true);
        if (!Double.isNaN(meridian)) {
            FMoonEphemeris eph = FMoonEphemeris.atJd(meridian, ts);
            check("Stundenwinkel beim Meridiandurchgang",
                    Math.abs(FAngle.norm180(FRiseSet.hourAngle(meridian, ts))), 0.0, 1e-4, "Grad");
            check("parallaktischer Winkel beim Meridiandurchgang",
                    Math.abs(eph.parallacticAngle), 0.0, 1e-3, "Grad");
        }
        FRiseSet rs = FRiseSet.moonFor(tagJd, ts);
        if (rs.hasTransit() && !Double.isNaN(meridian)) {
            double versatzMinuten = Math.abs(rs.transitJdUt - meridian) * 1440.0;
            check("Hoehenmaximum und Meridiandurchgang liegen auseinander",
                    (versatzMinuten > 0.2 && versatzMinuten < 12.0) ? 1 : 0, 1, 0, "");
        }

        System.out.println();
        System.out.println("=== Meridiandurchgang ueber lange Sicht ===");
        // Der Durchgang wird mit einem groben Raster gesucht und dann halbiert.
        // Beides ist am 13.09.2026 zugunsten der Geschwindigkeit gekuerzt
        // worden - von zehn Minuten auf eine halbe Stunde und von 30 auf 22
        // Halbierungen. Die Anzeige wurde dadurch von zweieinhalb Sekunden auf
        // neun Millisekunden schneller. Damit das nicht heimlich weitergeht,
        // bis der Raster einen Durchgang ueberspringt, steht hier die Grenze:
        // Was das Raster findet, muss es an JEDEM Tag finden, und das Ergebnis
        // muss den Stundenwinkel wirklich auf null bringen.
        FGeoLocation[] proben = {
            new FGeoLocation("Buesum", 54.1333, 8.8578, 2.0, "Europe/Berlin"),
            new FGeoLocation("Tromsoe", 69.65, 18.96, 0.0, "Europe/Oslo")
        };
        int fehlende = 0;
        int verglichen = 0;
        double schlimmsterWinkel = 0.0;
        double groesterAbstand = 0.0;
        double kleinsterAbstand = Double.MAX_VALUE;
        for (int p = 0; p < proben.length; p++) {
            double letzter = Double.NaN;
            for (int d = 0; d < 400; d++) {
                double jd = 2461000.0 + d;
                double t = FRiseSet.meridianTransit(jd, proben[p], true);
                if (Double.isNaN(t)) {
                    fehlende++;
                    continue;
                }
                verglichen++;
                schlimmsterWinkel = Math.max(schlimmsterWinkel,
                        Math.abs(FAngle.norm180(FRiseSet.hourAngle(t, proben[p]))));
                if (!Double.isNaN(letzter)) {
                    double abstandH = (t - letzter) * 24.0;
                    // Ein uebersprungener Durchgang zeigt sich als doppelter
                    // Abstand; ein doppelt gefundener als halber.
                    if (abstandH > 12.0) {
                        groesterAbstand = Math.max(groesterAbstand, abstandH);
                        kleinsterAbstand = Math.min(kleinsterAbstand, abstandH);
                    }
                }
                letzter = t;
            }
        }
        // NICHT jeder Tag hat einen oberen Durchgang, und das ist richtig so:
        // Der Mondtag dauert 24,84 Stunden, ein buergerlicher nur 24. Alle
        // 29,5 Tage rutscht ein Durchgang deshalb ueber die Mitternacht
        // hinaus. Erwartet werden 400 * (1 - 24/24,8412) = 13,5 Luecken je
        // Ort. Die erste Fassung dieser Pruefung verlangte null Luecken und
        // war damit rot, obwohl nichts kaputt war - eine Pruefung mit einer
        // falschen Annahme, wie sie in diesem Projekt schon mehrfach vorkam.
        // Was sie wirklich findet: einen Raster, der Durchgaenge verschluckt -
        // der zeigt sich als DEUTLICH mehr Luecken. Gegengeprobt: Bei zwei
        // Schritten je Tag (zwoelf Stunden) wird sie rot, bei sechs (vier
        // Stunden) nicht. Die Suche ist also bis vier Stunden herab
        // unempfindlich; die Pruefung markiert die Klippe, nicht den Hang.
        // Die Genauigkeit des Ergebnisses bewacht die naechste Zeile: mit
        // vier statt 22 Halbierungen wird sie rot (0,23 Grad).
        double erwarteteLuecken = proben.length * 400.0 * (1.0 - 24.0 / 24.8412);
        check("Luecken nur so viele, wie der Mondtag erzwingt",
                fehlende, erwarteteLuecken, 4.0, "Tage");
        check("geprueft ueber zwei Orte und 400 Tage", verglichen >= 760 ? 1 : 0, 1, 0, "");
        check("Stundenwinkel wird wirklich null", schlimmsterWinkel, 0.0, 1e-3, "Grad");
        check("groesster Abstand zweier Durchgaenge", groesterAbstand, 24.84, 0.60, "Stunden");
        check("kleinster Abstand zweier Durchgaenge", kleinsterAbstand, 24.84, 0.60, "Stunden");

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }
}
