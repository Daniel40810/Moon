package com.dan.fmoon;

import com.dan.fmoon.astro.FAngle;
import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FMoonPosition;
import com.dan.fmoon.astro.FSun;
import com.dan.fmoon.render.FMoonRenderer;
import com.dan.fmoon.tide.FHarbourTide;
import com.dan.fmoon.tide.FTideMoment;
import com.dan.fmoon.tide.FTideStation;
import com.dan.fstyle.FColors;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JPanel;

/**
 * Die Wasserstandskurve der Hafenzeitvorhersage, mit einem Band aus
 * gerechneten Monden darueber.
 *
 * <h3>Warum eine eigene Kurve</h3>
 *
 * <p>Die vorhandene Komponente {@link com.dan.ftidalgauge.FTidalGauge} wird in
 * FMoon weiter benutzt - ihre Pegelszene liest den Wasserstand aus dem Modell
 * und zeigt ihn richtig an. Ihre <b>Kurve</b> dagegen wird aus den
 * harmonischen Partialtiden gezeichnet, und die hat diese Vorhersage nicht:
 * Sie rechnet nach der Hafenzeit. Im Kopf der Anzeige stand deshalb
 * "0 Partialtiden", und die Kurve blieb leer.</p>
 *
 * <p>Der Ausweg waere gewesen, die Hafenzeit in aequivalente Partialtiden zu
 * uebersetzen. Das ist rechnerisch moeglich, schafft aber zwei Wahrheiten:
 * eine Kurve aus umgerechneten Partialtiden und Hochwasserzeiten aus der
 * Hafenzeit, die um Minuten auseinanderlaufen koennen. Deshalb zeichnet diese
 * Komponente die Kurve unmittelbar aus derselben Rechnung, aus der auch die
 * Zeiten stammen.</p>
 *
 * <h3>Das Mondband</h3>
 *
 * <p>Ueber der Kurve laeuft bei laengeren Zeitraeumen ein Band mit den
 * gerechneten Monden. Damit wird der Zusammenhang sichtbar, um den es hier
 * geht: Die hohen Tiden folgen dem Voll- und dem Neumond, die niedrigen den
 * Vierteln - mit ein bis zwei Tagen Verspaetung.</p>
 *
 * @author Claude fuer Daniel
 */
public class FTideCurve extends JPanel implements FTheme.ThemeListener {

    private static final long serialVersionUID = 1L;

    private FTideStation station = FTideStation.BUESUM;
    private long timeMillis = System.currentTimeMillis();
    private int spanHours = 30;
    private double offsetHours = -6.0;

    private boolean showMoonBand = true;
    private boolean showEvents = true;
    private boolean showNowLine = true;
    private boolean showGrid = true;
    private boolean themeAware = true;

    private Color curveColor = new Color(84, 179, 187);
    private Color fillColor = new Color(84, 179, 187, 46);
    private Color textColor;
    private Color mutedColor;
    private Color gridColor;
    private Color nowColor;

    private final Color moonLit = new Color(236, 229, 211);
    private final Color moonShine = new Color(60, 76, 92);

    /**
     * Legt die Kurve an.
     */
    public FTideCurve() {
        setOpaque(true);
        setPreferredSize(new Dimension(640, 360));
        applyTheme(FTheme.getInstance());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (themeAware) {
            FTheme.getInstance().addThemeListener(this);
        }
    }

    @Override
    public void removeNotify() {
        if (themeAware) {
            FTheme.getInstance().removeThemeListener(this);
        }
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
        textColor = theme.getText();
        mutedColor = theme.getTextMuted();
        gridColor = FColors.withAlpha(theme.getText(), 30);
        nowColor = theme.getAccent();
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

        int w = getWidth();
        int h = getHeight();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, w, h);

        FTheme theme = FTheme.getInstance();
        g2.setFont(theme.getFont(FFontRole.CAPTION));
        FontMetrics fc = g2.getFontMetrics();

        long von = timeMillis + (long) (offsetHours * 3600000.0);
        long bis = von + (long) (spanHours * 3600000.0);
        double tage = spanHours / 24.0;

        int bandHoehe = (showMoonBand && tage >= 4.0) ? 40 : 0;
        int links = 52;
        int rechts = w - 14;
        // Die Kopfzeile steht ZWISCHEN Mondband und Kurve. Stand sie im Band,
        // lag sie ueber den Monden.
        int oben = (bandHoehe > 0) ? 10 + bandHoehe : 24;
        int unten = h - 26;
        if (rechts <= links + 20 || unten <= oben + 30) {
            g2.dispose();
            return;
        }

        // Die Ereignisse werden EINMAL geholt und fuer alles weitere benutzt.
        // Sie einzeln je Bildpunkt zu holen hat frueher eine Kurvenbreite auf
        // zweieinhalb Sekunden gebracht - jeder Punkt suchte sich dieselben
        // acht Meridiandurchgaenge neu. Das Fenster reicht vorne und hinten
        // eine Tide ueber den Rand hinaus, sonst fehlt an den Raendern der
        // Nachbar zum Interpolieren.
        long rand = (long) (FHarbourTide.TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        List<FTideMoment> umgebung = FHarbourTide.events(station, von - rand, bis + rand);

        // Wertebereich und Marken brauchen nur das, was wirklich zu sehen ist.
        List<FTideMoment> ereignisse = new ArrayList<FTideMoment>();
        for (int i = 0; i < umgebung.size(); i++) {
            FTideMoment m = umgebung.get(i);
            if (m.getTimeMillis() >= von && m.getTimeMillis() <= bis) {
                ereignisse.add(m);
            }
        }
        double maxHub = station.getSpringRangeMeters();
        for (int i = 0; i < ereignisse.size(); i++) {
            maxHub = Math.max(maxHub, ereignisse.get(i).getRangeMeters());
        }
        double yMax = maxHub / 2.0 * 1.18;
        if (yMax < 0.1) {
            yMax = 0.1;
        }

        // Gitter und Beschriftung
        if (showGrid) {
            int stufen = (yMax > 1.5) ? 1 : ((yMax > 0.5) ? 2 : 4);
            double schritt = 1.0 / stufen;
            for (double v = -Math.floor(yMax / schritt) * schritt;
                    v <= yMax + 1e-9; v += schritt) {
                int y = yPos(v, yMax, oben, unten);
                g2.setColor(Math.abs(v) < 1e-9
                        ? FColors.withAlpha(textColor, 70) : gridColor);
                g2.drawLine(links, y, rechts, y);
                g2.setColor(mutedColor);
                String t = (Math.abs(v) < 1e-9) ? "MW" : String.format("%+.1f", v);
                g2.drawString(t, links - 8 - fc.stringWidth(t), y + 4);
            }
        }

        // Zeitachse
        SimpleDateFormat achse = new SimpleDateFormat(tage >= 4.0 ? "dd.MM." : "HH:mm");
        achse.setTimeZone(station.getLocation().getTimeZone());
        int marken = Math.max(2, Math.min(8, (int) Math.round(tage >= 4.0 ? tage / 4.0 : 6)));
        for (int i = 0; i <= marken; i++) {
            long t = von + (bis - von) * i / marken;
            int x = xPos(t, von, bis, links, rechts);
            g2.setColor(gridColor);
            g2.drawLine(x, oben, x, unten);
            String s = achse.format(new Date(t));
            g2.setColor(mutedColor);
            int tx = x - fc.stringWidth(s) / 2;
            tx = Math.max(links, Math.min(rechts - fc.stringWidth(s), tx));
            g2.drawString(s, tx, unten + 16);
        }

        // Kurve
        int schritte = Math.max(60, rechts - links);
        GeneralPath linie = new GeneralPath();
        GeneralPath flaeche = new GeneralPath();
        int nullY = yPos(0.0, yMax, oben, unten);
        for (int i = 0; i <= schritte; i++) {
            long t = von + (bis - von) * i / schritte;
            double lvl = FHarbourTide.levelAt(station, t, umgebung);
            int x = xPos(t, von, bis, links, rechts);
            int y = yPos(lvl, yMax, oben, unten);
            if (i == 0) {
                linie.moveTo(x, y);
                flaeche.moveTo(x, nullY);
                flaeche.lineTo(x, y);
            } else {
                linie.lineTo(x, y);
                flaeche.lineTo(x, y);
            }
            if (i == schritte) {
                flaeche.lineTo(x, nullY);
                flaeche.closePath();
            }
        }
        g2.setColor(fillColor);
        g2.fill(flaeche);
        g2.setColor(curveColor);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(linie);
        g2.setStroke(new BasicStroke(1f));

        // Hoch- und Niedrigwassermarken
        if (showEvents && tage <= 4.0) {
            SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
            hhmm.setTimeZone(station.getLocation().getTimeZone());
            for (int i = 0; i < ereignisse.size(); i++) {
                FTideMoment m = ereignisse.get(i);
                int x = xPos(m.getTimeMillis(), von, bis, links, rechts);
                int y = yPos(m.getLevelMeters(), yMax, oben, unten);
                g2.setColor(curveColor);
                g2.fillOval(x - 3, y - 3, 6, 6);
                String s = m.shortLabel() + " " + hhmm.format(new Date(m.getTimeMillis()));
                int tx = x - fc.stringWidth(s) / 2;
                tx = Math.max(links + 2, Math.min(rechts - fc.stringWidth(s) - 2, tx));
                int ty = m.isHighWater() ? y - 8 : y + 16;
                g2.setColor(textColor);
                g2.drawString(s, tx, ty);
            }
        }

        // Jetztlinie
        if (showNowLine && timeMillis >= von && timeMillis <= bis) {
            int x = xPos(timeMillis, von, bis, links, rechts);
            g2.setColor(FColors.withAlpha(nowColor, 160));
            g2.drawLine(x, oben, x, unten);
            double lvl = FHarbourTide.levelAt(station, timeMillis, umgebung);
            int y = yPos(lvl, yMax, oben, unten);
            g2.setColor(nowColor);
            g2.fillOval(x - 4, y - 4, 8, 8);
        }

        // Mondband
        if (bandHoehe > 0) {
            paintMoonBand(g2, von, bis, links, rechts, 6, bandHoehe - 8);
        }

        // Kopfzeile
        g2.setColor(mutedColor);
        String kopf = station.getName() + "  ·  Wasserstand über Mittelwasser";
        if (!station.isTidalRangeUseful()) {
            kopf = station.getName() + "  ·  gezeitenarm, nur die astronomische Tide";
        }
        g2.drawString(kopf, links, oben - 5);

        g2.dispose();
    }

    private void paintMoonBand(Graphics2D g2, long von, long bis,
            int links, int rechts, int y, int hoehe) {
        int groesse = Math.min(hoehe, 30);
        if (groesse < 10) {
            return;
        }
        int anzahl = Math.max(3, Math.min(14, (rechts - links) / (groesse + 16)));
        for (int i = 0; i < anzahl; i++) {
            long t = von + (bis - von) * i / (anzahl - 1);
            double jdUt = FAstroTime.julianDayFromMillis(t);
            double jde = jdUt + FAstroTime.deltaTDays(jdUt);
            FSun sun = FSun.at(jde);
            FMoonPosition moon = FMoonPosition.at(jde);
            double dLon = FAngle.norm360(moon.longitude - sun.apparentLongitude);
            double i2 = 180.0 - Math.abs(180.0 - dLon);
            double phaseWinkel = 180.0 - i2;
            double limb = (dLon < 180.0) ? 90.0 : 270.0;
            BufferedImage m = FMoonRenderer.render(groesse, phaseWinkel, limb,
                    0, 0, 0, 0.04, moonLit, moonShine);
            int x = xPos(t, von, bis, links, rechts) - groesse / 2;
            x = Math.max(links, Math.min(rechts - groesse, x));
            g2.drawImage(m, x, y, null);
        }
    }

    private static int xPos(long t, long von, long bis, int links, int rechts) {
        double f = (t - von) / (double) (bis - von);
        return (int) Math.round(links + f * (rechts - links));
    }

    private static int yPos(double v, double yMax, int oben, int unten) {
        double f = (yMax - v) / (2.0 * yMax);
        return (int) Math.round(oben + f * (unten - oben));
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Die dargestellte Station.
     *
     * @return Station
     */
    public FTideStation getStation() {
        return station;
    }

    /**
     * Setzt die dargestellte Station.
     *
     * @param station Station, nicht null
     */
    public void setStation(FTideStation station) {
        if (station == null) {
            return;
        }
        FTideStation old = this.station;
        this.station = station;
        firePropertyChange("station", old, station);
        repaint();
    }

    /**
     * Der Bezugszeitpunkt.
     *
     * @return Millisekunden seit 1970-01-01
     */
    public long getTimeMillis() {
        return timeMillis;
    }

    /**
     * Setzt den Bezugszeitpunkt.
     *
     * @param timeMillis Millisekunden seit 1970-01-01
     */
    public void setTimeMillis(long timeMillis) {
        long old = this.timeMillis;
        this.timeMillis = timeMillis;
        firePropertyChange("timeMillis", old, timeMillis);
        repaint();
    }

    /**
     * Die dargestellte Zeitspanne.
     *
     * @return Spanne in Stunden
     */
    public int getSpanHours() {
        return spanHours;
    }

    /**
     * Setzt die dargestellte Zeitspanne. Ab vier Tagen erscheint das Mondband,
     * und die Hoch- und Niedrigwassermarken verschwinden, weil sie dann zu
     * dicht liegen.
     *
     * @param spanHours Spanne in Stunden, mindestens 6
     */
    public void setSpanHours(int spanHours) {
        int old = this.spanHours;
        this.spanHours = Math.max(6, spanHours);
        firePropertyChange("spanHours", old, this.spanHours);
        repaint();
    }

    /**
     * Verschiebung des Fensteranfangs gegen den Bezugszeitpunkt.
     *
     * @return Verschiebung in Stunden
     */
    public double getOffsetHours() {
        return offsetHours;
    }

    /**
     * Setzt die Verschiebung des Fensteranfangs.
     *
     * @param offsetHours Verschiebung in Stunden, meist negativ
     */
    public void setOffsetHours(double offsetHours) {
        double old = this.offsetHours;
        this.offsetHours = offsetHours;
        firePropertyChange("offsetHours", old, offsetHours);
        repaint();
    }

    /**
     * Wird das Mondband gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowMoonBand() {
        return showMoonBand;
    }

    /**
     * Schaltet das Band mit den gerechneten Monden ueber der Kurve.
     *
     * @param showMoonBand wahr fuer das Band
     */
    public void setShowMoonBand(boolean showMoonBand) {
        boolean old = this.showMoonBand;
        this.showMoonBand = showMoonBand;
        firePropertyChange("showMoonBand", old, showMoonBand);
        repaint();
    }

    /**
     * Werden Hoch- und Niedrigwasser beschriftet?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowEvents() {
        return showEvents;
    }

    /**
     * Schaltet die Marken fuer Hoch- und Niedrigwasser.
     *
     * @param showEvents wahr fuer Marken
     */
    public void setShowEvents(boolean showEvents) {
        boolean old = this.showEvents;
        this.showEvents = showEvents;
        firePropertyChange("showEvents", old, showEvents);
        repaint();
    }

    /**
     * Wird die Jetztlinie gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowNowLine() {
        return showNowLine;
    }

    /**
     * Schaltet die senkrechte Linie am Bezugszeitpunkt.
     *
     * @param showNowLine wahr fuer die Linie
     */
    public void setShowNowLine(boolean showNowLine) {
        boolean old = this.showNowLine;
        this.showNowLine = showNowLine;
        firePropertyChange("showNowLine", old, showNowLine);
        repaint();
    }

    /**
     * Wird ein Gitter gezeichnet?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowGrid() {
        return showGrid;
    }

    /**
     * Schaltet das Gitter.
     *
     * @param showGrid wahr fuer das Gitter
     */
    public void setShowGrid(boolean showGrid) {
        boolean old = this.showGrid;
        this.showGrid = showGrid;
        firePropertyChange("showGrid", old, showGrid);
        repaint();
    }

    /**
     * Farbe der Kurve.
     *
     * @return Farbe
     */
    public Color getCurveColor() {
        return curveColor;
    }

    /**
     * Setzt die Farbe der Kurve.
     *
     * @param curveColor Farbe, nicht null
     */
    public void setCurveColor(Color curveColor) {
        if (curveColor == null) {
            return;
        }
        Color old = this.curveColor;
        this.curveColor = curveColor;
        this.fillColor = FColors.withAlpha(curveColor, 46);
        firePropertyChange("curveColor", old, curveColor);
        repaint();
    }

    /**
     * Folgt die Komponente dem FTheme?
     *
     * @return wahr, wenn ja
     */
    public boolean isThemeAware() {
        return themeAware;
    }

    /**
     * Laesst die Komponente dem FTheme folgen.
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
