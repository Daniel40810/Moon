package com.dan.fmoon.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Die Verbindung zur Oracle-Datenbank.
 *
 * <p>Bewusst schlicht: eine Verbindung, kein Vorrat. FMoon fragt die
 * Datenbank beim Start nach den Stationen und beim Vorrechnen nach Platz
 * zum Schreiben - beides selten. Ein Verbindungsvorrat waere Aufwand ohne
 * Nutzen.</p>
 *
 * <p>Wichtig fuer alles, was hier geschrieben und gelesen wird: <b>Alle
 * Zeitpunkte in den FMN-Tabellen stehen in UTC.</b> Die Umrechnung in
 * Ortszeit geschieht an genau einer Stelle, in der Anzeige.</p>
 *
 * @author Claude fuer Daniel
 */
public class FMoonDataSource {

    /** Vorgabe fuer den Dienstnamen. */
    public static final String DEFAULT_SERVICE = "PDBORCL";

    /** Vorgabe fuer den Rechner. */
    public static final String DEFAULT_HOST = "localhost";

    /** Vorgabe fuer den Port. */
    public static final int DEFAULT_PORT = 1521;

    /** Vorgabe fuer das Schema. */
    public static final String DEFAULT_USER = "DEMO";

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String service = DEFAULT_SERVICE;
    private String user = DEFAULT_USER;
    private String password = "de";

    private Connection connection;

    /**
     * Legt eine Verbindung mit den Vorgabewerten an.
     */
    public FMoonDataSource() {
    }

    /**
     * Legt eine Verbindung an.
     *
     * @param host     Rechner
     * @param port     Port
     * @param service  Dienstname
     * @param user     Benutzer
     * @param password Kennwort
     */
    public FMoonDataSource(String host, int port, String service,
            String user, String password) {
        this.host = host;
        this.port = port;
        this.service = service;
        this.user = user;
        this.password = password;
    }

    /**
     * Die JDBC-Adresse, die sich aus den Angaben ergibt.
     *
     * @return Adresse
     */
    public String getUrl() {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + service;
    }

    /**
     * Oeffnet die Verbindung, falls sie noch nicht offen ist.
     *
     * @return offene Verbindung
     * @throws SQLException wenn die Verbindung nicht zustande kommt
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        // Die Sitzungszeitzone wird NICHT angefasst. Daran zu drehen hat
        // bei FCurvedField einen Abend gekostet; die FMN-Tabellen halten
        // ihre Zeiten ohnehin in UTC.
        connection = DriverManager.getConnection(getUrl(), props);
        connection.setAutoCommit(false);
        return connection;
    }

    /**
     * Ist eine Verbindung erreichbar? Fragt nach, ohne eine Ausnahme
     * nach aussen zu geben - der Aufrufer soll sich entscheiden koennen,
     * ohne einen Fehlerblock zu schreiben.
     *
     * @return wahr, wenn die Datenbank antwortet
     */
    public boolean isAvailable() {
        try {
            Connection c = getConnection();
            return c != null && !c.isClosed();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Schliesst die Verbindung.
     */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // beim Schliessen ist nichts mehr zu retten
            }
            connection = null;
        }
    }

    /**
     * Rechner.
     *
     * @return Rechnername
     */
    public String getHost() {
        return host;
    }

    /**
     * Setzt den Rechner.
     *
     * @param host Rechnername
     */
    public void setHost(String host) {
        this.host = host;
        close();
    }

    /**
     * Port.
     *
     * @return Port
     */
    public int getPort() {
        return port;
    }

    /**
     * Setzt den Port.
     *
     * @param port Port
     */
    public void setPort(int port) {
        this.port = port;
        close();
    }

    /**
     * Dienstname.
     *
     * @return Dienstname
     */
    public String getService() {
        return service;
    }

    /**
     * Setzt den Dienstnamen.
     *
     * @param service Dienstname
     */
    public void setService(String service) {
        this.service = service;
        close();
    }

    /**
     * Benutzer.
     *
     * @return Benutzername
     */
    public String getUser() {
        return user;
    }

    /**
     * Setzt den Benutzer.
     *
     * @param user Benutzername
     */
    public void setUser(String user) {
        this.user = user;
        close();
    }

    /**
     * Setzt das Kennwort.
     *
     * @param password Kennwort
     */
    public void setPassword(String password) {
        this.password = password;
        close();
    }
}
