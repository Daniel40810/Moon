import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FRiseSet;
import com.dan.fmoon.db.FMoonDao;
import com.dan.fmoon.db.FMoonDataSource;
import com.dan.fmoon.db.FStationProvider;
import com.dan.fmoon.tide.FTideStation;
import com.dan.ftidalgauge.DefaultFTidalGaugeModel;
import com.dan.ftidalgauge.FTideEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Misst die Hafenzeit einer Station aus beobachteten Hochwasserzeiten.
 *
 * <p>Die Hafenzeit ist der Abstand zwischen dem oertlichen Meridiandurchgang
 * des Mondes und dem darauf folgenden Hochwasser. Wer eine Reihe
 * beobachteter oder veroeffentlichter Hochwasserzeiten hat, kann sie damit
 * ausrechnen lassen, statt sie zu schaetzen: Dieses Werkzeug rechnet zu
 * jedem Hochwasser den Durchgang mit demselben Kern, der auch die Vorhersage
 * macht, und mittelt die Abstaende.</p>
 *
 * <h3>Warum es kurze Reihen zurueckweist</h3>
 *
 * <p>Die Hafenzeit ist keine Konstante. Sie schwankt ueber den
 * Spring-Nipp-Zyklus um eine halbe Stunde und mehr - die Seeleute nennen das
 * Vor- und Nachlaufen der Tide. Wer ueber zehn Tage mittelt, misst nicht die
 * Hafenzeit, sondern den Ausschnitt des Zyklus, der zufaellig in den zehn
 * Tagen lag. Genau dieser Fehler ist in diesem Projekt schon dreimal
 * aufgetreten (anomalistischer Monat, synodischer Monat, Springverzoegerung),
 * und deshalb verweigert dieses Werkzeug die Arbeit, wenn die Reihe kuerzer
 * ist als ein synodischer Monat. Mit {@code -kurz} laesst sich das
 * uebergehen; dann wird der Vorbehalt aber in die Quellenangabe geschrieben
 * und wandert mit in die Datenbank.</p>
 *
 * <h3>Eingabeformat</h3>
 *
 * <pre>
 * # Quelle: BSH Gezeitenkalender 2026
 * # Zeitzone: Europe/Berlin
 * BUESUM 2026-09-13 03:00
 * BUESUM 2026-09-13 15:15
 * HELGOLAND 2026-09-13 01:52
 * </pre>
 *
 * <p>Leerzeilen und Zeilen mit {@code #} am Anfang sind Kommentar; die beiden
 * Angaben Quelle und Zeitzone werden daraus gelesen. Fehlt die Zeitzone, gilt
 * Europe/Berlin - mit Sommerzeit, so wie eine deutsche Gezeitentafel sie
 * angibt.</p>
 *
 * <h3>Aufruf</h3>
 *
 * <pre>
 * java FMoonHarbourFit beobachtungen.txt            nur rechnen und anzeigen
 * java FMoonHarbourFit beobachtungen.txt -schreiben in die Datenbank eintragen
 * java FMoonHarbourFit beobachtungen.txt -kurz      kurze Reihe zulassen
 * java FMoonHarbourFit -harmonik                    aus FTidalGauge messen
 * </pre>
 *
 * <h3>Die Betriebsart -harmonik</h3>
 *
 * <p>Statt abgeschriebener Zeiten nimmt sie die harmonischen Konstanten
 * aus {@code com.dan.ftidalgauge} - die fertigen Modelle fuer Cuxhaven
 * und Wilhelmshaven -, laesst sie ein volles Jahr Hochwasser erzeugen und
 * misst daraus die Hafenzeit. Das ist die beste Quelle, die ohne fremde
 * Hilfe zu haben ist: eine physikalische Reihe statt einer Schaetzung.
 * Was sie NICHT ist, ist eine amtliche Gezeitentafel - sie ist nur so gut
 * wie die Konstanten, die in der Bibliothek stehen, und deren Herkunft
 * steht nicht dabei.</p>
 */
public class FMoonHarbourFit {

    /** Halber mittlerer Mondtag in Stunden - die Periode der Hafenzeit. */
    static final double PERIOD_H = 12.4206;

    /** Ein synodischer Monat in Tagen. */
    static final double SYNODIC_D = 29.530589;

    static String quelle = "unbenannte Beobachtungsreihe";
    static TimeZone zone = TimeZone.getTimeZone("Europe/Berlin");

    /** Eine Station mit ihren beobachteten Hochwasserzeiten. */
    static final class Reihe {
        final String code;
        final List<Long> hochwasser = new ArrayList<Long>();
        Reihe(String code) {
            this.code = code;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Aufruf: java FMoonHarbourFit <datei> "
                    + "[-schreiben] [-kurz]");
            System.exit(2);
            return;
        }
        boolean schreiben = false;
        boolean kurzErlaubt = false;
        for (int i = 1; i < args.length; i++) {
            if ("-schreiben".equals(args[i])) {
                schreiben = true;
            } else if ("-kurz".equals(args[i])) {
                kurzErlaubt = true;
            } else {
                System.out.println("Unbekannte Angabe: " + args[i]);
                System.exit(2);
                return;
            }
        }

        if ("-harmonik".equals(args[0])) {
            ausHarmonik(schreiben);
            return;
        }

        Map<String, Reihe> reihen = lies(new File(args[0]));
        if (reihen.isEmpty()) {
            System.out.println("Keine Hochwasserzeiten in " + args[0] + ".");
            System.exit(1);
            return;
        }

        System.out.println("FMoon Hafenzeitmessung");
        System.out.println("=====================================");
        System.out.println("Datei:    " + args[0]);
        System.out.println("Quelle:   " + quelle);
        System.out.println("Zeitzone: " + zone.getID());
        System.out.println();

        // Die Stationen: aus der Datenbank, wenn sie erreichbar ist, sonst
        // aus den Konstanten. Gebraucht wird nur der Ort - Laenge, Breite
        // und Hoehe gehen in den Meridiandurchgang ein.
        FMoonDataSource src = null;
        try {
            src = new FMoonDataSource();
        } catch (Throwable t) {
            src = null;
        }
        FStationProvider stationen = FStationProvider.load(src);
        System.out.println("Stationen: " + stationen.getNote());
        System.out.println();

        int fehler = 0;
        for (Reihe r : reihen.values()) {
            FTideStation st = finde(stationen, r.code);
            if (st == null) {
                System.out.println(r.code + ": keine solche Station.");
                fehler++;
                continue;
            }
            fehler += auswerten(st, r, kurzErlaubt, schreiben, src, stationen);
            System.out.println();
        }

        if (src != null) {
            src.close();
        }
        System.exit(fehler == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------

    /**
     * Misst die Hafenzeit aus den harmonischen Modellen in FStyle.jar.
     *
     * <p>Die Modelle liefern Hochwasserzeiten aus vierzehn Partialtiden.
     * Ein volles Jahr davon, gegen den Meridiandurchgang gemessen, ergibt
     * die Hafenzeit mitsamt ihrer Streuung - und die Streuung ist hier
     * die eigentliche Auskunft: Sie sagt, wie gut eine einzige Zahl
     * diesen Ort ueberhaupt beschreiben kann.</p>
     */
    static void ausHarmonik(boolean schreiben) throws Exception {
        System.out.println("FMoon Hafenzeit aus harmonischen Konstanten");
        System.out.println("=====================================");
        System.out.println("Quelle:   com.dan.ftidalgauge (FStyle.jar),");
        System.out.println("          KEINE amtliche Gezeitentafel");
        System.out.println();

        Object[][] paare = {
            {DefaultFTidalGaugeModel.cuxhaven(), FGeoLocation.CUXHAVEN, "CUXHAVEN"},
            {DefaultFTidalGaugeModel.wilhelmshaven(), FGeoLocation.CUXHAVEN, null}
        };
        for (int i = 0; i < paare.length; i++) {
            DefaultFTidalGaugeModel m = (DefaultFTidalGaugeModel) paare[i][0];
            FGeoLocation loc = (FGeoLocation) paare[i][1];
            String code = (String) paare[i][2];

            long von = FAstroTime.millisFromJulianDay(
                    FAstroTime.julianDay(2026, 1, 1.0));
            long bis = FAstroTime.millisFromJulianDay(
                    FAstroTime.julianDay(2027, 1, 1.0));

            // findEvents deckelt das Fenster - in Scheiben holen.
            List<Long> hochwasser = new ArrayList<Long>();
            long letzte = Long.MIN_VALUE;
            for (long t = von; t < bis; t += 25L * 86400000L) {
                long e2 = Math.min(bis, t + 25L * 86400000L);
                for (FTideEvent e : m.findEvents(t, e2)) {
                    if (e.isHighWater() && e.getTimeMillis() > letzte) {
                        hochwasser.add(Long.valueOf(e.getTimeMillis()));
                        letzte = e.getTimeMillis();
                    }
                }
            }

            List<Double> abstaende = new ArrayList<Double>();
            for (int k = 0; k < hochwasser.size(); k++) {
                double jd = FAstroTime.julianDayFromMillis(
                        hochwasser.get(k).longValue());
                double tr = letzterDurchgangVor(jd, loc);
                if (!Double.isNaN(tr)) {
                    abstaende.add(Double.valueOf((jd - tr) * 24.0));
                }
            }
            if (abstaende.size() < 100) {
                System.out.println(m.getStationName() + ": zu wenig Ereignisse.");
                continue;
            }
            double mittel = kreisMittel(abstaende);
            double sd = kreisStreuung(abstaende, mittel);

            System.out.println("--- " + m.getStationName() + " ---");
            System.out.printf("  Hochwasser im Jahr:      %d%n", abstaende.size());
            System.out.printf("  Gemessene Hafenzeit:     %.4f h  (%s)%n",
                    mittel, hm(mittel));
            System.out.printf("  Streuung:                %.1f min%n", sd * 60.0);
            if (code == null) {
                System.out.println("  (keine FMoon-Station - nur zum Vergleich)");
            } else {
                System.out.println();
                System.out.println("  Zum Eintragen von Hand:");
                System.out.println("    BEGIN fmn_api.set_harbour_time(");
                System.out.println("      '" + code + "', "
                        + String.format(Locale.US, "%.4f", mittel) + ", NULL,");
                System.out.println("      'Gemessen aus den harmonischen "
                        + "Konstanten in com.dan.ftidalgauge, ein Jahr; "
                        + "keine amtliche Gezeitentafel',");
                System.out.println("      SYSDATE, 'J', "
                        + String.format(Locale.US, "%.1f", sd * 60.0) + "); END;");
            }
            System.out.println();
        }
        System.out.println("Hinweis: Das Kennzeichen bleibt auf 'J'. Der Wert");
        System.out.println("stammt aus einem Modell, nicht aus einer Tafel -");
        System.out.println("besser als eine Schaetzung, aber keine Urkunde.");
    }

    static int auswerten(FTideStation st, Reihe r, boolean kurzErlaubt,
            boolean schreiben, FMoonDataSource src, FStationProvider prov)
            throws Exception {

        System.out.println("--- " + st.getName() + " (" + r.code + ") ---");
        System.out.println("  Hochwasser in der Reihe: " + r.hochwasser.size());

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Long t : r.hochwasser) {
            min = Math.min(min, t.longValue());
            max = Math.max(max, t.longValue());
        }
        double spanneTage = (max - min) / 86400000.0;
        System.out.printf("  Spanne:                  %.2f Tage%n", spanneTage);

        if (r.hochwasser.size() < 4) {
            System.out.println("  ZU WENIG: unter vier Hochwassern ist ein "
                    + "Mittelwert Zierat.");
            return 1;
        }

        if (spanneTage < SYNODIC_D && !kurzErlaubt) {
            System.out.println("  ABGELEHNT: Die Reihe ist kuerzer als ein");
            System.out.println("  synodischer Monat (29,53 Tage). Die Hafenzeit");
            System.out.println("  schwankt ueber den Spring-Nipp-Zyklus; ein");
            System.out.println("  kuerzeres Fenster misst das Fenster und nicht");
            System.out.println("  den Ort. Mit -kurz laesst sich das uebergehen -");
            System.out.println("  der Vorbehalt steht dann in der Quellenangabe.");
            return 1;
        }

        // Zu jedem Hochwasser der letzte Meridiandurchgang davor.
        List<Double> abstaende = new ArrayList<Double>();
        int ohneDurchgang = 0;
        for (Long t : r.hochwasser) {
            double jd = FAstroTime.julianDayFromMillis(t.longValue());
            double transit = letzterDurchgangVor(jd, st.getLocation());
            if (Double.isNaN(transit)) {
                ohneDurchgang++;
                continue;
            }
            abstaende.add(Double.valueOf((jd - transit) * 24.0));
        }
        if (ohneDurchgang > 0) {
            System.out.println("  Ohne Durchgang gefunden: " + ohneDurchgang);
        }
        if (abstaende.size() < 4) {
            System.out.println("  ZU WENIG auswertbare Paare.");
            return 1;
        }

        double mittel = kreisMittel(abstaende);
        double sd = kreisStreuung(abstaende, mittel);
        double groessteAbw = 0.0;
        for (Double a : abstaende) {
            groessteAbw = Math.max(groessteAbw,
                    Math.abs(differenz(a.doubleValue(), mittel)));
        }

        System.out.printf("  Gemessene Hafenzeit:     %.4f h  (%s)%n",
                mittel, hm(mittel));
        System.out.printf("  Streuung:                %.1f min%n", sd * 60.0);
        System.out.printf("  Groesste Abweichung:     %.1f min%n",
                groessteAbw * 60.0);
        System.out.printf("  Bisher eingetragen:      %.4f h  (%s)%n",
                st.getHighWaterIntervalHours(),
                st.isApproximate() ? "Naeherung" : "gesetzt");
        System.out.printf("  Unterschied:             %.1f min%n",
                differenz(mittel, st.getHighWaterIntervalHours()) * 60.0);

        String quellenText = quelle;
        if (spanneTage < SYNODIC_D) {
            quellenText = quellenText + " (Reihe nur "
                    + String.format(Locale.GERMANY, "%.0f", spanneTage)
                    + " Tage, kuerzer als ein synodischer Monat)";
        }

        System.out.println();
        System.out.println("  Zum Eintragen von Hand:");
        System.out.println("    BEGIN fmn_api.set_harbour_time(");
        System.out.println("      '" + r.code + "', "
                + String.format(Locale.US, "%.4f", mittel) + ", NULL,");
        System.out.println("      '" + quellenText.replace("'", "''") + "',");
        System.out.println("      SYSDATE, 'N', "
                + String.format(Locale.US, "%.1f", sd * 60.0) + "); END;");

        if (schreiben) {
            if (src == null) {
                System.out.println("  NICHT GESCHRIEBEN: keine Verbindung.");
                return 1;
            }
            try {
                new FMoonDao(src).setHarbourTime(r.code, mittel, -1.0,
                        quellenText, false, sd * 60.0);
                System.out.println("  In die Datenbank geschrieben.");
            } catch (Exception e) {
                System.out.println("  NICHT GESCHRIEBEN: " + ersteZeile(e));
                return 1;
            }
        }
        return 0;
    }

    /**
     * Der letzte Meridiandurchgang - obere oder untere Kulmination - vor dem
     * angegebenen Zeitpunkt.
     *
     * <p>Beide Kulminationen zaehlen: Der Mond geht zweimal am Tag durch den
     * Meridian, und zu jedem der beiden Durchgaenge gehoert ein Hochwasser.
     * Nur die obere zu nehmen wuerde jedes zweite Hochwasser um 12,42 Stunden
     * danebenlegen.</p>
     */
    static double letzterDurchgangVor(double jd, FGeoLocation loc) {
        // FRiseSet.NONE ist Double.NaN, und NaN ist mit == nie gleich sich
        // selbst. Ein Vergleich "tr == FRiseSet.NONE" ist deshalb immer
        // falsch, und das NaN wandert unbemerkt bis in den Mittelwert. Beim
        // ersten Lauf dieses Werkzeugs stand genau deshalb NaN in jeder
        // Zeile. Abgefragt wird mit Double.isNaN.
        double best = Double.NaN;
        for (int k = 0; k < 2; k++) {
            boolean oben = (k == 0);
            double von = jd - 1.0;
            for (int i = 0; i < 4; i++) {
                double tr = FRiseSet.meridianTransit(von, loc, oben);
                if (Double.isNaN(tr) || tr >= jd) {
                    break;
                }
                if (Double.isNaN(best) || tr > best) {
                    best = tr;
                }
                von = tr + 0.1;
            }
        }
        return best;
    }

    /**
     * Mittelwert auf dem Kreis.
     *
     * <p>Die Hafenzeit ist eine Phase mit der Periode eines halben Mondtages,
     * kein gewoehnlicher Messwert. Ein arithmetisches Mittel zerbricht, sobald
     * Werte beiderseits des Nulldurchgangs liegen: Aus 0,1 h und 12,3 h wuerde
     * 6,2 h - ein Wert, der genau in der Mitte zwischen den beiden Beobachtungen
     * liegt und mit keiner von beiden etwas zu tun hat. Gemittelt wird deshalb
     * ueber die Einheitsvektoren.</p>
     *
     * @param werte Abstaende in Stunden
     * @return Mittelwert in Stunden, im Bereich 0 bis {@link #PERIOD_H}
     */
    static double kreisMittel(List<Double> werte) {
        double sx = 0.0;
        double sy = 0.0;
        for (Double a : werte) {
            double w = 2.0 * Math.PI * a.doubleValue() / PERIOD_H;
            sx += Math.cos(w);
            sy += Math.sin(w);
        }
        double mittel = Math.atan2(sy / werte.size(), sx / werte.size())
                * PERIOD_H / (2.0 * Math.PI);
        return (mittel < 0.0) ? mittel + PERIOD_H : mittel;
    }

    /**
     * Streuung um einen Mittelwert, ebenfalls auf dem Kreis.
     *
     * @param werte  Abstaende in Stunden
     * @param mittel Mittelwert aus {@link #kreisMittel}
     * @return Standardabweichung in Stunden
     */
    static double kreisStreuung(List<Double> werte, double mittel) {
        double summe = 0.0;
        for (Double a : werte) {
            double d = differenz(a.doubleValue(), mittel);
            summe += d * d;
        }
        return Math.sqrt(summe / (werte.size() - 1));
    }

    /** Abstand zweier Phasen auf dem Kreis, in Stunden, mit Vorzeichen. */
    static double differenz(double a, double b) {
        double d = a - b;
        while (d > PERIOD_H / 2.0) {
            d -= PERIOD_H;
        }
        while (d < -PERIOD_H / 2.0) {
            d += PERIOD_H;
        }
        return d;
    }

    static String hm(double stunden) {
        int m = (int) Math.round(stunden * 60.0);
        return (m / 60) + " h " + (m % 60) + " min";
    }

    static FTideStation finde(FStationProvider p, String code) {
        List<FTideStation> alle = p.getStations();
        for (int i = 0; i < alle.size(); i++) {
            if (code.equals(p.codeOf(alle.get(i)))) {
                return alle.get(i);
            }
        }
        // Ohne Datenbank gibt es keine Codes. Dann ueber den Namen,
        // umlautunempfindlich.
        for (int i = 0; i < alle.size(); i++) {
            String n = entumlaute(alle.get(i).getName()).toUpperCase();
            if (n.startsWith(entumlaute(code).toUpperCase())
                    || entumlaute(code).toUpperCase().startsWith(n)) {
                return alle.get(i);
            }
        }
        return null;
    }

    static String entumlaute(String s) {
        return s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
                .replace("ß", "ss").replace("/", "_").replace(" ", "_");
    }

    // ------------------------------------------------------------------

    static Map<String, Reihe> lies(File f) throws Exception {
        Map<String, Reihe> out = new LinkedHashMap<String, Reihe>();
        if (!f.exists()) {
            System.out.println("Datei nicht gefunden: " + f.getAbsolutePath());
            System.exit(2);
            return out;
        }
        String inhalt = new String(Files.readAllBytes(f.toPath()),
                Charset.forName("UTF-8"));
        String[] zeilen = inhalt.split("\r?\n", -1);

        // Zeitzone zuerst: Sie muss stehen, bevor die erste Zeit gelesen wird.
        for (int i = 0; i < zeilen.length; i++) {
            String z = zeilen[i].trim();
            if (z.startsWith("#")) {
                String rest = z.substring(1).trim();
                if (rest.toLowerCase().startsWith("zeitzone:")) {
                    zone = TimeZone.getTimeZone(rest.substring(9).trim());
                } else if (rest.toLowerCase().startsWith("quelle:")) {
                    quelle = rest.substring(7).trim();
                }
            }
        }

        SimpleDateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY);
        fmt.setTimeZone(zone);
        fmt.setLenient(false);

        for (int i = 0; i < zeilen.length; i++) {
            String z = zeilen[i].trim();
            if (z.isEmpty() || z.startsWith("#")) {
                continue;
            }
            int leer = z.indexOf(' ');
            if (leer < 0) {
                System.out.println("Zeile " + (i + 1) + " unverstaendlich: " + z);
                continue;
            }
            String code = z.substring(0, leer).trim().toUpperCase();
            String zeit = z.substring(leer + 1).trim();
            Date d;
            try {
                d = fmt.parse(zeit);
            } catch (Exception e) {
                System.out.println("Zeile " + (i + 1) + ": '" + zeit
                        + "' ist keine Zeit im Format yyyy-MM-dd HH:mm.");
                continue;
            }
            Reihe r = out.get(code);
            if (r == null) {
                r = new Reihe(code);
                out.put(code, r);
            }
            r.hochwasser.add(Long.valueOf(d.getTime()));
        }
        return out;
    }

    static String ersteZeile(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.toString();
        }
        int nl = m.indexOf('\n');
        return (nl > 0) ? m.substring(0, nl) : m;
    }
}
