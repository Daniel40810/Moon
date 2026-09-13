package com.dan.fmoon;

import com.dan.fmoon.astro.FAngle;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.render.FMoonRenderer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * Das Erklaerbild: links die Bahn von schraeg oben, rechts derselbe
 * Augenblick von der Erde aus gesehen.
 *
 * <p>Die Frage, die dieses Bild in drei Sekunden beantwortet, lautet:
 * <i>Warum ist heute Vollmond?</i> Links steht der Mond der Sonne
 * gegenueber, rechts sieht man seine ganze beleuchtete Seite. Steht er
 * links neben der Erde, sieht man rechts eine Sichel. Der Zusammenhang
 * wird nicht behauptet, sondern gezeigt.</p>
 *
 * <h3>Was an diesem Bild stimmt und was nicht</h3>
 *
 * <p>Es waere leicht, hier ein huebsches Schaubild zu malen, das mit dem
 * Himmel nichts zu tun hat. Deshalb steht genau fest, was echt ist:</p>
 *
 * <ul>
 *   <li><b>Der Winkel Sonne-Erde-Mond ist echt.</b> Er kommt aus den
 *       gerechneten ekliptikalen Laengen und wird in Grad angeschrieben.
 *       Aus ihm folgt die Phase rechts - beide Bilder stammen aus
 *       derselben Rechnung, nicht aus zwei.</li>
 *   <li><b>Die Bahnexzentrizitaet ist echt</b> (0,0549), und der
 *       aktuelle Abstand wird angeschrieben. Erdnaehe und Erdferne sind
 *       damit im Bild zu sehen, nicht nur im Text.</li>
 *   <li><b>Die Groessen und der Abstand sind es NICHT.</b> Massstabsgerecht
 *       waere die Erde bei dieser Bahngroesse zwei Bildpunkte gross und
 *       die Sonne 150 Bildschirme weit weg. Das Bild sagt das selbst,
 *       unten links.</li>
 *   <li><b>Die Bahnneigung fehlt</b> - von oben gesehen ist sie
 *       unsichtbar. Sie ist aber der Grund, warum nicht jeder Vollmond
 *       eine Finsternis ist, und steht deshalb als Satz daneben.</li>
 * </ul>
 *
 * @author Claude fuer Daniel
 */
public class FOrbitView extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Numerische Exzentrizitaet der Mondbahn. */
    public static final double ECCENTRICITY = 0.0549;

    /** Mittlerer Abstand Erde-Mond in Kilometern. */
    public static final double MEAN_DISTANCE_KM = 384400.0;

    private long timeMillis = System.currentTimeMillis();
    private FGeoLocation observerLocation = FGeoLocation.TIMMENDORFER_STRAND;

    private boolean showSunRays = true;
    private boolean showElongation = true;
    private boolean showLabels = true;
    private boolean showScaleNote = true;

    private Color background = new Color(0x0C1219);
    private Color earthColor = new Color(0x2E6E8E);
    private Color moonColor = new Color(0xE8E2D0);
    private Color orbitColor = new Color(0x3A4A57);
    private Color sunColor = new Color(0xD8B163);
    private Color textColor = new Color(0xBFCAD2);

    private transient FMoonEphemeris cached;
    private transient long cachedFor = Long.MIN_VALUE;

    /** Legt das Erklaerbild an. */
    public FOrbitView() {
        setOpaque(true);
        setPreferredSize(new Dimension(900, 420));
        setFont(new Font("SansSerif", Font.PLAIN, 12));
    }

    private FMoonEphemeris ephemeris() {
        if (cached == null || cachedFor != timeMillis) {
            cached = FMoonEphemeris.at(timeMillis, observerLocation);
            cachedFor = timeMillis;
        }
        return cached;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();
        g2.setColor(background);
        g2.fillRect(0, 0, w, h);

        FMoonEphemeris eph = ephemeris();

        // Links die Bahn, rechts der Anblick. Die rechte Seite bekommt
        // ein Quadrat, die linke den Rest - die Mondscheibe braucht
        // keine Breite, die Bahn schon.
        int rechts = Math.min(h - 40, Math.max(120, w / 3));
        int bahnBreite = w - rechts - 24;

        paintOrbit(g2, 0, 0, bahnBreite, h, eph);
        paintAsSeen(g2, w - rechts - 8, (h - rechts) / 2, rechts, eph);

        g2.dispose();
    }

    // ------------------------------------------------------------------
    // Links: die Bahn von schraeg oben
    // ------------------------------------------------------------------

    private void paintOrbit(Graphics2D g2, int x, int y, int w, int h,
            FMoonEphemeris eph) {
        if (w < 80 || h < 80) {
            return;
        }
        int cx = x + w / 2;
        int cy = y + h / 2;
        // Etwas kleiner und etwas hoeher als die Mitte: Unten steht der
        // Hinweis zum Massstab, und der Mond soll nicht hineinlaufen.
        double r = Math.min(w, h) * 0.29;

        // Die Sonnenrichtung: dorthin, wo die Sonne von der Erde aus
        // steht. Auf dem Bild zeigt sie nach links, damit immer dieselbe
        // Seite beleuchtet ist und man die Phasen vergleichen kann.
        double sonneLaenge = eph.sun.apparentLongitude;
        double mondLaenge = eph.moon.longitude;
        // Der Winkel im Bild: die Sonne liegt fest links (180 Grad in
        // Bildschirmkoordinaten), der Mond um die Elongation gedreht.
        double elong = FAngle.norm360(mondLaenge - sonneLaenge);

        if (showSunRays) {
            paintSunRays(g2, x, cy, h);
        }

        // Die Bahn als Ellipse mit der ECHTEN Exzentrizitaet. Die Erde
        // steht in einem Brennpunkt, nicht in der Mitte - das ist der
        // ganze Unterschied zwischen einem Kreis und einer Keplerbahn.
        //
        // Und die Ellipse wird GEDREHT, und zwar dorthin, wo die Erdnaehe
        // wirklich liegt. Der erste Entwurf legte die grosse Achse einfach
        // waagerecht: Dann stand der Mond bei 404 000 km weit ausserhalb
        // seiner eigenen Bahnlinie und bei 370 000 km weit innerhalb. Ein
        // Bild, das eine Bahn zeichnet und den Koerper daneben setzt,
        // erklaert nichts. Die Richtung der Erdnaehe ist die Laenge des
        // Perigaeums, also mittlere Laenge minus mittlere Anomalie; sie
        // dreht sich in knapp neun Jahren einmal herum und hat mit der
        // Sonnenrichtung nichts zu tun.
        double a = r;
        double b = a * Math.sqrt(1.0 - ECCENTRICITY * ECCENTRICITY);
        double c = a * ECCENTRICITY;
        double perigaeumLaenge = eph.moon.meanLongitude - eph.moon.meanAnomaly;
        double phi = Math.toRadians(
                180.0 - FAngle.norm360(perigaeumLaenge - sonneLaenge));
        java.awt.geom.AffineTransform drehung =
                java.awt.geom.AffineTransform.getRotateInstance(-phi, cx, cy);
        // Ungedreht liegt die Erdnaehe rechts: Mittelpunkt um c nach links
        // versetzt, dann faellt der Brennpunkt genau auf cx.
        java.awt.Shape bahn = drehung.createTransformedShape(
                new Ellipse2D.Double(cx - a - c, cy - b, 2 * a, 2 * b));
        g2.setColor(orbitColor);
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[] {3f, 4f}, 0f));
        g2.draw(bahn);
        g2.setStroke(new BasicStroke(1.0f));

        // Die Erdnaehe als Marke - damit man sieht, dass die Bahn eine
        // Richtung hat und nicht nur eine Form.
        double perigaeumX = cx + (a - c) * Math.cos(phi);
        double perigaeumY = cy - (a - c) * Math.sin(phi);

        // Die Erde
        double erdR = Math.max(6.0, r * 0.16);
        paintLitBody(g2, cx, cy, erdR, 180.0, earthColor);

        // Der Mond auf seiner Bahn. Der Abstand kommt aus der Rechnung,
        // nicht aus der Ellipse - so ist zu sehen, dass er bei Erdnaehe
        // wirklich naeher steht.
        double abstand = r * (eph.moon.distanceKm / MEAN_DISTANCE_KM);
        double winkel = Math.toRadians(180.0 - elong);
        double mx = cx + abstand * Math.cos(winkel);
        double my = cy - abstand * Math.sin(winkel);
        double mondR = Math.max(4.0, r * 0.075);

        // Die Blicklinie Erde - Mond
        g2.setColor(new Color(textColor.getRed(), textColor.getGreen(),
                textColor.getBlue(), 80));
        g2.drawLine(cx, cy, (int) Math.round(mx), (int) Math.round(my));

        // Der Mond, beleuchtet von links - also von der Sonne.
        paintLitBody(g2, mx, my, mondR, 180.0, moonColor);

        // Die Erdnaehe als Marke - damit man sieht, dass die Bahn eine
        // Richtung hat und nicht nur eine Form. Sie entfaellt, wenn der
        // Mond gerade dort steht: zwei Beschriftungen uebereinander sind
        // schlechter als eine weggelassene.
        if (showLabels
                && Math.hypot(perigaeumX - mx, perigaeumY - my) > 3 * mondR + 30) {
            g2.setColor(new Color(orbitColor.getRed(), orbitColor.getGreen(),
                    orbitColor.getBlue(), 220));
            g2.fillOval((int) Math.round(perigaeumX) - 2,
                    (int) Math.round(perigaeumY) - 2, 5, 5);
            g2.setFont(getFont().deriveFont(10f));
            g2.setColor(new Color(textColor.getRed(), textColor.getGreen(),
                    textColor.getBlue(), 110));
            g2.drawString("Erdnähe", (int) Math.round(perigaeumX) + 6,
                    (int) Math.round(perigaeumY) + 4);
        }

        if (showElongation) {
            paintElongation(g2, cx, cy, abstand, elong);
        }

        if (showLabels) {
            paintOrbitLabels(g2, x, y, w, h, cx, cy, mx, my, mondR, eph, elong);
        }
    }

    /**
     * Malt einen Koerper, dessen eine Haelfte beleuchtet ist.
     *
     * @param richtungGrad Richtung, aus der das Licht kommt, in
     *                     Bildschirmgrad (180 = von links)
     */
    private void paintLitBody(Graphics2D g2, double cx, double cy, double r,
            double richtungGrad, Color farbe) {
        Ellipse2D.Double scheibe =
                new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        // Nachtseite
        g2.setColor(dunkler(farbe, 0.30));
        g2.fill(scheibe);
        // Tagseite: die Haelfte, die zum Licht zeigt
        Area hell = new Area(scheibe);
        hell.intersect(new Area(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r,
                richtungGrad - 90.0, 180.0, Arc2D.PIE)));
        g2.setColor(farbe);
        g2.fill(hell);
        g2.setColor(dunkler(farbe, 0.6));
        g2.draw(scheibe);
    }

    private void paintSunRays(Graphics2D g2, int x, int cy, int h) {
        g2.setColor(new Color(sunColor.getRed(), sunColor.getGreen(),
                sunColor.getBlue(), 70));
        g2.setStroke(new BasicStroke(1.4f));
        int n = 5;
        for (int i = 0; i < n; i++) {
            int yy = cy - h / 5 + i * (2 * h / 5) / (n - 1);
            g2.drawLine(x + 4, yy, x + 34, yy);
            GeneralPath spitze = new GeneralPath();
            spitze.moveTo(x + 34, yy);
            spitze.lineTo(x + 28, yy - 3);
            spitze.lineTo(x + 28, yy + 3);
            spitze.closePath();
            g2.fill(spitze);
        }
        g2.setStroke(new BasicStroke(1.0f));
        g2.setColor(sunColor);
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g2.drawString("Sonne", x + 4, cy - h / 5 - 8);
    }

    private void paintElongation(Graphics2D g2, int cx, int cy,
            double abstand, double elong) {
        double bogenR = Math.max(18.0, abstand * 0.42);
        g2.setColor(new Color(sunColor.getRed(), sunColor.getGreen(),
                sunColor.getBlue(), 110));
        // Von der Sonnenrichtung (180 Grad im Bild) um die Elongation
        g2.draw(new Arc2D.Double(cx - bogenR, cy - bogenR,
                2 * bogenR, 2 * bogenR, 180.0, -elong, Arc2D.OPEN));
        // Die Richtung zur Sonne als kurzer Strich
        g2.drawLine(cx, cy, (int) Math.round(cx - bogenR * 1.25), cy);

        // Bei fast null oder fast 360 Grad ist der Bogen ein Punkt; dann
        // steht die Zahl neben der Sonnenrichtung statt im Nichts.
        double mitte = Math.toRadians(180.0
                - ((elong < 8.0 || elong > 352.0) ? 0.0 : elong / 2.0));
        String text = String.format("%.0f°", elong);
        g2.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (int) Math.round(cx + (bogenR + 12) * Math.cos(mitte))
               - fm.stringWidth(text) / 2;
        int ty = (int) Math.round(cy - (bogenR + 12) * Math.sin(mitte))
               + fm.getAscent() / 2;
        g2.setColor(sunColor);
        g2.drawString(text, tx, ty);
    }

    private void paintOrbitLabels(Graphics2D g2, int x, int y, int w, int h,
            int cx, int cy, double mx, double my, double mondR,
            FMoonEphemeris eph, double elong) {
        g2.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(textColor);
        g2.drawString("Erde", cx + 10, cy + fm.getAscent() / 2 + 14);
        // Der Mondname nach aussen, weg von der Erde - sonst steht er bei
        // Neumond mitten im Winkeltext.
        double weg = Math.hypot(mx - cx, my - cy);
        double ex = (weg < 1.0) ? 1.0 : (mx - cx) / weg;
        double ey = (weg < 1.0) ? 0.0 : (my - cy) / weg;
        int lx = (int) Math.round(mx + ex * (mondR + 8));
        int ly = (int) Math.round(my + ey * (mondR + 8) + fm.getAscent() / 2);
        if (ex < 0) {
            lx -= fm.stringWidth("Mond");
        }
        g2.drawString("Mond", lx, ly);

        // Was echt ist und was nicht - unten links, klein, aber da.
        if (showScaleNote) {
            g2.setColor(new Color(textColor.getRed(), textColor.getGreen(),
                    textColor.getBlue(), 150));
            String[] zeilen = {
                String.format("Abstand %.0f km (Bahn mit echter "
                        + "Exzentrizität 0,055)", eph.moon.distanceKm),
                "Größen und Sonnenabstand nicht maßstäblich",
                "Bahnneigung 5,1° hier unsichtbar — deshalb ist "
                    + "nicht jeder Vollmond eine Finsternis"
            };
            int yy = y + h - 8 - (zeilen.length - 1) * (fm.getHeight());
            for (int i = 0; i < zeilen.length; i++) {
                g2.drawString(zeilen[i], x + 8, yy + i * fm.getHeight());
            }
        }
    }

    // ------------------------------------------------------------------
    // Rechts: derselbe Augenblick von der Erde aus
    // ------------------------------------------------------------------

    private void paintAsSeen(Graphics2D g2, int x, int y, int size,
            FMoonEphemeris eph) {
        if (size < 40) {
            return;
        }
        int scheibe = (int) (size * 0.78);
        BufferedImage bild = FMoonRenderer.render(scheibe,
                eph.phase.phaseAngle, eph.phase.brightLimbAngle,
                eph.libration.longitude, eph.libration.latitude,
                eph.libration.axisPositionAngle,
                0.06 * (1.0 - eph.phase.illuminatedFraction),
                moonColor, new Color(0x24405A));
        g2.drawImage(bild, x + (size - scheibe) / 2,
                y + (size - scheibe) / 2 - 10, null);

        if (!showLabels) {
            return;
        }
        g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics fm = g2.getFontMetrics();
        String name = eph.phase.phaseName();
        g2.setColor(textColor);
        g2.drawString(name, x + (size - fm.stringWidth(name)) / 2,
                y + size - 16);

        g2.setFont(getFont().deriveFont(11f));
        fm = g2.getFontMetrics();
        String unten = String.format("%.0f %% beleuchtet",
                eph.phase.illuminatedFraction * 100.0);
        g2.setColor(new Color(textColor.getRed(), textColor.getGreen(),
                textColor.getBlue(), 170));
        g2.drawString(unten, x + (size - fm.stringWidth(unten)) / 2,
                y + size);
    }

    // ------------------------------------------------------------------
    // Auskunft ueber das, was gezeichnet wird
    //
    // Ein Bild laesst sich schlecht pruefen, eine Zahl gut. Diese beiden
    // Methoden geben heraus, was im Bild steht - damit eine Pruefung
    // nachrechnen kann, ob der Mond wirklich auf seiner Bahn liegt.
    // ------------------------------------------------------------------

    /**
     * Der gezeichnete Winkel Sonne-Erde-Mond in Grad.
     *
     * @return Elongation, 0 bis 360
     */
    public double getElongationDeg() {
        FMoonEphemeris eph = ephemeris();
        return FAngle.norm360(eph.moon.longitude - eph.sun.apparentLongitude);
    }

    /**
     * Der Winkel zwischen der Erdnaehe und dem Mond, von der Erde aus -
     * die wahre Anomalie, soweit sie im Bild steckt.
     *
     * @return Winkel in Grad, 0 bis 360
     */
    public double getTrueAnomalyDeg() {
        FMoonEphemeris eph = ephemeris();
        double perigaeum = eph.moon.meanLongitude - eph.moon.meanAnomaly;
        return FAngle.norm360(eph.moon.longitude - perigaeum);
    }

    /**
     * Der Abstand, den eine Keplerellipse mit der Exzentrizitaet 0,0549
     * bei diesem Winkel haette - in Vielfachen der grossen Halbachse.
     *
     * <p>Der Vergleich mit dem wirklichen Abstand sagt, wie gut die
     * gezeichnete Bahn zum gezeichneten Mond passt. Ganz genau kann er
     * nicht sein: Der wirkliche Mond folgt keiner reinen Ellipse, die
     * Sonne stoert seine Bahn um mehrere tausend Kilometer. Genau das
     * macht die Abweichung interessant statt aergerlich.</p>
     *
     * @return Abstand in Vielfachen der grossen Halbachse
     */
    public double getEllipseRadiusFactor() {
        double nu = Math.toRadians(getTrueAnomalyDeg());
        double e = ECCENTRICITY;
        return (1.0 - e * e) / (1.0 + e * Math.cos(nu));
    }

    private static Color dunkler(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f),
                (int) (c.getBlue() * f));
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Der dargestellte Zeitpunkt.
     *
     * @return Zeitpunkt in Millisekunden seit 1970
     */
    public long getTimeMillis() {
        return timeMillis;
    }

    /**
     * Setzt den dargestellten Zeitpunkt.
     *
     * @param millis Zeitpunkt in Millisekunden seit 1970
     */
    public void setTimeMillis(long millis) {
        long alt = timeMillis;
        timeMillis = millis;
        firePropertyChange("timeMillis", alt, millis);
        repaint();
    }

    /**
     * Der Beobachtungsort. Er geht nur in die rechte Seite ein - von der
     * Bahn aus gesehen steht der Mond fuer alle gleich.
     *
     * @return Ort
     */
    public FGeoLocation getObserverLocation() {
        return observerLocation;
    }

    /**
     * Setzt den Beobachtungsort.
     *
     * @param loc Ort
     */
    public void setObserverLocation(FGeoLocation loc) {
        FGeoLocation alt = observerLocation;
        observerLocation = (loc == null)
                ? FGeoLocation.TIMMENDORFER_STRAND : loc;
        cachedFor = Long.MIN_VALUE;
        firePropertyChange("observerLocation", alt, observerLocation);
        repaint();
    }

    /**
     * Werden die Sonnenstrahlen gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowSunRays() {
        return showSunRays;
    }

    /**
     * Schaltet die Sonnenstrahlen.
     *
     * @param b wahr fuer sichtbar
     */
    public void setShowSunRays(boolean b) {
        boolean alt = showSunRays;
        showSunRays = b;
        firePropertyChange("showSunRays", alt, b);
        repaint();
    }

    /**
     * Wird der Winkel Sonne-Erde-Mond angeschrieben?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowElongation() {
        return showElongation;
    }

    /**
     * Schaltet den Winkel Sonne-Erde-Mond.
     *
     * @param b wahr fuer sichtbar
     */
    public void setShowElongation(boolean b) {
        boolean alt = showElongation;
        showElongation = b;
        firePropertyChange("showElongation", alt, b);
        repaint();
    }

    /**
     * Werden Beschriftungen gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowLabels() {
        return showLabels;
    }

    /**
     * Schaltet die Beschriftungen.
     *
     * @param b wahr fuer sichtbar
     */
    public void setShowLabels(boolean b) {
        boolean alt = showLabels;
        showLabels = b;
        firePropertyChange("showLabels", alt, b);
        repaint();
    }

    /**
     * Wird der Hinweis zum Massstab gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowScaleNote() {
        return showScaleNote;
    }

    /**
     * Schaltet den Hinweis zum Massstab.
     *
     * <p>Ihn abzuschalten macht das Bild huebscher und unehrlicher. Wer
     * das Bild weitergibt, sollte ihn stehen lassen.</p>
     *
     * @param b wahr fuer sichtbar
     */
    public void setShowScaleNote(boolean b) {
        boolean alt = showScaleNote;
        showScaleNote = b;
        firePropertyChange("showScaleNote", alt, b);
        repaint();
    }

    /**
     * Hintergrundfarbe.
     *
     * @return Farbe
     */
    public Color getBackground2() {
        return background;
    }

    /**
     * Setzt die Hintergrundfarbe des Bildes.
     *
     * @param c Farbe
     */
    public void setBackground2(Color c) {
        Color alt = background;
        background = c;
        firePropertyChange("background2", alt, c);
        repaint();
    }

    /**
     * Farbe der Erde.
     *
     * @return Farbe
     */
    public Color getEarthColor() {
        return earthColor;
    }

    /**
     * Setzt die Farbe der Erde.
     *
     * @param c Farbe
     */
    public void setEarthColor(Color c) {
        Color alt = earthColor;
        earthColor = c;
        firePropertyChange("earthColor", alt, c);
        repaint();
    }

    /**
     * Farbe des Mondes.
     *
     * @return Farbe
     */
    public Color getMoonColor() {
        return moonColor;
    }

    /**
     * Setzt die Farbe des Mondes.
     *
     * @param c Farbe
     */
    public void setMoonColor(Color c) {
        Color alt = moonColor;
        moonColor = c;
        firePropertyChange("moonColor", alt, c);
        repaint();
    }

    /**
     * Farbe der Bahnlinie.
     *
     * @return Farbe
     */
    public Color getOrbitColor() {
        return orbitColor;
    }

    /**
     * Setzt die Farbe der Bahnlinie.
     *
     * @param c Farbe
     */
    public void setOrbitColor(Color c) {
        Color alt = orbitColor;
        orbitColor = c;
        firePropertyChange("orbitColor", alt, c);
        repaint();
    }

    /**
     * Farbe der Sonnenstrahlen und des Winkelbogens.
     *
     * @return Farbe
     */
    public Color getSunColor() {
        return sunColor;
    }

    /**
     * Setzt die Farbe der Sonnenstrahlen.
     *
     * @param c Farbe
     */
    public void setSunColor(Color c) {
        Color alt = sunColor;
        sunColor = c;
        firePropertyChange("sunColor", alt, c);
        repaint();
    }

    /**
     * Farbe der Beschriftung.
     *
     * @return Farbe
     */
    public Color getTextColor() {
        return textColor;
    }

    /**
     * Setzt die Farbe der Beschriftung.
     *
     * @param c Farbe
     */
    public void setTextColor(Color c) {
        Color alt = textColor;
        textColor = c;
        firePropertyChange("textColor", alt, c);
        repaint();
    }
}
