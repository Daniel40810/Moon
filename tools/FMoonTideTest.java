import com.dan.fmoon.astro.*;
import com.dan.fmoon.tide.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Prueft die Hafenzeitrechnung gegen das, was fuer Gezeiten gelten MUSS:
 * Periode, Abfolge, Spring- und Nippzeit, Mondentfernung, Interpolation.
 *
 * <p>Die Hafenzeiten selbst sind Naeherungswerte und koennen deshalb nicht
 * gegen amtliche Zeiten geprueft werden. Pruefbar ist alles andere: dass die
 * Rechnung die Regeln einhaelt, denen jede Tide gehorcht.</p>
 */
public class FMoonTideTest {

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
        System.out.printf("%s %-54s ist=%-14.4f soll=%-14.4f %s%n",
                ok ? "  OK  " : " FEHL ", what, actual, expected, unit);
    }

    static void checkTrue(String what, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-54s %s%n", ok ? "  OK  " : " FEHL ", what, detail);
    }

    static long millis(int y, int m, int d, int h) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("Europe/Berlin"));
        c.clear();
        c.set(y, m - 1, d, h, 0, 0);
        return c.getTimeInMillis();
    }

    public static void main(String[] args) {
        FTideStation bue = FTideStation.BUESUM;
        long von = millis(2026, 9, 1, 0);
        long bis = millis(2026, 10, 1, 0);

        System.out.println("=== Zahl und Abstand der Ereignisse ===");
        List<FTideMoment> alle = FHarbourTide.events(bue, von, bis);
        int hw = 0;
        int nw = 0;
        for (FTideMoment m : alle) {
            if (m.isHighWater()) {
                hw++;
            } else {
                nw++;
            }
        }
        // Ein Mondtag dauert 24 h 50 min, in 30 Tagen passen 29 davon.
        checkTrue("Zahl der Hochwasser in 30 Tagen", hw >= 56 && hw <= 59, hw + " Stueck");
        checkTrue("Zahl der Niedrigwasser in 30 Tagen", nw >= 56 && nw <= 59, nw + " Stueck");

        // Mittlerer Abstand zweier Hochwasser: die halbe Mondtageslaenge.
        List<Long> hwZeiten = new ArrayList<Long>();
        for (FTideMoment m : alle) {
            if (m.isHighWater()) {
                hwZeiten.add(m.getTimeMillis());
            }
        }
        double summe = 0.0;
        double kuerzest = Double.MAX_VALUE;
        double laengst = 0.0;
        for (int i = 1; i < hwZeiten.size(); i++) {
            double d = (hwZeiten.get(i) - hwZeiten.get(i - 1)) / 3600000.0;
            summe += d;
            kuerzest = Math.min(kuerzest, d);
            laengst = Math.max(laengst, d);
        }
        check("mittlerer Abstand zweier Hochwasser",
                summe / (hwZeiten.size() - 1), 12.4206, 0.05, "Stunden");
        checkTrue("kein Abstand entartet",
                kuerzest > 11.0 && laengst < 14.0,
                String.format("%.2f bis %.2f Stunden", kuerzest, laengst));

        System.out.println();
        System.out.println("=== Die Kette Mond - Hafenzeit - Hochwasser ===");
        double groessteAbweichung = 0.0;
        for (FTideMoment m : alle) {
            if (!m.isHighWater()) {
                continue;
            }
            double abstand = (m.getTimeMillis() - m.getMoonTransitMillis()) / 3600000.0;
            groessteAbweichung = Math.max(groessteAbweichung,
                    Math.abs(abstand - bue.getHighWaterIntervalHours()));
        }
        check("Hochwasser folgt der Kulmination um die Hafenzeit",
                groessteAbweichung, 0.0, 1e-6, "Stunden");

        // Niedrigwasser liegt zwischen zwei Hochwassern
        int dazwischen = 0;
        int geprueft = 0;
        for (int i = 0; i + 2 < alle.size(); i++) {
            FTideMoment a = alle.get(i);
            FTideMoment b = alle.get(i + 1);
            FTideMoment c = alle.get(i + 2);
            if (a.isHighWater() && !b.isHighWater() && c.isHighWater()) {
                geprueft++;
                if (b.getTimeMillis() > a.getTimeMillis()
                        && b.getTimeMillis() < c.getTimeMillis()) {
                    dazwischen++;
                }
            }
        }
        checkTrue("Hoch- und Niedrigwasser wechseln sich ab",
                geprueft > 40 && dazwischen == geprueft,
                dazwischen + " von " + geprueft);

        System.out.println();
        System.out.println("=== Spring- und Nippzeit ===");
        // Der groesste Hub muss kurz NACH Voll- oder Neumond liegen, der
        // kleinste kurz nach einem Viertel. Das ist die Springverspaetung.
        FTideMoment groesster = null;
        FTideMoment kleinster = null;
        for (FTideMoment m : alle) {
            if (!m.isHighWater()) {
                continue;
            }
            if (groesster == null || m.getRangeMeters() > groesster.getRangeMeters()) {
                groesster = m;
            }
            if (kleinster == null || m.getRangeMeters() < kleinster.getRangeMeters()) {
                kleinster = m;
            }
        }
        checkTrue("Springhub groesser als Nipphub",
                groesster.getRangeMeters() > kleinster.getRangeMeters() * 1.25,
                String.format("%.2f m gegen %.2f m",
                        groesster.getRangeMeters(), kleinster.getRangeMeters()));

        double jdGross = FAstroTime.julianDayFromMillis(groesster.getTimeMillis());
        FMoonPhaseTimes.Phase vollVorher = FMoonPhaseTimes.previous(jdGross,
                FMoonPhaseTimes.FULL_MOON);
        FMoonPhaseTimes.Phase neuVorher = FMoonPhaseTimes.previous(jdGross,
                FMoonPhaseTimes.NEW_MOON);
        double abstandTage = Math.min(jdGross - vollVorher.jdUt, jdGross - neuVorher.jdUt);
        checkTrue("groesster Hub folgt Voll- oder Neumond mit Verspaetung",
                abstandTage > 0.5 && abstandTage < 3.0,
                String.format("%.2f Tage danach", abstandTage));

        double jdKlein = FAstroTime.julianDayFromMillis(kleinster.getTimeMillis());
        FMoonPhaseTimes.Phase ersteVorher = FMoonPhaseTimes.previous(jdKlein,
                FMoonPhaseTimes.FIRST_QUARTER);
        FMoonPhaseTimes.Phase letzteVorher = FMoonPhaseTimes.previous(jdKlein,
                FMoonPhaseTimes.LAST_QUARTER);
        double abstandNipp = Math.min(jdKlein - ersteVorher.jdUt, jdKlein - letzteVorher.jdUt);
        checkTrue("kleinster Hub folgt einem Viertel mit Verspaetung",
                abstandNipp > 0.5 && abstandNipp < 3.0,
                String.format("%.2f Tage danach", abstandNipp));

        // Die Schwankung des Hubs hat die halbe Monatsperiode: In einem Monat
        // gibt es ZWEI Springzeiten und zwei Nippzeiten.
        int hubMaxima = 0;
        double vorher2 = -1;
        double vorher1 = -1;
        for (FTideMoment m : alle) {
            if (!m.isHighWater()) {
                continue;
            }
            double r = m.getRangeMeters();
            if (vorher2 > 0 && vorher1 > vorher2 && vorher1 >= r) {
                hubMaxima++;
            }
            vorher2 = vorher1;
            vorher1 = r;
        }
        checkTrue("zwei Springzeiten je Monat", hubMaxima == 2, hubMaxima + " Maxima");

        System.out.println();
        System.out.println("=== Einfluss der Mondentfernung ===");
        // Bei gleicher Phasenlage muss die Erdnaehe einen groesseren Hub geben
        // als die Erdferne. Gesucht werden zwei Vollmonde, von denen einer
        // nahe am Perigaeum liegt.
        double[] perigaeen = FMoonEphemeris.findApsides(
                FAstroTime.julianDay(2026, 1, 1.0), FAstroTime.julianDay(2027, 1, 1.0), true);
        double[] apogaeen = FMoonEphemeris.findApsides(
                FAstroTime.julianDay(2026, 1, 1.0), FAstroTime.julianDay(2027, 1, 1.0), false);
        double hubNah = 0.0;
        double hubFern = 0.0;
        double besteNah = 99.0;
        double besteFern = 99.0;
        for (int i = 0; i < 13; i++) {
            FMoonPhaseTimes.Phase voll = FMoonPhaseTimes.next(
                    FAstroTime.julianDay(2026, 1, 1.0) + i * 29.53, FMoonPhaseTimes.FULL_MOON);
            double r = FHarbourTide.rangeAt(bue, voll.jdUt);
            for (double p : perigaeen) {
                double d = Math.abs(p - voll.jdUt);
                if (d < besteNah) {
                    besteNah = d;
                    hubNah = r;
                }
            }
            for (double a : apogaeen) {
                double d = Math.abs(a - voll.jdUt);
                if (d < besteFern) {
                    besteFern = d;
                    hubFern = r;
                }
            }
        }
        checkTrue("Vollmond in Erdnaehe gibt mehr Hub als in Erdferne",
                hubNah > hubFern * 1.05,
                String.format("%.2f m gegen %.2f m", hubNah, hubFern));

        System.out.println();
        System.out.println("=== Wasserstandskurve ===");
        // Am Hochwasser muss der Stand ein Maximum sein.
        FTideMoment einHw = null;
        for (FTideMoment m : alle) {
            if (m.isHighWater()) {
                einHw = m;
                break;
            }
        }
        double amHw = FHarbourTide.levelAt(bue, einHw.getTimeMillis());
        double vorHw = FHarbourTide.levelAt(bue, einHw.getTimeMillis() - 3600000L);
        double nachHw = FHarbourTide.levelAt(bue, einHw.getTimeMillis() + 3600000L);
        checkTrue("Hochwasser ist ein Maximum der Kurve",
                amHw > vorHw && amHw > nachHw,
                String.format("%.2f / %.2f / %.2f m", vorHw, amHw, nachHw));
        check("Stand am Hochwasser entspricht dem halben Hub",
                amHw, einHw.getRangeMeters() / 2.0, 0.02, "m");

        // Zwischen Niedrig- und Hochwasser steigt das Wasser durchgehend
        FTideMoment einNw = null;
        for (FTideMoment m : alle) {
            if (!m.isHighWater() && m.getTimeMillis() > einHw.getTimeMillis()) {
                einNw = m;
                break;
            }
        }
        boolean faelltDurchgehend = true;
        long schritt = (einNw.getTimeMillis() - einHw.getTimeMillis()) / 20;
        double letzter = Double.MAX_VALUE;
        for (long t = einHw.getTimeMillis(); t <= einNw.getTimeMillis(); t += schritt) {
            double l = FHarbourTide.levelAt(bue, t);
            if (l > letzter + 1e-9) {
                faelltDurchgehend = false;
            }
            letzter = l;
        }
        checkTrue("zwischen Hoch- und Niedrigwasser faellt das Wasser stetig",
                faelltDurchgehend, "");

        checkTrue("kurz nach dem Hochwasser faellt das Wasser",
                !FHarbourTide.isRising(bue, einHw.getTimeMillis() + 1800000L), "");
        checkTrue("kurz nach dem Niedrigwasser steigt das Wasser",
                FHarbourTide.isRising(bue, einNw.getTimeMillis() + 1800000L), "");

        System.out.println();
        System.out.println("=== Abfolge entlang der Kueste ===");
        // Die Tidewelle laeuft von draussen in die Deutsche Bucht hinein:
        // Helgoland zuerst, die Kuestenorte danach.
        long bezug = millis(2026, 9, 15, 0);
        FTideMoment helHw = FHarbourTide.nextHighWater(FTideStation.HELGOLAND, bezug);
        FTideMoment bueHw = FHarbourTide.nextHighWater(FTideStation.BUESUM, bezug);
        FTideMoment cuxHw = FHarbourTide.nextHighWater(FTideStation.CUXHAVEN, bezug);
        SimpleDateFormat f = new SimpleDateFormat("dd.MM. HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        System.out.println("       Helgoland " + f.format(new Date(helHw.getTimeMillis()))
                + ", Büsum " + f.format(new Date(bueHw.getTimeMillis()))
                + ", Cuxhaven " + f.format(new Date(cuxHw.getTimeMillis())));
        checkTrue("Helgoland vor Buesum",
                helHw.getTimeMillis() < bueHw.getTimeMillis(), "");
        checkTrue("Buesum vor Cuxhaven",
                bueHw.getTimeMillis() < cuxHw.getTimeMillis(), "");
        checkTrue("Helgoland und Cuxhaven liegen rund eine Stunde auseinander",
                Math.abs(cuxHw.getTimeMillis() - helHw.getTimeMillis()) < 2 * 3600000L,
                String.format("%.0f Minuten",
                        (cuxHw.getTimeMillis() - helHw.getTimeMillis()) / 60000.0));

        System.out.println();
        System.out.println("=== Ostsee wird nicht als Vorhersage ausgegeben ===");
        FTideStation ts = FTideStation.TIMMENDORFER_STRAND;
        checkTrue("Timmendorf ist als gezeitenarm gekennzeichnet",
                !ts.isTidalRangeUseful(), "");
        checkTrue("Hinweistext nennt Wind und Luftdruck",
                ts.qualifier().contains("Wind") && ts.qualifier().contains("Luftdruck"),
                ts.qualifier());
        checkTrue("Ostsee-Hub liegt unter zwanzig Zentimetern",
                ts.getSpringRangeMeters() < 0.20,
                String.format("%.2f m zur Springzeit", ts.getSpringRangeMeters()));
        checkTrue("Nordsee-Hub ist mindestens zehnmal groesser",
                FTideStation.BUESUM.getMeanRangeMeters()
                > ts.getMeanRangeMeters() * 10.0,
                String.format("%.2f m gegen %.2f m",
                        FTideStation.BUESUM.getMeanRangeMeters(), ts.getMeanRangeMeters()));
        checkTrue("alle mitgelieferten Stationen sind als Naeherung gekennzeichnet",
                alleNaeherung(), "");

        System.out.println();
        System.out.println("=== Modell und Rechnung sagen dasselbe ===");
        // Das Modell fuer die Pegelanzeige rechnet aus einem Zwischenspeicher.
        // Wenn es dabei auch nur um Zentimeter von der direkten Rechnung
        // abweicht, gaebe es zwei Wahrheiten - eine in der Anzeige und eine
        // in der Tabelle darunter.
        com.dan.fmoon.tide.FHarbourTideModel modell =
                new com.dan.fmoon.tide.FHarbourTideModel(bue);
        double groessterUnterschied = 0.0;
        long start = millis(2026, 9, 10, 0);
        for (int i = 0; i < 400; i++) {
            long t = start + i * 600000L;
            double ausModell = modell.getLevelAt(t);
            double direkt = FHarbourTide.levelAt(bue, t);
            groessterUnterschied = Math.max(groessterUnterschied,
                    Math.abs(ausModell - direkt));
        }
        check("Pegelmodell und direkte Rechnung stimmen ueberein",
                groessterUnterschied, 0.0, 1e-9, "m");

        List<com.dan.ftidalgauge.FTideEvent> ausModell =
                modell.findEvents(start, start + 5L * 86400000L);
        List<FTideMoment> direkt = FHarbourTide.events(bue, start, start + 5L * 86400000L);
        checkTrue("Pegelmodell meldet dieselben Ereignisse",
                ausModell.size() == direkt.size(),
                ausModell.size() + " gegen " + direkt.size());
        boolean gleich = ausModell.size() == direkt.size();
        for (int i = 0; gleich && i < ausModell.size(); i++) {
            if (ausModell.get(i).getTimeMillis() != direkt.get(i).getTimeMillis()) {
                gleich = false;
            }
            if (Math.abs(ausModell.get(i).getLevelMeters()
                    - direkt.get(i).getLevelMeters()) > 1e-9) {
                gleich = false;
            }
        }
        checkTrue("auch in Zeit und Hoehe", gleich, "");

        // Der Zwischenspeicher muss auch bei einem Sprung weit weg stimmen
        long weit = millis(2027, 3, 1, 12);
        check("Zwischenspeicher stimmt auch nach einem weiten Sprung",
                modell.getLevelAt(weit), FHarbourTide.levelAt(bue, weit), 1e-9, "m");
        check("und danach wieder am Ausgangspunkt",
                modell.getLevelAt(start), FHarbourTide.levelAt(bue, start), 1e-9, "m");

        // ---------------------------------------------------------------
        // Die Hafenzeitmessung: Rundreise durch FMoonHarbourFit
        //
        // Aus unseren eigenen Hochwasserzeiten muss die Hafenzeit
        // herauskommen, die wir hineingesteckt haben. Das prueft die
        // Messung UND die Vorhersage gegeneinander: Beide benutzen den
        // Meridiandurchgang, aber in entgegengesetzter Richtung.
        System.out.println();
        System.out.println("--- Hafenzeit aus Hochwasserzeiten zurueckgerechnet ---");

        java.util.List<Double> rund = new java.util.ArrayList<Double>();
        int ohneDurchgang = 0;
        long fitVon = millis(2026, 3, 1, 0);
        long fitBis = fitVon + 60L * 86400000L;
        for (FTideMoment m : FHarbourTide.events(bue, fitVon, fitBis)) {
            if (!m.isHighWater()) {
                continue;
            }
            double jd = FAstroTime.julianDayFromMillis(m.getTimeMillis());
            double tr = FMoonHarbourFit.letzterDurchgangVor(jd, bue.getLocation());
            if (Double.isNaN(tr)) {
                ohneDurchgang++;
                continue;
            }
            rund.add(Double.valueOf((jd - tr) * 24.0));
        }
        // FRiseSet.NONE ist NaN, und NaN ist mit == nie gleich sich selbst.
        // Genau daran ist die Messung beim ersten Lauf gescheitert: Der
        // Vergleich griff nie, das NaN lief bis in den Mittelwert durch.
        // Deshalb wird hier gezaehlt statt verglichen.
        check("Zu jedem Hochwasser gibt es einen Durchgang davor",
                ohneDurchgang, 0.0, 0.0, "ohne");
        check("und die Reihe ist vollstaendig",
                rund.size(), 116.0, 4.0, "Paare");
        check("Gemessene Hafenzeit trifft die eingetragene",
                FMoonHarbourFit.kreisMittel(rund),
                bue.getHighWaterIntervalHours(), 1.0 / 60.0, "h");
        check("und streut dabei kaum",
                FMoonHarbourFit.kreisStreuung(rund,
                        FMoonHarbourFit.kreisMittel(rund)) * 60.0,
                0.0, 1.0, "min");

        // Der Mittelwert muss auf dem KREIS gerechnet werden. Zwei Werte
        // links und rechts des Nulldurchgangs liegen nahe beieinander,
        // obwohl ihre Zahlen weit auseinanderstehen; ein arithmetisches
        // Mittel legte den Wert genau auf die falsche Seite.
        java.util.List<Double> ueberNull = new java.util.ArrayList<Double>();
        ueberNull.add(Double.valueOf(0.1));
        ueberNull.add(Double.valueOf(12.3));
        check("Mittelwert ueber den Nulldurchgang bleibt am Rand",
                FMoonHarbourFit.kreisMittel(ueberNull), 12.4106, 0.02, "h");

        System.out.println();
        System.out.println("=== Der schnelle Weg zum Wasserstand ===");
        // Am 13.09.2026 hat FTideCurve eine Kurvenbreite noch so gezeichnet,
        // dass JEDER Bildpunkt sich seine Ereignisse neu suchte: 601 mal
        // dieselben acht Meridiandurchgaenge, zweieinhalb Sekunden je Bild.
        // Seitdem gibt es levelAt(station, zeit, liste) - denselben Rechenweg,
        // nur mit einmal geholten Ereignissen. Damit sind es zwei Wege zu
        // einer Zahl, und die muessen dieselbe Zahl liefern.
        FTideStation bs = FTideStation.BUESUM;
        long jetzt = FAstroTime.millisFromJulianDay(2461295.0);
        long randMs = (long) (FHarbourTide.TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        long spanne = 30L * 3600000L;
        List<FTideMoment> liste = FHarbourTide.events(bs, jetzt - randMs, jetzt + spanne + randMs);
        double abstandBeiderWege = 0.0;
        for (int i = 0; i <= 200; i++) {
            long t = jetzt + spanne * i / 200;
            abstandBeiderWege = Math.max(abstandBeiderWege,
                    Math.abs(FHarbourTide.levelAt(bs, t, liste) - FHarbourTide.levelAt(bs, t)));
        }
        check("beide Wege zum Wasserstand stimmen ueberein",
                abstandBeiderWege, 0.0, 1e-9, "m");

        // Und die Kurve darf nicht wieder je Bildpunkt rechnen. Absolute
        // Millisekunden waeren von der Maschine abhaengig und damit eine
        // wacklige Pruefung; das Verhaeltnis ist es nicht: Eine ganze
        // Kurvenbreite aus der fertigen Liste muss billiger sein als das
        // EINMALIGE Holen dieser Liste. Faellt jemand auf den alten Weg
        // zurueck, steht hier das Sechshundertfache.
        long t0 = System.nanoTime();
        List<FTideMoment> einmal = FHarbourTide.events(bs, jetzt - randMs, jetzt + spanne + randMs);
        long kostenHolen = System.nanoTime() - t0;
        double probe = 0.0;
        t0 = System.nanoTime();
        for (int i = 0; i <= 600; i++) {
            probe += FHarbourTide.levelAt(bs, jetzt + spanne * i / 600, einmal);
        }
        long kostenKurve = System.nanoTime() - t0;
        check("601 Wasserstaende kosten weniger als ein Ereignisabruf",
                kostenKurve / (double) kostenHolen, 0.0, 1.0, "mal");
        check("und die Summe ist eine Zahl", Double.isNaN(probe) ? 1 : 0, 0, 0, "");

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }

    static boolean alleNaeherung() {
        for (FTideStation s : FTideStation.DEFAULTS) {
            if (!s.isApproximate()) {
                return false;
            }
        }
        return true;
    }
}
