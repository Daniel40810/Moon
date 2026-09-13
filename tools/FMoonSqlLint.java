import com.dan.fmoon.tide.FTideStation;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueft die SQL-Skripte, ohne eine Datenbank zu brauchen.
 *
 * <p>Der Grund: Die Oracle-Datenbank steht auf Daniels Rechner und ist
 * von aussen nicht erreichbar. Ein Tippfehler im SQL faellt deshalb erst
 * beim Einspielen auf - eine Runde spaeter. Was sich vorher pruefen
 * laesst, wird hier geprueft.</p>
 *
 * <p>Die wichtigste Pruefung ist die letzte: Die Stammdaten in
 * <code>04_stations.sql</code> muessen mit den Konstanten in
 * <code>FTideStation</code> uebereinstimmen. Zwei Stellen mit denselben
 * Zahlen sind sonst zwei Wahrheiten, sobald jemand eine davon anfasst.</p>
 */
public class FMoonSqlLint {

    static int pass = 0;
    static int fail = 0;

    static void check(String was, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-58s %s%n", ok ? "  OK  " : " FEHL ", was, detail);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File("db");
        if (!dir.isDirectory()) {
            System.out.println("Kein Ordner db gefunden.");
            System.exit(1);
        }
        File[] dateien = dir.listFiles();
        java.util.Arrays.sort(dateien);

        System.out.println("=== Aufbau der Skripte ===");
        for (File f : dateien) {
            if (!f.getName().endsWith(".sql")) {
                continue;
            }
            pruefeDatei(f);
        }

        System.out.println();
        System.out.println("=== Stammdaten gegen die Java-Konstanten ===");
        pruefeStationen(new File(dir, "04_stations.sql"));

        System.out.println();
        System.out.println("=== Reihenfolge und Vollstaendigkeit ===");
        String[] noetig = {"00_drop.sql", "01_tables.sql", "02_types.sql",
            "03_spatial.sql", "04_stations.sql", "05_names.sql", "06_api.sql",
            "07_api_body.sql", "08_views.sql", "09_scheduler.sql",
            "10_import.sql", "11_grant_ogp.sql", "12_coast.sql",
            "99_showcase.sql"};
        for (String n : noetig) {
            check("Skript " + n + " vorhanden", new File(dir, n).exists(), "");
        }

        // Spec und Body gehoeren zusammen - steht das auch in beiden drin?
        String spec = lies(new File(dir, "06_api.sql"));
        String body = lies(new File(dir, "07_api_body.sql"));
        check("Die Spec warnt davor, sie allein einzuspielen",
                spec.contains("verwirft den") || spec.contains("06 und 07"), "");
        check("Der Rumpf nennt die Spec",
                body.contains("06_api.sql"), "");

        // Jede in der Spec angekuendigte Routine muss im Rumpf stehen
        List<String> inSpec = routinen(spec);
        List<String> fehlend = new ArrayList<String>();
        for (String r : inSpec) {
            if (!body.toUpperCase().contains(r.toUpperCase())) {
                fehlend.add(r);
            }
        }
        check("Jede Routine der Spec steht auch im Rumpf",
                fehlend.isEmpty(),
                fehlend.isEmpty() ? inSpec.size() + " Routinen"
                                  : "fehlt: " + fehlend);

        // Die Toleranz der Phasenpruefung darf nur an einer Stelle stehen.
        // Sie stand einmal als 15 in der Spec, im Rumpf und in der
        // naechtlichen Prozedur; geaendert wurden davon zwei - der Job
        // schlug dann fehl, obwohl der Durchstich gruen war.
        check("Die Phasentoleranz ist als Konstante erklaert",
                spec.contains("c_phase_tol_h"), "");
        List<String> hartkodiert = new ArrayList<String>();
        File[] alle = new File("db").listFiles();
        if (alle != null) {
            Arrays.sort(alle);
            Pattern ruf = Pattern.compile(
                    "verify_events\\s*\\(\\s*[0-9]", Pattern.CASE_INSENSITIVE);
            for (File d : alle) {
                if (!d.getName().endsWith(".sql")) {
                    continue;
                }
                if (ruf.matcher(entferneKommentare(lies(d))).find()) {
                    hartkodiert.add(d.getName());
                }
            }
        }
        // Jeder Typ, den die Spezifikation nennt, muss VORHER angelegt
        // sein. Genau das ist schiefgegangen: T_FMN_COAST_TAB entstand
        // erst in 12_coast.sql, FMN_API nennt ihn aber schon in 06 -
        // PLS-00201 in der Spec, PLS-00905 im Rumpf, und neun weitere
        // Fehler als Folge. Die Reihenfolge der Skripte ist eine
        // Zusicherung und gehoert geprueft.
        java.util.Map<String, Integer> angelegt =
                new java.util.TreeMap<String, Integer>();
        File[] sql = new File("db").listFiles();
        if (sql != null) {
            Arrays.sort(sql);
            Pattern anlegen = Pattern.compile(
                    "CREATE\\s+OR\\s+REPLACE\\s+TYPE\\s+(t_fmn_[a-z_]+)",
                    Pattern.CASE_INSENSITIVE);
            for (File d : sql) {
                if (!d.getName().endsWith(".sql")) {
                    continue;
                }
                int nr = nummer(d.getName());
                Matcher mm = anlegen.matcher(lies(d));
                while (mm.find()) {
                    String t = mm.group(1).toUpperCase();
                    // TYPE BODY zaehlt nicht als Anlegen des Typs
                    if (angelegt.get(t) == null) {
                        angelegt.put(t, Integer.valueOf(nr));
                    }
                }
            }
        }
        List<String> zuSpaet = new ArrayList<String>();
        Matcher benutzt = Pattern.compile("(t_fmn_[a-z_]+)",
                Pattern.CASE_INSENSITIVE).matcher(entferneKommentare(spec));
        while (benutzt.find()) {
            String t = benutzt.group(1).toUpperCase();
            Integer wo = angelegt.get(t);
            if (wo == null) {
                if (!zuSpaet.contains(t + " (nirgends angelegt)")) {
                    zuSpaet.add(t + " (nirgends angelegt)");
                }
            } else if (wo.intValue() >= 6) {
                String meldung = t + " erst in Skript " + wo;
                if (!zuSpaet.contains(meldung)) {
                    zuSpaet.add(meldung);
                }
            }
        }
        check("Jeder Typ der Spec ist vor 06 angelegt",
                zuSpaet.isEmpty(),
                zuSpaet.isEmpty() ? angelegt.size() + " Typen" : zuSpaet.toString());

        // Das Grant-Skript laeuft als OGP und darf deshalb NICHT in der
        // Liste des Einspielprogramms stehen. Ein Skript, das sich in
        // einem Stapellauf still an einem fremden Schema zu schaffen
        // macht, waere genau die Art Ueberraschung, die niemand will.
        String setup = lies(new File("tools/FMoonDbSetup.java"));
        check("Das Grant-Skript laeuft nicht im Stapel mit",
                !setup.contains("\"11_grant_ogp.sql\""), "");
        check("Das Kuestenskript laeuft im Stapel mit",
                setup.contains("\"12_coast.sql\""), "");
        String grant = lies(new File("db/11_grant_ogp.sql"));
        check("Das Grant-Skript sagt oben, als wer es laufen muss",
                grant.contains("als OGP") || grant.contains("NICHT als DEMO"), "");
        check("Es gibt nur ein Leserecht, keine Aenderung an OGP",
                !grant.toUpperCase().contains("CREATE ")
                && !grant.toUpperCase().contains("DROP ")
                && !grant.toUpperCase().contains("INSERT ")
                && !grant.toUpperCase().contains("UPDATE "), "");
        // Die Job-Schalter tragen mit Absicht keine Nummer und duerfen
        // nicht im Stapellauf mitlaufen. Ein Aufbaulauf, der nebenbei
        // etwas einschaltet, das danach jede Nacht von selbst arbeitet,
        // waere dieselbe Art Ueberraschung wie ein Grant an ein fremdes
        // Schema.
        String[] schalter = {"job_status.sql", "job_an.sql", "job_aus.sql",
            "job_jetzt.sql"};
        for (String n : schalter) {
            check("Schalter " + n + " vorhanden", new File(dir, n).exists(), "");
            check("Schalter " + n + " laeuft nicht im Stapel mit",
                    !setup.contains("\"" + n + "\""), "");
        }
        // Und der Aufbau selbst darf ihn nicht anschalten - sonst waere
        // der Schalter Zierde.
        String sched = lies(new File(dir, "09_scheduler.sql"));
        check("Der Aufbau legt den Job ausgeschaltet an",
                sched.contains("enabled         => FALSE")
                && !sched.matches("(?s).*DBMS_SCHEDULER\\.enable.*"), "");
        String an = lies(new File(dir, "job_an.sql"));
        check("Das Einschalten glaubt der Meldung nicht, sondern sieht nach",
                an.contains("next_run_date") && an.contains("RAISE_APPLICATION_ERROR"), "");
        check("Das Einschalten sagt, dass ein Neuaufbau es zuruecknimmt",
                an.contains("db_setup.bat"), "");
        String aus = lies(new File(dir, "job_aus.sql"));
        check("Das Ausschalten prueft, ob es gegriffen hat",
                aus.contains("RAISE_APPLICATION_ERROR"), "");
        String bat = lies(new File("job.bat"));
        check("job.bat kennt alle vier Griffe",
                bat.contains("job_an.sql") && bat.contains("job_aus.sql")
                && bat.contains("job_jetzt.sql") && bat.contains("job_status.sql"), "");

        String kueste = lies(new File("db/12_coast.sql"));
        check("Die Kueste wird geliehen, nicht kopiert",
                !kueste.toUpperCase().contains("CREATE TABLE")
                && kueste.contains("fmn_coast_v"), "");
        check("Fehlt das Leserecht, faellt der Aufbau nicht um",
                kueste.contains("EXCEPTION WHEN OTHERS"), "");

        // Die Herkunft ist Teil der Stammdaten, nicht Beiwerk
        String tabellen = lies(new File("db/01_tables.sql"));
        check("FMN_STATION fuehrt die Herkunft mit",
                tabellen.contains("source")
                && tabellen.contains("source_date")
                && tabellen.contains("hw_interval_sd_min"), "");
        check("Ohne Naeherungskennzeichen ist eine Quelle Pflicht",
                tabellen.contains("fmn_station_source_ck"), "");
        check("Es gibt einen geprueften Weg, echte Werte einzutragen",
                spec.toLowerCase().contains("set_harbour_time")
                && body.toLowerCase().contains("set_harbour_time"), "");
        // Jede Station muss eine Herkunft nennen - aber nicht alle
        // dieselbe: Die fuenf Nordseestationen sind auf die Messung an
        // Cuxhaven ausgerichtet, die drei Ostseestationen nicht, weil
        // dort keine Vorhersage sinnvoll ist. Gezaehlt wird deshalb,
        // dass acht INSERTs acht Quellenangaben haben.
        String stationen = lies(new File("db/04_stations.sql"));
        int mitQuelle = zaehleVorkommen(stationen, "', DATE '");
        check("Jede Station nennt ihre Herkunft", mitQuelle == 8,
                mitQuelle + " von 8");
        // Ohne Kommentare gezaehlt: Im Kopf des Skripts steht die
        // Erklaerung dazu, und die soll nicht mitzaehlen.
        int mitMessung = zaehleVorkommen(entferneKommentare(stationen),
                "com.dan.ftidalgauge");
        check("Die Nordsee nennt die Messung als Bezug", mitMessung == 5,
                mitMessung + " von 5");

        check("Kein Skript schreibt die Toleranz noch einmal hin",
                hartkodiert.isEmpty(),
                hartkodiert.isEmpty() ? "" : "in " + hartkodiert);

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }

    static String lies(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
    }

    static void pruefeDatei(File f) throws Exception {
        String inhalt = lies(f);
        String name = f.getName();

        // Nur ASCII: Die Skripte gehen ueber JDBC in die Datenbank, und
        // eine falsch geratene Zeichensatzeinstellung macht aus Umlauten
        // Fragezeichen - mitten in Stammdaten, wo es niemand sieht.
        int nichtAscii = 0;
        int ersteZeile = -1;
        String[] zeilen = inhalt.split("\n", -1);
        for (int i = 0; i < zeilen.length; i++) {
            for (int j = 0; j < zeilen[i].length(); j++) {
                if (zeilen[i].charAt(j) > 127) {
                    nichtAscii++;
                    if (ersteZeile < 0) {
                        ersteZeile = i + 1;
                    }
                }
            }
        }
        check(name + ": nur ASCII", nichtAscii == 0,
                nichtAscii == 0 ? "" : nichtAscii + " Zeichen, zuerst Zeile " + ersteZeile);

        // Ein Java-Escape in einem SQL-String waere stehengeblieben
        check(name + ": keine Java-Escapes",
                !inhalt.contains("\\u00"), "");

        // INTERVAL 'nnn' DAY: ohne Angabe hat ein Tagesintervall zwei
        // fuehrende Stellen. Drei Ziffern passen nicht hinein und die
        // Datenbank antwortet mit ORA-01873 - aber erst beim Uebersetzen
        // des Blocks, also leicht zu uebersehen.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "INTERVAL\\s*'(\\d{3,})'\\s*(DAY|HOUR|MINUTE|SECOND|YEAR|MONTH)"
                + "(?!\\s*\\()", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(entferneKommentare(inhalt));
        StringBuilder zuLang = new StringBuilder();
        while (m.find()) {
            if (zuLang.length() > 0) {
                zuLang.append(", ");
            }
            zuLang.append(m.group());
        }
        check(name + ": Intervalle passen in zwei Stellen",
                zuLang.length() == 0, zuLang.toString());

        // Zerlegung wie im Einspielprogramm
        List<String> bloecke = zerlegen(inhalt);
        int echte = 0;
        for (String b : bloecke) {
            if (!nurKommentar(b)) {
                echte++;
            }
        }
        check(name + ": zerfaellt in Bloecke", echte > 0, echte + " Anweisungen");

        // Klammern und Anfuehrungszeichen je Block
        int schief = 0;
        int hochkomma = 0;
        for (String b : bloecke) {
            String ohneKommentar = entferneKommentare(b);
            int auf = 0;
            for (int i = 0; i < ohneKommentar.length(); i++) {
                char ch = ohneKommentar.charAt(i);
                if (ch == '(') {
                    auf++;
                } else if (ch == ')') {
                    auf--;
                }
            }
            if (auf != 0) {
                schief++;
            }
            int hk = 0;
            for (int i = 0; i < ohneKommentar.length(); i++) {
                if (ohneKommentar.charAt(i) == '\'') {
                    hk++;
                }
            }
            if (hk % 2 != 0) {
                hochkomma++;
            }
        }
        check(name + ": Klammern gehen auf", schief == 0,
                schief == 0 ? "" : schief + " Bloecke schief");
        check(name + ": Hochkommata paarweise", hochkomma == 0,
                hochkomma == 0 ? "" : hochkomma + " Bloecke ungerade");

        // Keine Tabulatoren (in SQL*Plus und SQL Developer unterschiedlich breit)
        check(name + ": keine Tabulatoren", inhalt.indexOf('\t') < 0, "");

        // Die Datei muss mit einem Trenner enden, sonst faellt die letzte
        // Anweisung unter den Tisch.
        //
        // Das gilt nur fuer die NUMMERIERTEN Skripte: Die laufen durch
        // FMoonDbSetup, und der zerlegt sie an den Schraegstrich-Zeilen -
        // was nach dem letzten Trenner steht, wird nie ausgefuehrt. Die
        // Job-Schalter laufen unmittelbar in SQL*Plus und duerfen mit
        // SET, PROMPT oder einem @@-Aufruf enden. Die Regel wird hier
        // nicht aufgeweicht, sondern auf die Skripte begrenzt, fuer die
        // ihre Begruendung ueberhaupt gilt.
        String getrimmt = inhalt.trim();
        if (name.length() > 0 && Character.isDigit(name.charAt(0))) {
            check(name + ": endet mit einem Trenner",
                    getrimmt.endsWith("/"), "");
        }
    }

    /** Zerlegt wie FMoonDbSetup: an Zeilen, die nur einen Schraegstrich tragen. */
    static List<String> zerlegen(String inhalt) {
        List<String> out = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        String[] zeilen = inhalt.split("\r?\n", -1);
        for (String z : zeilen) {
            if (z.trim().equals("/")) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(z).append('\n');
            }
        }
        if (sb.toString().trim().length() > 0) {
            out.add(sb.toString());
        }
        return out;
    }

    static boolean nurKommentar(String block) {
        String[] zeilen = block.split("\n");
        for (String z : zeilen) {
            String t = z.trim();
            if (!t.isEmpty() && !t.startsWith("--")) {
                return false;
            }
        }
        return true;
    }

    static String entferneKommentare(String block) {
        StringBuilder sb = new StringBuilder();
        for (String z : block.split("\n")) {
            int p = z.indexOf("--");
            // Nur wenn das Minuspaar nicht in einem Text steht. Grobe
            // Regel: gerade Zahl von Hochkommata davor.
            if (p >= 0) {
                int hk = 0;
                for (int i = 0; i < p; i++) {
                    if (z.charAt(i) == '\'') {
                        hk++;
                    }
                }
                if (hk % 2 == 0) {
                    z = z.substring(0, p);
                }
            }
            sb.append(z).append('\n');
        }
        return sb.toString();
    }

    static List<String> routinen(String spec) {
        List<String> out = new ArrayList<String>();
        Pattern p = Pattern.compile(
                "(?m)^\\s*(FUNCTION|PROCEDURE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher m = p.matcher(spec);
        while (m.find()) {
            String n = m.group(2);
            if (!out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------

    static void pruefeStationen(File f) throws Exception {
        String inhalt = lies(f);

        // Die INSERT-Bloecke auslesen: VALUES ('CODE', 'Name', 'GEWAESSER',
        // lat, lon, hoehe, hwi, lwo, spring, neap, 'J', ...)
        // Der Name steht entweder unmittelbar als Zeichenkette oder, wenn
        // er einen Umlaut traegt, in UNISTR mit dem Codepunkt.
        Pattern p = Pattern.compile(
            "VALUES\\s*\\(\\s*'([A-Z_]+)'\\s*,\\s*((?:UNISTR\\()?'[^']*'\\)?)\\s*,\\s*'([A-Z_]+)'\\s*,"
            + "\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,"
            + "\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,"
            + "\\s*([-0-9.]+)\\s*,\\s*'([JN])'");
        Matcher m = p.matcher(inhalt);

        int gefunden = 0;
        int gleich = 0;
        List<String> abweichungen = new ArrayList<String>();

        while (m.find()) {
            gefunden++;
            String name = entpackeName(m.group(2));
            String gew = m.group(3);
            double lat = Double.parseDouble(m.group(4));
            double lon = Double.parseDouble(m.group(5));
            double hwi = Double.parseDouble(m.group(7));
            double lwo = Double.parseDouble(m.group(8));
            double spring = Double.parseDouble(m.group(9));
            double neap = Double.parseDouble(m.group(10));
            boolean approx = "J".equals(m.group(11));

            // Zeichen fuer Zeichen, ohne Nachsicht. Frueher liess diese
            // Stelle auch die umschriebene Schreibweise durch - und
            // genau deshalb war sie gruen, waehrend der Durchstich in
            // der Datenbank zwei Stationen nicht wiederfand, weil ihr
            // Name dort anders lautete als im Java-Code. Eine Pruefung,
            // die die Abweichung verzeiht, die sie finden soll, ist
            // keine.
            FTideStation code = null;
            for (FTideStation s : FTideStation.DEFAULTS) {
                if (s.getName().equals(name)) {
                    code = s;
                }
            }
            if (code == null) {
                abweichungen.add(name + ": im Java-Code nicht gefunden");
                continue;
            }

            StringBuilder diff = new StringBuilder();
            if (Math.abs(code.getHighWaterIntervalHours() - hwi) > 1e-9) {
                diff.append(" Hafenzeit ").append(code.getHighWaterIntervalHours())
                    .append("/").append(hwi);
            }
            if (Math.abs(code.getLowWaterOffsetHours() - lwo) > 1e-9) {
                diff.append(" NW-Versatz ").append(code.getLowWaterOffsetHours())
                    .append("/").append(lwo);
            }
            if (Math.abs(code.getSpringRangeMeters() - spring) > 1e-9) {
                diff.append(" Springhub ").append(code.getSpringRangeMeters())
                    .append("/").append(spring);
            }
            if (Math.abs(code.getNeapRangeMeters() - neap) > 1e-9) {
                diff.append(" Nipphub ").append(code.getNeapRangeMeters())
                    .append("/").append(neap);
            }
            if (Math.abs(code.getLocation().getLatitudeDeg() - lat) > 1e-6) {
                diff.append(" Breite ").append(code.getLocation().getLatitudeDeg())
                    .append("/").append(lat);
            }
            if (Math.abs(code.getLocation().getLongitudeDeg() - lon) > 1e-6) {
                diff.append(" Laenge ").append(code.getLocation().getLongitudeDeg())
                    .append("/").append(lon);
            }
            boolean gewPasst = ("NORTH_SEA".equals(gew)
                    && code.getWaterBody().isTidal())
                    || ("BALTIC_SEA".equals(gew) && !code.getWaterBody().isTidal());
            if (!gewPasst) {
                diff.append(" Gewaesser ").append(code.getWaterBody())
                    .append("/").append(gew);
            }
            if (approx != code.isApproximate()) {
                diff.append(" Naeherungskennzeichen");
            }

            if (diff.length() == 0) {
                gleich++;
            } else {
                abweichungen.add(name + ":" + diff);
            }
        }

        check("Alle acht Stationen im Skript gefunden", gefunden == 8,
                gefunden + " gefunden");
        check("Skript und Java-Konstanten stimmen ueberein",
                gefunden > 0 && gleich == gefunden,
                gleich + " von " + gefunden);
        for (String a : abweichungen) {
            System.out.println("       " + a);
        }

        // Alle als Naeherung gekennzeichnet - in beiden Welten
        int nichtApprox = 0;
        for (FTideStation s : FTideStation.DEFAULTS) {
            if (!s.isApproximate()) {
                nichtApprox++;
            }
        }
        check("Java-Konstanten alle als Naeherung gekennzeichnet",
                nichtApprox == 0, nichtApprox + " ohne Kennzeichen");
        check("Skript kennzeichnet alle als Naeherung",
                !inhalt.contains(", 'N',"), "");
        check("Skript sagt ausdruecklich, dass die Werte Naeherungen sind",
                inhalt.contains("NAEHERUNGSWERTE"), "");
    }

    /**
     * Macht aus dem Namen im Skript den Namen, der in der Spalte landet.
     *
     * <p>Zwei Formen kommen vor: {@code 'Husum'} und
     * {@code UNISTR('B\00FCsum')}. Die zweite haelt das Skript in reinem
     * ASCII und bringt den Umlaut trotzdem in die Datenbank; hier wird
     * genau das nachvollzogen, damit der Vergleich mit
     * FTideStation.DEFAULTS gegen den tatsaechlichen Wert laeuft und
     * nicht gegen die Schreibweise im Skript.</p>
     */
    /** Die fuehrende Zahl eines Skriptnamens, oder 99. */
    static int nummer(String dateiname) {
        int i = 0;
        while (i < dateiname.length()
                && Character.isDigit(dateiname.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return 99;
        }
        return Integer.parseInt(dateiname.substring(0, i));
    }

    static int zaehleVorkommen(String text, String teil) {
        int n = 0;
        int i = text.indexOf(teil);
        while (i >= 0) {
            n++;
            i = text.indexOf(teil, i + 1);
        }
        return n;
    }

    static String entpackeName(String roh) {
        String s = roh.trim();
        boolean unistr = false;
        if (s.toUpperCase().startsWith("UNISTR(")) {
            unistr = true;
            s = s.substring(7);
            if (s.endsWith(")")) {
                s = s.substring(0, s.length() - 1);
            }
            s = s.trim();
        }
        if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        if (!unistr) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 4 < s.length()) {
                if (s.charAt(i + 1) == '\\') {
                    sb.append('\\');      // \\ ist ein echter Schraegstrich
                    i++;
                } else {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
