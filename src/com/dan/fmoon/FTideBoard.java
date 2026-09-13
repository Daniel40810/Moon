package com.dan.fmoon;

import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FRiseSet;
import com.dan.fmoon.tide.FHarbourTide;
import com.dan.fmoon.tide.FTideMoment;
import com.dan.fmoon.tide.FTideStation;
import com.dan.fstyle.FColors;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import javax.swing.JPanel;

/**
 * Die Ableseanzeige: Hoch- und Niedrigwasser eines Tages, und darueber die
 * Kette, aus der sie stammen.
 *
 * <p>Das Besondere an der Hafenzeit ist, dass man ihr beim Rechnen zusehen
 * kann. Deshalb steht oben nicht nur das Ergebnis, sondern der ganze Weg:
 * Mond im Sueden um soundsoviel Uhr, plus die Hafenzeit dieses Ortes, ergibt
 * Hochwasser. Wer das einmal gelesen hat, kann es am Strand im Kopf
 * nachrechnen.</p>
 *
 * <p>An gezeitenarmen Stationen zeigt die Anzeige <b>keine</b> Zeiten,
 * sondern sagt, warum es dort keine gibt. Eine Hochwasserzeit fuer die
 * Lubecker Bucht waere eine Zahl ohne Aussage.</p>
 *
 * @author Claude fuer Daniel
 */
public class FTideBoard extends JPanel implements FTheme.ThemeListener {

    private static final long serialVersionUID = 1L;

    private FTideStation station = FTideStation.BUESUM;
    private long timeMillis = System.currentTimeMillis();

    private boolean showChain = true;
    private boolean showNow = true;
    private boolean themeAware = true;

    private Color textColor;
    private Color mutedColor;
    private Color accentColor;
    private Color highColor = new Color(84, 179, 187);
    private Color lowColor = new Color(150, 160, 172);
    private Color warnColor = new Color(210, 119, 92);

    private List<FTideMoment> tagesEreignisse;
    private List<FTideMoment> umgebung;
    private long ereignisseFuer = Long.MIN_VALUE;
    private FTideStation ereignisseVon;

    /**
     * Legt die Anzeige an.
     */
    public FTideBoard() {
        setOpaque(true);
        setPreferredSize(new Dimension(420, 320));
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
        accentColor = theme.getAccent();
    }

    // ------------------------------------------------------------------
    // Daten
    // ------------------------------------------------------------------

    /**
     * Die Gezeitenereignisse des angezeigten Tages.
     *
     * @return Ereignisse in zeitlicher Reihenfolge
     */
    public List<FTideMoment> getDayEvents() {
        long tag = tagesBeginn();
        berechne(tag);
        return tagesEreignisse;
    }

    /**
     * Die Ereignisse des Tages samt einer Tide Rand nach beiden Seiten.
     *
     * <p>Der Rand ist noetig, weil der Wasserstand zwischen zwei Ereignissen
     * interpoliert wird: Um sechs Uhr frueh liegt das vorausgehende Ereignis
     * oft noch im Vortag. Beide Listen stammen aus EINER Rechnung - der teure
     * Teil ist der Meridiandurchgang, und den zweimal zu suchen waere
     * doppelte Arbeit fuer dieselbe Auskunft.</p>
     */
    private List<FTideMoment> getUmgebung() {
        berechne(tagesBeginn());
        return umgebung;
    }

    private void berechne(long tag) {
        if (tagesEreignisse != null && tag == ereignisseFuer && station == ereignisseVon) {
            return;
        }
        long rand = (long) (FHarbourTide.TIDE_PERIOD_HOURS * 2.0 * 3600000.0);
        umgebung = FHarbourTide.events(station, tag - rand, tag + 86400000L + rand);
        tagesEreignisse = new ArrayList<FTideMoment>();
        for (int i = 0; i < umgebung.size(); i++) {
            FTideMoment m = umgebung.get(i);
            if (m.getTimeMillis() >= tag && m.getTimeMillis() <= tag + 86400000L) {
                tagesEreignisse.add(m);
            }
        }
        ereignisseFuer = tag;
        ereignisseVon = station;
    }

    private long tagesBeginn() {
        Calendar c = new GregorianCalendar(station.getLocation().getTimeZone());
        c.setTimeInMillis(timeMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void invalidateEvents() {
        ereignisseFuer = Long.MIN_VALUE;
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
        SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
        hhmm.setTimeZone(station.getLocation().getTimeZone());

        int x = 14;
        int y = 10;

        // Kopf
        g2.setFont(theme.getFont(FFontRole.HEADING));
        FontMetrics fm = g2.getFontMetrics();
        y += fm.getAscent();
        g2.setColor(textColor);
        g2.drawString(station.getName(), x, y);

        g2.setFont(theme.getFont(FFontRole.CAPTION));
        FontMetrics fc = g2.getFontMetrics();
        String gew = station.getWaterBody().getDisplayName();
        g2.setColor(mutedColor);
        g2.drawString(gew, w - 14 - fc.stringWidth(gew), y);

        y += 10;

        if (!station.isTidalRangeUseful()) {
            paintNoTide(g2, theme, x, y, w, h);
            g2.dispose();
            return;
        }

        // Die Kette
        if (showChain) {
            y = paintChain(g2, theme, x, y, w, hhmm);
        }

        // Ereignisse des Tages
        y += 8;
        g2.setFont(theme.getFont(FFontRole.CAPTION));
        fc = g2.getFontMetrics();
        y += fc.getAscent();
        g2.setColor(mutedColor);
        g2.drawString("HEUTE", x, y);
        g2.setColor(FColors.withAlpha(textColor, 40));
        g2.drawLine(x + 44, y - 4, w - 14, y - 4);
        y += 6;

        List<FTideMoment> ev = getDayEvents();
        g2.setFont(theme.getFont(FFontRole.BODY));
        FontMetrics fb = g2.getFontMetrics();
        for (int i = 0; i < ev.size(); i++) {
            FTideMoment m = ev.get(i);
            y += fb.getHeight();
            if (y > h - 34) {
                break;
            }
            g2.setColor(m.isHighWater() ? highColor : lowColor);
            g2.drawString(m.shortLabel(), x, y);
            g2.setColor(textColor);
            String zeit = hhmm.format(new Date(m.getTimeMillis()));
            g2.drawString(zeit, x + 40, y);
            g2.setColor(mutedColor);
            String hoehe = String.format("%+.2f m", m.getLevelMeters());
            g2.drawString(hoehe, x + 110, y);
            String hub = String.format("Hub %.2f m", m.getRangeMeters());
            g2.drawString(hub, x + 196, y);
        }

        // Jetztstand
        if (showNow) {
            y += fb.getHeight() + 6;
            if (y < h - 22) {
                List<FTideMoment> um = getUmgebung();
                double stand = FHarbourTide.levelAt(station, timeMillis, um);
                boolean steigt = FHarbourTide.isRising(station, timeMillis, um);
                g2.setColor(accentColor);
                String jetzt = String.format("%s  %+.2f m  ·  %s",
                        hhmm.format(new Date(timeMillis)), stand,
                        steigt ? "Flut, das Wasser steigt" : "Ebbe, das Wasser fällt");
                g2.drawString(jetzt, x, y);
            }
        }

        // Vorbehalt
        paintQualifier(g2, theme, x, h - 8, w);
        g2.dispose();
    }

    private int paintChain(Graphics2D g2, FTheme theme, int x, int y, int w,
            SimpleDateFormat hhmm) {
        // Gezeigt wird der Durchgang, aus dem das NAECHSTE Hochwasser folgt -
        // nicht einfach der naechste Durchgang. Beides ist nicht dasselbe:
        // Weil die Hafenzeit elf bis zwoelf Stunden betraegt, gehoert das
        // naechste Hochwasser fast immer zu einem Durchgang, der schon
        // vorueber ist. Deshalb wird vom Ergebnis her aufgerollt; FTideMoment
        // fuehrt seinen Durchgang mit.
        FTideMoment hw = null;
        List<FTideMoment> um = getUmgebung();
        for (int i = 0; i < um.size(); i++) {
            FTideMoment m = um.get(i);
            if (m.isHighWater() && m.getTimeMillis() > timeMillis) {
                hw = m;
                break;
            }
        }
        if (hw == null) {
            return y;
        }

        g2.setFont(theme.getFont(FFontRole.CAPTION));
        FontMetrics fc = g2.getFontMetrics();
        y += fc.getAscent() + 4;
        g2.setColor(mutedColor);
        g2.drawString("SO ENTSTEHT DIE VORHERSAGE", x, y);
        g2.setColor(FColors.withAlpha(textColor, 40));
        g2.drawLine(x + 190, y - 4, w - 14, y - 4);

        g2.setFont(theme.getFont(FFontRole.BODY));
        FontMetrics fb = g2.getFontMetrics();
        int rechts = w - 14;

        y += fb.getHeight() + 2;
        g2.setColor(textColor);
        String wo = hw.isUpperTransit()
                ? "Mond im S\u00fcden" : "Mond unter dem Horizont";
        g2.drawString(wo, x, y);
        g2.setColor(accentColor);
        String t1 = hhmm.format(new Date(hw.getMoonTransitMillis()));
        g2.drawString(t1, rechts - fb.stringWidth(t1), y);

        y += fb.getHeight();
        g2.setColor(mutedColor);
        int stunden = (int) station.getHighWaterIntervalHours();
        int minuten = (int) Math.round((station.getHighWaterIntervalHours() - stunden) * 60.0);
        g2.drawString(String.format("+ Hafenzeit %d h %02d min", stunden, minuten), x + 10, y);

        y += fb.getHeight();
        g2.setColor(FColors.withAlpha(textColor, 60));
        g2.drawLine(x, y - fb.getAscent() + 3, rechts, y - fb.getAscent() + 3);
        y += 2;
        g2.setColor(highColor);
        g2.drawString("Hochwasser", x, y);
        String t2 = hhmm.format(new Date(hw.getTimeMillis()));
        g2.drawString(t2, rechts - fb.stringWidth(t2), y);

        return y + 4;
    }

    private void paintNoTide(Graphics2D g2, FTheme theme, int x, int y, int w, int h) {
        g2.setFont(theme.getFont(FFontRole.TITLE));
        FontMetrics ft = g2.getFontMetrics();
        y += ft.getAscent() + 10;
        g2.setColor(warnColor);
        g2.drawString("Keine Gezeitenvorhersage", x, y);

        g2.setFont(theme.getFont(FFontRole.BODY));
        FontMetrics fb = g2.getFontMetrics();
        String[] zeilen = {
            "Die Ostsee ist praktisch tidenfrei. Der astronomische",
            "Tidenhub liegt hier bei etwa "
            + String.format("%.0f", station.getMeanRangeMeters() * 100.0)
            + " Zentimetern und",
            "verschwindet unter dem, was Wetter macht: Ein",
            "kräftiger Ost- oder Weststurm verschiebt den",
            "Wasserstand um mehr als einen Meter, also um das",
            "Zehnfache. Dazu kommen die Eigenschwingungen des",
            "Beckens mit rund 26 Stunden Periode, die mit dem",
            "Mond nichts zu tun haben.",
            "",
            "Eine Hochwasserzeit anzugeben wäre hier eine Zahl",
            "ohne Aussage. Wählen Sie eine Nordseestation."
        };
        for (int i = 0; i < zeilen.length; i++) {
            y += fb.getHeight();
            if (y > h - 14) {
                break;
            }
            g2.setColor(zeilen[i].isEmpty() ? mutedColor : mutedColor);
            g2.drawString(zeilen[i], x, y);
        }
    }

    /**
     * Setzt den Vorbehalt unten an den Rand und bricht ihn um, statt ihn
     * abzuschneiden. Ein halb sichtbarer Vorbehalt ist kein Vorbehalt.
     */
    private void paintQualifier(Graphics2D g2, FTheme theme, int x, int unten, int w) {
        String q = station.qualifier();
        if (q == null || q.isEmpty()) {
            return;
        }
        g2.setFont(theme.getFont(FFontRole.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        int breite = w - x - 14;
        List<String> zeilen = umbrechen(q, fm, breite);
        int y = unten - (zeilen.size() - 1) * fm.getHeight();
        g2.setColor(FColors.withAlpha(mutedColor, 190));
        for (int i = 0; i < zeilen.size(); i++) {
            g2.drawString(zeilen.get(i), x, y + i * fm.getHeight());
        }
    }

    private static List<String> umbrechen(String text, FontMetrics fm, int breite) {
        List<String> out = new ArrayList<String>();
        String[] worte = text.split(" ");
        StringBuilder zeile = new StringBuilder();
        for (int i = 0; i < worte.length; i++) {
            String probe = (zeile.length() == 0) ? worte[i] : zeile + " " + worte[i];
            if (fm.stringWidth(probe) > breite && zeile.length() > 0) {
                out.add(zeile.toString());
                zeile = new StringBuilder(worte[i]);
            } else {
                zeile = new StringBuilder(probe);
            }
        }
        if (zeile.length() > 0) {
            out.add(zeile.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Die angezeigte Station.
     *
     * @return Station
     */
    public FTideStation getStation() {
        return station;
    }

    /**
     * Setzt die angezeigte Station.
     *
     * @param station Station, nicht null
     */
    public void setStation(FTideStation station) {
        if (station == null) {
            return;
        }
        FTideStation old = this.station;
        this.station = station;
        invalidateEvents();
        firePropertyChange("station", old, station);
        repaint();
    }

    /**
     * Der angezeigte Zeitpunkt.
     *
     * @return Millisekunden seit 1970-01-01
     */
    public long getTimeMillis() {
        return timeMillis;
    }

    /**
     * Setzt den angezeigten Zeitpunkt.
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
     * Wird die Rechenkette Mond - Hafenzeit - Hochwasser gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowChain() {
        return showChain;
    }

    /**
     * Schaltet die Rechenkette.
     *
     * @param showChain wahr fuer die Kette
     */
    public void setShowChain(boolean showChain) {
        boolean old = this.showChain;
        this.showChain = showChain;
        firePropertyChange("showChain", old, showChain);
        repaint();
    }

    /**
     * Wird der augenblickliche Wasserstand gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowNow() {
        return showNow;
    }

    /**
     * Schaltet die Zeile mit dem augenblicklichen Stand.
     *
     * @param showNow wahr fuer die Zeile
     */
    public void setShowNow(boolean showNow) {
        boolean old = this.showNow;
        this.showNow = showNow;
        firePropertyChange("showNow", old, showNow);
        repaint();
    }

    /**
     * Farbe fuer Hochwasser.
     *
     * @return Farbe
     */
    public Color getHighColor() {
        return highColor;
    }

    /**
     * Setzt die Farbe fuer Hochwasser.
     *
     * @param highColor Farbe, nicht null
     */
    public void setHighColor(Color highColor) {
        if (highColor == null) {
            return;
        }
        Color old = this.highColor;
        this.highColor = highColor;
        firePropertyChange("highColor", old, highColor);
        repaint();
    }

    /**
     * Farbe fuer Niedrigwasser.
     *
     * @return Farbe
     */
    public Color getLowColor() {
        return lowColor;
    }

    /**
     * Setzt die Farbe fuer Niedrigwasser.
     *
     * @param lowColor Farbe, nicht null
     */
    public void setLowColor(Color lowColor) {
        if (lowColor == null) {
            return;
        }
        Color old = this.lowColor;
        this.lowColor = lowColor;
        firePropertyChange("lowColor", old, lowColor);
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
