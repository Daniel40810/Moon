package com.dan.fmoon;

import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.astro.FRiseSet;
import com.dan.fmoon.render.FMoonRenderer;
import com.dan.fstyle.FColors;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Der Mond als beleuchtete Kugel, zu einem gewaehlten Zeitpunkt und fuer einen
 * gewaehlten Ort.
 *
 * <p>Die Phase wird nicht gezeichnet, sondern beleuchtet: Der Rechenkern
 * liefert den Phasenwinkel, den Positionswinkel der hellen Gliedmitte und die
 * Libration, und der Renderer setzt eine Kugel unter genau dieses Licht.
 * Deshalb stimmt nicht nur die Breite der Sichel, sondern auch ihre Neigung
 * und die perspektivische Verkuerzung der Maria zum Rand hin.</p>
 *
 * <p>Der scheinbare Durchmesser folgt der Entfernung: Zwischen Erdnaehe und
 * Erdferne liegen vierzehn Prozent, was auf dem Bildschirm deutlich zu sehen
 * ist, am Himmel aber kaum jemand bemerkt.</p>
 *
 * @author Claude fuer Daniel
 */
public class FMoonGlobe extends JPanel implements FTheme.ThemeListener {

    private static final long serialVersionUID = 1L;

    /** Mittlerer Abstand Erde-Mond in Kilometern, Bezug fuer die Groesse. */
    private static final double MEAN_DISTANCE = 385000.56;

    private long timeMillis = System.currentTimeMillis();
    private FGeoLocation location = FGeoLocation.TIMMENDORFER_STRAND;
    private FMoonViewMode viewMode = FMoonViewMode.GEOCENTRIC;

    private boolean animated;
    private double timeLapseFactor = 3600.0;
    private boolean live;

    private boolean showLabels = true;
    private boolean showStars = true;
    private boolean showEarthshine = true;
    private boolean scaleWithDistance = true;
    private boolean showLibrationGrid;

    private double earthshineStrength = 0.055;
    private double diskFill = 0.62;

    private Color sunlitColor = new Color(236, 229, 211);
    private Color earthshineColor = new Color(74, 96, 116);
    private Color starColor = new Color(196, 210, 224);
    private Color labelColor;
    private Color valueColor;
    private boolean themeAware = true;

    private Timer timer;
    private long lastTick;
    private int[] starField;

    private FMoonEphemeris cached;
    private long cachedFor = Long.MIN_VALUE;

    /**
     * Legt die Komponente mit dem aktuellen Zeitpunkt an.
     */
    public FMoonGlobe() {
        setOpaque(true);
        setPreferredSize(new Dimension(360, 400));
        applyTheme(FTheme.getInstance());
    }

    // ------------------------------------------------------------------
    // Lebenszyklus
    // ------------------------------------------------------------------

    @Override
    public void addNotify() {
        super.addNotify();
        if (themeAware) {
            FTheme.getInstance().addThemeListener(this);
        }
        startTimerIfNeeded();
    }

    @Override
    public void removeNotify() {
        if (themeAware) {
            FTheme.getInstance().removeThemeListener(this);
        }
        stopTimer();
        super.removeNotify();
    }

    @Override
    public void themeChanged(FTheme theme) {
        applyTheme(theme);
        repaint();
    }

    private void applyTheme(FTheme theme) {
        if (!themeAware) {
            return;
        }
        setBackground(theme.getBackground());
        labelColor = theme.getTextMuted();
        valueColor = theme.getText();
        starColor = FColors.withAlpha(theme.getText(), 150);
    }

    private void startTimerIfNeeded() {
        if ((animated || live) && timer == null) {
            lastTick = System.currentTimeMillis();
            timer = new Timer(60, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    tick();
                }
            });
            timer.start();
        }
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (live) {
            setTimeMillis(now);
        } else if (animated) {
            long delta = now - lastTick;
            setTimeMillis(timeMillis + (long) (delta * timeLapseFactor));
        }
        lastTick = now;
    }

    // ------------------------------------------------------------------
    // Rechnung
    // ------------------------------------------------------------------

    /**
     * Die Angaben zum eingestellten Zeitpunkt und Ort.
     *
     * @return Ephemeriden
     */
    public FMoonEphemeris getEphemeris() {
        // auf zehn Sekunden gerundet zwischengespeichert - bei laufender
        // Animation sonst sechzehn volle Rechnungen je Sekunde
        long key = timeMillis / 10000L;
        if (cached == null || key != cachedFor) {
            cached = FMoonEphemeris.at(timeMillis, location);
            cachedFor = key;
        }
        return cached;
    }

    private void invalidateEphemeris() {
        cachedFor = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, w, h);

        if (showStars) {
            paintStars(g2, w, h);
        }

        FMoonEphemeris eph = getEphemeris();

        int labelSpace = showLabels ? Math.min(96, h / 4) : 0;
        int available = Math.min(w, h - labelSpace);
        double fill = diskFill;
        if (scaleWithDistance) {
            fill *= MEAN_DISTANCE / eph.moon.distanceKm;
        }
        int size = (int) Math.round(available * fill * 1.45);
        if (size < 8) {
            size = 8;
        }

        double limb = (viewMode == FMoonViewMode.HORIZON)
                ? eph.limbTiltFromVertical
                : eph.phase.brightLimbAngle;
        double axis = (viewMode == FMoonViewMode.HORIZON)
                ? eph.libration.axisPositionAngle - eph.parallacticAngle
                : eph.libration.axisPositionAngle;

        BufferedImage moon = FMoonRenderer.render(size,
                eph.phase.phaseAngle, limb,
                eph.libration.longitude, eph.libration.latitude, axis,
                showEarthshine ? earthshineStrength * (1.0 - eph.phase.illuminatedFraction) : 0.0,
                sunlitColor, earthshineColor);

        int cx = w / 2;
        int cy = (h - labelSpace) / 2;
        g2.drawImage(moon, cx - size / 2, cy - size / 2, null);

        if (showLibrationGrid) {
            paintLibrationMark(g2, cx, cy, size, eph);
        }

        if (showLabels) {
            paintLabels(g2, w, h, labelSpace, eph);
        }

        g2.dispose();
    }

    private void paintStars(Graphics2D g2, int w, int h) {
        if (starField == null || starField.length < 3) {
            buildStarField();
        }
        for (int i = 0; i + 2 < starField.length; i += 3) {
            int x = starField[i] % Math.max(1, w);
            int y = starField[i + 1] % Math.max(1, h);
            int bright = starField[i + 2];
            g2.setColor(FColors.withAlpha(starColor, 30 + bright));
            int s = bright > 90 ? 2 : 1;
            g2.fillRect(x, y, s, s);
        }
    }

    private void buildStarField() {
        int count = 150;
        starField = new int[count * 3];
        long seed = 0x53544152L;   // "STAR"
        for (int i = 0; i < count; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starField[i * 3] = (int) ((seed >>> 33) % 4000);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starField[i * 3 + 1] = (int) ((seed >>> 33) % 4000);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            starField[i * 3 + 2] = (int) ((seed >>> 33) % 110);
        }
    }

    private void paintLibrationMark(Graphics2D g2, int cx, int cy, int size,
            FMoonEphemeris eph) {
        // Zeigt, wohin die Libration den Mittelpunkt der sichtbaren Scheibe
        // geschoben hat: Das Kreuz steht auf dem Punkt, der dem Beobachter
        // genau gegenuebersteht.
        double r = size / 2.0;
        double px = -Math.sin(Math.toRadians(eph.libration.longitude)) * r;
        double py = -Math.sin(Math.toRadians(eph.libration.latitude)) * r;
        g2.setColor(FColors.withAlpha(valueColor, 110));
        int x = (int) Math.round(cx + px);
        int y = (int) Math.round(cy - py);
        g2.drawLine(x - 5, y, x + 5, y);
        g2.drawLine(x, y - 5, x, y + 5);
    }

    private void paintLabels(Graphics2D g2, int w, int h, int space,
            FMoonEphemeris eph) {
        FTheme theme = FTheme.getInstance();
        int y = h - space + 6;

        g2.setFont(theme.getFont(FFontRole.TITLE));
        g2.setColor(valueColor);
        String titel = eph.phase.phaseName();
        FontMetrics fm = g2.getFontMetrics();
        y += fm.getAscent();
        g2.drawString(titel, (w - fm.stringWidth(titel)) / 2, y);

        g2.setFont(theme.getFont(FFontRole.CAPTION));
        fm = g2.getFontMetrics();
        y += fm.getHeight() + 4;

        String zeile1 = String.format("%.1f %% beleuchtet · %.1f Tage alt · %.0f km",
                eph.phase.illuminatedFraction * 100.0,
                FMoonPhaseTimes.ageDays(eph.jdUt),
                eph.moon.distanceKm);
        g2.setColor(labelColor);
        g2.drawString(zeile1, (w - fm.stringWidth(zeile1)) / 2, y);

        y += fm.getHeight() + 2;
        SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
        TimeZone zone = location.getTimeZone();
        hhmm.setTimeZone(zone);

        double mitternacht = localMidnight(eph.jdUt, zone);
        FRiseSet rs = FRiseSet.moonFor(mitternacht, location);
        StringBuilder zeile2 = new StringBuilder();
        zeile2.append(location.getName()).append("  ");
        if (rs.hasRise()) {
            zeile2.append("auf ").append(hhmm.format(
                    new Date(FAstroTime.millisFromJulianDay(rs.riseJdUt))));
        } else {
            zeile2.append("kein Aufgang");
        }
        zeile2.append("  ");
        if (rs.hasSet()) {
            zeile2.append("unter ").append(hhmm.format(
                    new Date(FAstroTime.millisFromJulianDay(rs.setJdUt))));
        } else {
            zeile2.append("kein Untergang");
        }
        String z2 = zeile2.toString();
        g2.drawString(z2, (w - fm.stringWidth(z2)) / 2, y);

        y += fm.getHeight() + 2;
        String zeile3 = String.format("Höhe %+.1f° · Azimut %.0f° · %.1f'",
                eph.altitude, eph.azimuth, eph.apparentDiameterArcmin());
        if (eph.altitude < 0.0) {
            zeile3 = zeile3 + " (unter dem Horizont)";
        }
        g2.drawString(zeile3, (w - fm.stringWidth(zeile3)) / 2, y);
    }

    private static double localMidnight(double jdUt, TimeZone zone) {
        long millis = FAstroTime.millisFromJulianDay(jdUt);
        int offset = zone.getOffset(millis);
        double local = jdUt + offset / 86400000.0;
        return Math.floor(local - 0.5) + 0.5 - offset / 86400000.0;
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Der dargestellte Zeitpunkt.
     *
     * @return Millisekunden seit 1970-01-01
     */
    public long getTimeMillis() {
        return timeMillis;
    }

    /**
     * Setzt den dargestellten Zeitpunkt.
     *
     * @param timeMillis Millisekunden seit 1970-01-01
     */
    public void setTimeMillis(long timeMillis) {
        long old = this.timeMillis;
        this.timeMillis = timeMillis;
        invalidateEphemeris();
        firePropertyChange("timeMillis", old, timeMillis);
        repaint();
    }

    /**
     * Der Beobachtungsort.
     *
     * <p>Die Eigenschaft heisst absichtlich nicht "location": Diesen Namen
     * belegt bereits {@link java.awt.Component#getLocation()} mit einem Punkt
     * in Bildpunkten. Ein Ueberschreiben ist unmoeglich, weil sich die
     * Rueckgabetypen widersprechen.</p>
     *
     * @return Ort
     */
    public FGeoLocation getObserverLocation() {
        return location;
    }

    /**
     * Setzt den Beobachtungsort.
     *
     * @param observerLocation Ort, nicht null
     */
    public void setObserverLocation(FGeoLocation observerLocation) {
        if (observerLocation == null) {
            return;
        }
        FGeoLocation old = this.location;
        this.location = observerLocation;
        invalidateEphemeris();
        firePropertyChange("observerLocation", old, observerLocation);
        repaint();
    }

    /**
     * Die Ausrichtung der Scheibe.
     *
     * @return Darstellungsart
     */
    public FMoonViewMode getViewMode() {
        return viewMode;
    }

    /**
     * Setzt die Ausrichtung der Scheibe.
     *
     * @param viewMode Darstellungsart, nicht null
     */
    public void setViewMode(FMoonViewMode viewMode) {
        if (viewMode == null) {
            return;
        }
        FMoonViewMode old = this.viewMode;
        this.viewMode = viewMode;
        firePropertyChange("viewMode", old, viewMode);
        repaint();
    }

    /**
     * Laeuft der Zeitraffer?
     *
     * @return wahr, wenn animiert wird
     */
    public boolean isAnimated() {
        return animated;
    }

    /**
     * Schaltet den Zeitraffer ein oder aus.
     *
     * @param animated wahr fuer laufende Zeit
     */
    public void setAnimated(boolean animated) {
        boolean old = this.animated;
        this.animated = animated;
        firePropertyChange("animated", old, animated);
        if (animated) {
            startTimerIfNeeded();
        } else if (!live) {
            stopTimer();
        }
    }

    /**
     * Wie schnell die Zeit im Zeitraffer laeuft.
     *
     * @return Faktor gegenueber der Wirklichkeit
     */
    public double getTimeLapseFactor() {
        return timeLapseFactor;
    }

    /**
     * Setzt die Geschwindigkeit des Zeitraffers.
     *
     * @param timeLapseFactor Faktor, 3600 bedeutet eine Stunde je Sekunde
     */
    public void setTimeLapseFactor(double timeLapseFactor) {
        double old = this.timeLapseFactor;
        this.timeLapseFactor = timeLapseFactor;
        firePropertyChange("timeLapseFactor", old, timeLapseFactor);
    }

    /**
     * Folgt die Anzeige der Uhr?
     *
     * @return wahr, wenn die aktuelle Zeit gezeigt wird
     */
    public boolean isLive() {
        return live;
    }

    /**
     * Laesst die Anzeige der Uhr folgen.
     *
     * @param live wahr fuer die aktuelle Zeit
     */
    public void setLive(boolean live) {
        boolean old = this.live;
        this.live = live;
        firePropertyChange("live", old, live);
        if (live) {
            startTimerIfNeeded();
        } else if (!animated) {
            stopTimer();
        }
    }

    /**
     * Werden Beschriftungen gezeigt?
     *
     * @return wahr, wenn beschriftet wird
     */
    public boolean isShowLabels() {
        return showLabels;
    }

    /**
     * Schaltet die Beschriftungen.
     *
     * @param showLabels wahr fuer Beschriftungen
     */
    public void setShowLabels(boolean showLabels) {
        boolean old = this.showLabels;
        this.showLabels = showLabels;
        firePropertyChange("showLabels", old, showLabels);
        repaint();
    }

    /**
     * Wird ein Sternenhintergrund gezeichnet?
     *
     * @return wahr, wenn Sterne gezeichnet werden
     */
    public boolean isShowStars() {
        return showStars;
    }

    /**
     * Schaltet den Sternenhintergrund.
     *
     * @param showStars wahr fuer Sterne
     */
    public void setShowStars(boolean showStars) {
        boolean old = this.showStars;
        this.showStars = showStars;
        firePropertyChange("showStars", old, showStars);
        repaint();
    }

    /**
     * Wird der Erdschein gezeichnet?
     *
     * @return wahr, wenn die Nachtseite schwach leuchtet
     */
    public boolean isShowEarthshine() {
        return showEarthshine;
    }

    /**
     * Schaltet den Erdschein, das aschgraue Licht auf der Nachtseite.
     *
     * @param showEarthshine wahr fuer Erdschein
     */
    public void setShowEarthshine(boolean showEarthshine) {
        boolean old = this.showEarthshine;
        this.showEarthshine = showEarthshine;
        firePropertyChange("showEarthshine", old, showEarthshine);
        repaint();
    }

    /**
     * Staerke des Erdscheins.
     *
     * @return Staerke, 0 bis 1
     */
    public double getEarthshineStrength() {
        return earthshineStrength;
    }

    /**
     * Setzt die Staerke des Erdscheins. Der Wert wird zusaetzlich mit dem
     * unbeleuchteten Anteil gewichtet - bei Vollmond leuchtet die Erde vom
     * Mond aus gesehen als schmale Sichel und gibt kaum Licht.
     *
     * @param earthshineStrength Staerke, 0 bis 1
     */
    public void setEarthshineStrength(double earthshineStrength) {
        double old = this.earthshineStrength;
        this.earthshineStrength = Math.max(0.0, Math.min(1.0, earthshineStrength));
        firePropertyChange("earthshineStrength", old, this.earthshineStrength);
        repaint();
    }

    /**
     * Folgt die Scheibengroesse der Entfernung?
     *
     * @return wahr, wenn die Groesse mit der Entfernung wechselt
     */
    public boolean isScaleWithDistance() {
        return scaleWithDistance;
    }

    /**
     * Laesst die Scheibengroesse der Entfernung folgen.
     *
     * @param scaleWithDistance wahr fuer wechselnde Groesse
     */
    public void setScaleWithDistance(boolean scaleWithDistance) {
        boolean old = this.scaleWithDistance;
        this.scaleWithDistance = scaleWithDistance;
        firePropertyChange("scaleWithDistance", old, scaleWithDistance);
        repaint();
    }

    /**
     * Wird die Librationsmarke gezeigt?
     *
     * @return wahr, wenn die Marke gezeichnet wird
     */
    public boolean isShowLibrationGrid() {
        return showLibrationGrid;
    }

    /**
     * Zeigt ein Kreuz auf dem Punkt, der dem Beobachter genau gegenuebersteht.
     * Es wandert im Lauf eines Monats um bis zu acht Grad - das ist die
     * Libration, sichtbar gemacht.
     *
     * @param showLibrationGrid wahr fuer die Marke
     */
    public void setShowLibrationGrid(boolean showLibrationGrid) {
        boolean old = this.showLibrationGrid;
        this.showLibrationGrid = showLibrationGrid;
        firePropertyChange("showLibrationGrid", old, showLibrationGrid);
        repaint();
    }

    /**
     * Wie viel der Flaeche die Scheibe einnimmt.
     *
     * @return Anteil, 0 bis 1
     */
    public double getDiskFill() {
        return diskFill;
    }

    /**
     * Setzt, wie viel der Flaeche die Scheibe einnimmt.
     *
     * @param diskFill Anteil, 0,2 bis 1
     */
    public void setDiskFill(double diskFill) {
        double old = this.diskFill;
        this.diskFill = Math.max(0.2, Math.min(1.0, diskFill));
        firePropertyChange("diskFill", old, this.diskFill);
        repaint();
    }

    /**
     * Farbe der beleuchteten Seite.
     *
     * @return Farbe
     */
    public Color getSunlitColor() {
        return sunlitColor;
    }

    /**
     * Setzt die Farbe der beleuchteten Seite.
     *
     * @param sunlitColor Farbe, nicht null
     */
    public void setSunlitColor(Color sunlitColor) {
        if (sunlitColor == null) {
            return;
        }
        Color old = this.sunlitColor;
        this.sunlitColor = sunlitColor;
        firePropertyChange("sunlitColor", old, sunlitColor);
        repaint();
    }

    /**
     * Farbe des Erdscheins.
     *
     * @return Farbe
     */
    public Color getEarthshineColor() {
        return earthshineColor;
    }

    /**
     * Setzt die Farbe des Erdscheins.
     *
     * @param earthshineColor Farbe, nicht null
     */
    public void setEarthshineColor(Color earthshineColor) {
        if (earthshineColor == null) {
            return;
        }
        Color old = this.earthshineColor;
        this.earthshineColor = earthshineColor;
        firePropertyChange("earthshineColor", old, earthshineColor);
        repaint();
    }

    /**
     * Folgt die Komponente dem FTheme?
     *
     * @return wahr, wenn sie dem Thema folgt
     */
    public boolean isThemeAware() {
        return themeAware;
    }

    /**
     * Laesst die Komponente dem FTheme folgen. Die Farben des Mondes selbst
     * bleiben davon unberuehrt - ein Mond in Themenfarben waere kein Mond.
     *
     * @param themeAware wahr fuer Themenbindung
     */
    public void setThemeAware(boolean themeAware) {
        boolean old = this.themeAware;
        this.themeAware = themeAware;
        firePropertyChange("themeAware", old, themeAware);
        if (themeAware) {
            applyTheme(FTheme.getInstance());
        }
        repaint();
    }
}
