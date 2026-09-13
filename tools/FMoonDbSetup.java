import com.dan.fmoon.db.FMoonDataSource;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Spielt die Skripte aus dem Ordner db ein und schreibt ein Protokoll.
 *
 * <p>Dieses Programm ist der Rueckkanal: Die Datenbank steht auf Daniels
 * Rechner und ist von aussen nicht erreichbar. Was hier ins Protokoll
 * geschrieben wird, ist alles, was ueber den Lauf zu erfahren ist -
 * entsprechend ausfuehrlich ist es.</p>
 *
 * <h3>Wie die Skripte getrennt werden</h3>
 *
 * <p>An Zeilen, die NUR einen Schraegstrich enthalten. Diese Regel gilt
 * fuer jedes Skript und fuer jede Art von Anweisung, auch fuer einfaches
 * DDL. Ein Semikolon als Trenner waere hier falsch: In einem PL/SQL-Block
 * stehen Semikolons im Inneren, und ein Zerlegen daran zerschneidet den
 * Block.</p>
 *
 * <p>Aufruf: {@code java FMoonDbSetup [Skript ...]}. Ohne Argumente wird
 * die Standardliste eingespielt.</p>
 */
public class FMoonDbSetup {

    /** Die Skripte in der Reihenfolge, in der sie aufeinander aufbauen. */
    static final String[] STANDARD = {
        "00_drop.sql",
        "01_tables.sql",
        "02_types.sql",
        "03_spatial.sql",
        "04_stations.sql",
        "05_names.sql",
        "06_api.sql",
        "07_api_body.sql",
        "08_views.sql",
        "09_scheduler.sql",
        "10_import.sql",
        // 11_grant_ogp.sql fehlt hier mit Absicht: Es laeuft als OGP,
        // nicht als DEMO, und ein Stapellauf, der sich still an einem
        // fremden Schema zu schaffen macht, waere ein schlechter
        // Stapellauf. Es wird von Hand eingespielt; 12 kommt ohne es
        // aus und meldet dann nur, dass die Kueste fehlt.
        "12_coast.sql"
    };

    static PrintWriter log;
    static int fehler = 0;
    static int anweisungen = 0;

    static void sag(String s) {
        System.out.println(s);
        if (log != null) {
            log.println(s);
            log.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        File logDatei = new File("build/db_setup.log");
        logDatei.getParentFile().mkdirs();
        log = new PrintWriter(logDatei, "UTF-8");

        sag("FMoon Datenbankaufbau");
        sag("=====================================");
        sag(new Date().toString());
        sag("Java " + System.getProperty("java.version"));
        sag("");

        String[] skripte = (args.length > 0) ? args : STANDARD;

        FMoonDataSource src = new FMoonDataSource();
        sag("Verbindung: " + src.getUrl() + " als " + src.getUser());

        Connection c;
        try {
            c = src.getConnection();
        } catch (Throwable t) {
            sag("KEINE VERBINDUNG: " + t);
            sag("");
            sag("Zu pruefen: Laeuft die Datenbank? Ist lib/ojdbc11.jar im");
            sag("Klassenpfad? Stimmen Dienstname, Benutzer und Kennwort?");
            log.close();
            System.exit(1);
            return;
        }
        sag("Verbindung steht.");
        sag("");

        for (int i = 0; i < skripte.length; i++) {
            einspielen(c, skripte[i]);
        }

        sag("");
        sag("=====================================");
        sag("Anweisungen ausgefuehrt: " + anweisungen);
        sag("Fehler: " + fehler);
        if (fehler == 0) {
            sag("Der Aufbau ist durchgelaufen.");
        } else {
            sag("ES GIBT FEHLER - siehe oben.");
        }
        log.close();
        src.close();
        System.exit(fehler == 0 ? 0 : 1);
    }

    static void einspielen(Connection c, String name) {
        File f = new File("db", name);
        if (!f.exists()) {
            f = new File(name);
        }
        sag("--- " + name + " ---");
        if (!f.exists()) {
            sag("   NICHT GEFUNDEN: " + f.getAbsolutePath());
            fehler++;
            return;
        }

        String inhalt;
        try {
            inhalt = new String(Files.readAllBytes(f.toPath()),
                    Charset.forName("UTF-8"));
        } catch (Exception e) {
            sag("   NICHT LESBAR: " + e);
            fehler++;
            return;
        }

        List<String> bloecke = zerlegen(inhalt);
        int ok = 0;
        for (int i = 0; i < bloecke.size(); i++) {
            String sql = bloecke.get(i);
            if (sql.trim().isEmpty()) {
                continue;
            }
            Statement st = null;
            try {
                st = c.createStatement();
                st.execute(sql);
                ok++;
                anweisungen++;
            } catch (SQLException e) {
                fehler++;
                sag("   FEHLER in Block " + (i + 1) + ": " + ersteZeile(e.getMessage()));
                sag("   Anfang des Blocks: " + anfang(sql));
            } finally {
                if (st != null) {
                    try {
                        st.close();
                    } catch (SQLException e) {
                        // nichts zu retten
                    }
                }
            }
        }
        try {
            c.commit();
        } catch (SQLException e) {
            sag("   FEHLER beim Festschreiben: " + ersteZeile(e.getMessage()));
            fehler++;
        }
        sag("   " + ok + " von " + bloecke.size() + " Bloecken ausgefuehrt.");

        // Nach jedem Skript nachsehen, ob etwas ungueltig geworden ist.
        // Ein ungueltiges Package faellt sonst erst beim ersten Aufruf
        // auf - und dann sieht es aus wie ein Fehler des Aufrufers.
        ungueltigeMelden(c);
    }

    /**
     * Zerlegt ein Skript an Zeilen, die nur einen Schraegstrich enthalten.
     */
    static List<String> zerlegen(String inhalt) {
        List<String> out = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        String[] zeilen = inhalt.split("\r?\n", -1);
        for (int i = 0; i < zeilen.length; i++) {
            String z = zeilen[i];
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

    static void ungueltigeMelden(Connection c) {
        Statement st = null;
        try {
            st = c.createStatement();
            java.sql.ResultSet rs = st.executeQuery(
                "SELECT object_type, object_name FROM user_objects "
                + "WHERE status <> 'VALID' AND object_name LIKE 'FMN%' "
                + "ORDER BY object_type, object_name");
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(rs.getString(1)).append(' ').append(rs.getString(2));
            }
            rs.close();
            if (sb.length() > 0) {
                sag("   UNGUELTIG danach: " + sb);
                fehler++;
                zeigeFehlerTexte(c);
            }
        } catch (SQLException e) {
            // Wenn noch keine Tabellen da sind, ist das in Ordnung
        } finally {
            if (st != null) {
                try {
                    st.close();
                } catch (SQLException e) {
                    // nichts zu retten
                }
            }
        }
    }

    static void zeigeFehlerTexte(Connection c) {
        Statement st = null;
        try {
            st = c.createStatement();
            java.sql.ResultSet rs = st.executeQuery(
                "SELECT name, type, line, position, text FROM user_errors "
                + "WHERE name LIKE 'FMN%' ORDER BY name, sequence");
            int n = 0;
            while (rs.next() && n < 20) {
                sag("      " + rs.getString(1) + " " + rs.getString(2)
                        + " Zeile " + rs.getInt(3) + ": " + rs.getString(5).trim());
                n++;
            }
            rs.close();
        } catch (SQLException e) {
            // keine Fehlertabelle erreichbar
        } finally {
            if (st != null) {
                try {
                    st.close();
                } catch (SQLException e) {
                    // nichts zu retten
                }
            }
        }
    }

    static String ersteZeile(String s) {
        if (s == null) {
            return "(ohne Meldung)";
        }
        int nl = s.indexOf('\n');
        return (nl > 0) ? s.substring(0, nl) : s;
    }

    static String anfang(String sql) {
        String s = sql.trim().replace('\n', ' ').replaceAll("\\s+", " ");
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
