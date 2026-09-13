package com.dan.fmoon;

import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.render.FMoonRenderer;
import com.dan.fstyle.FColors;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import javax.swing.JPanel;

/**
 * Ein Monatskalender, in dem jede Zelle ihren eigenen Mond traegt.
 *
 * <p>Die Monde sind keine Sinnbilder aus einem Zeichensatz, sondern dieselben
 * gerechneten Kugeln wie in {@link FMoonGlobe}, nur klein. Dadurch stimmt in
 * jeder Zelle die Breite der Sichel, ihre Neigung und die Zeichnung der
 * Oberflaeche - und man sieht im Monatsbild, wie die Sichel im Lauf des
 * Monats kippt.</p>
 *
 * @author Claude fuer Daniel
 */
public class FMoonCalendar extends JPanel implements FTheme.ThemeListener {

    private static final long serialVersionUID = 1L;

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    private int year;
    private int month;
    private FGeoLocation location = FGeoLocation.TIMMENDORFER_STRAND;
    private FMoonMonth data;

    private int selectedDay = -1;
    private int hoverDay = -1;

    private boolean showRiseSet = true;
    private boolean showIllumination = true;
    private boolean showEvents = true;
    private boolean showEarthshine = true;
    private boolean themeAware = true;

    private Color sunlitColor = new Color(236, 229, 211);
    private Color earthshineColor = new Color(74, 96, 116);
    private Color gridColor;
    private Color textColor;
    private Color mutedColor;
    private Color accentColor;
    private Color selectionColor;

    private final List<FMoonCalendarListener> listeners =
            new ArrayList<FMoonCalendarListener>();

    private final Rectangle[] cellBounds = new Rectangle[31];

    /**
     * Legt einen Kalender fuer den laufenden Monat an.
     */
    public FMoonCalendar() {
        Calendar cal = new GregorianCalendar();
        this.year = cal.get(Calendar.YEAR);
        this.month = cal.get(Calendar.MONTH) + 1;
        setOpaque(true);
        setPreferredSize(new Dimension(700, 560));
        applyTheme(FTheme.getInstance());
        installMouse();
    }

    private void installMouse() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int d = dayAt(e.getX(), e.getY());
                if (d != hoverDay) {
                    hoverDay = d;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverDay != -1) {
                    hoverDay = -1;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int d = dayAt(e.getX(), e.getY());
                if (d > 0) {
                    setSelectedDay(d);
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private int dayAt(int x, int y) {
        for (int i = 0; i < cellBounds.length; i++) {
            if (cellBounds[i] != null && cellBounds[i].contains(x, y)) {
                return i + 1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Lebenszyklus und Thema
    // ------------------------------------------------------------------

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
        gridColor = FColors.withAlpha(theme.getText(), 38);
        textColor = theme.getText();
        mutedColor = theme.getTextMuted();
        accentColor = theme.getAccent();
        selectionColor = FColors.withAlpha(theme.getPrimary(), 60);
    }

    // ------------------------------------------------------------------
    // Daten
    // ------------------------------------------------------------------

    /**
     * Die durchgerechneten Angaben des angezeigten Monats. Die Rechnung
     * geschieht beim ersten Zugriff und wird bis zur naechsten Aenderung
     * von Monat oder Ort festgehalten.
     *
     * @return Monatsangaben
     */
    public FMoonMonth getMonthData() {
        if (data == null || data.getYear() != year || data.getMonth() != month
                || data.getLocation() != location) {
            data = new FMoonMonth(year, month, location);
        }
        return data;
    }

    private void invalidate0() {
        data = null;
        repaint();
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
        FMoonMonth m = getMonthData();

        // Kopfzeile
        g2.setFont(theme.getFont(FFontRole.HEADING));
        FontMetrics fmHead = g2.getFontMetrics();
        String titel = m.monthName() + " " + m.getYear();
        g2.setColor(textColor);
        int headY = 8 + fmHead.getAscent();
        g2.drawString(titel, 14, headY);

        g2.setFont(theme.getFont(FFontRole.CAPTION));
        FontMetrics fmCap = g2.getFontMetrics();
        String ort = location.getName();
        g2.setColor(mutedColor);
        g2.drawString(ort, w - 14 - fmCap.stringWidth(ort), headY);

        int top = headY + 10;

        // Wochentagsleiste
        int left = 8;
        int right = w - 8;
        int cols = 7;
        double cellW = (right - left) / (double) cols;
        g2.setFont(theme.getFont(FFontRole.CAPTION));
        fmCap = g2.getFontMetrics();
        for (int c = 0; c < cols; c++) {
            String t = WEEKDAYS[c];
            int cx = (int) Math.round(left + c * cellW + cellW / 2.0);
            g2.setColor(c >= 5 ? accentColor : mutedColor);
            g2.drawString(t, cx - fmCap.stringWidth(t) / 2, top + fmCap.getAscent());
        }
        top += fmCap.getHeight() + 4;

        int firstCol = m.firstWeekday() - 1;
        int length = m.getLength();
        int rows = (int) Math.ceil((firstCol + length) / 7.0);
        double cellH = (h - top - 8) / (double) rows;

        for (int i = 0; i < cellBounds.length; i++) {
            cellBounds[i] = null;
        }

        SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
        hhmm.setTimeZone(location.getTimeZone());

        for (int d = 1; d <= length; d++) {
            int index = firstCol + d - 1;
            int col = index % 7;
            int row = index / 7;
            int x = (int) Math.round(left + col * cellW);
            int y = (int) Math.round(top + row * cellH);
            int cw = (int) Math.round(left + (col + 1) * cellW) - x;
            int ch = (int) Math.round(top + (row + 1) * cellH) - y;
            cellBounds[d - 1] = new Rectangle(x, y, cw, ch);
            paintCell(g2, theme, m.getDay(d), x, y, cw, ch, col >= 5, hhmm);
        }

        // Raster zum Schluss, damit es ueber den Zellen liegt
        g2.setColor(gridColor);
        for (int r = 0; r <= rows; r++) {
            int y = (int) Math.round(top + r * cellH);
            g2.drawLine(left, y, right, y);
        }
        for (int c = 0; c <= cols; c++) {
            int x = (int) Math.round(left + c * cellW);
            g2.drawLine(x, top, x, (int) Math.round(top + rows * cellH));
        }

        g2.dispose();
    }

    private void paintCell(Graphics2D g2, FTheme theme, FMoonDay day,
            int x, int y, int w, int h, boolean weekend, SimpleDateFormat hhmm) {

        if (day.dayOfMonth == selectedDay) {
            g2.setColor(selectionColor);
            g2.fillRect(x + 1, y + 1, w - 1, h - 1);
        } else if (day.dayOfMonth == hoverDay) {
            g2.setColor(FColors.withAlpha(theme.getPrimary(), 26));
            g2.fillRect(x + 1, y + 1, w - 1, h - 1);
        }

        // Mond. Unter der Scheibe muss Platz fuer ZWEI Textzeilen bleiben:
        // die Auf- und Untergangszeiten und darueber gegebenenfalls ein
        // Ereignis. Wird hier nur eine Zeile eingeplant, schiebt sich an
        // Vollmondtagen die Beschriftung ueber die Scheibe.
        int untenNoetig = 4;
        if (showRiseSet) {
            untenNoetig += 14;
        }
        if (showEvents) {
            untenNoetig += 14;
        }
        int moonSize = Math.min(w - 14, h - 16 - untenNoetig);
        if (moonSize > 6) {
            BufferedImage img = FMoonRenderer.render(moonSize,
                    day.ephemeris.phase.phaseAngle,
                    day.ephemeris.phase.brightLimbAngle,
                    day.ephemeris.libration.longitude,
                    day.ephemeris.libration.latitude,
                    day.ephemeris.libration.axisPositionAngle,
                    showEarthshine
                            ? 0.05 * (1.0 - day.illuminatedFraction())
                            : 0.0,
                    sunlitColor, earthshineColor);
            g2.drawImage(img, x + (w - moonSize) / 2, y + 16, null);
        }

        // Tageszahl
        Font small = theme.getFont(FFontRole.CAPTION);
        g2.setFont(small);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(weekend ? accentColor : textColor);
        g2.drawString(String.valueOf(day.dayOfMonth), x + 5, y + 3 + fm.getAscent());

        // Beleuchtung rechts oben
        if (showIllumination) {
            String k = Math.round(day.illuminatedFraction() * 100.0) + "%";
            g2.setColor(mutedColor);
            g2.drawString(k, x + w - 5 - fm.stringWidth(k), y + 3 + fm.getAscent());
        }

        int textY = y + h - 4;

        // Auf- und Untergang unten
        if (showRiseSet) {
            StringBuilder sb = new StringBuilder();
            if (day.moonriseMillis != Long.MIN_VALUE) {
                sb.append("↑").append(hhmm.format(new Date(day.moonriseMillis)));
            }
            if (day.moonsetMillis != Long.MIN_VALUE) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("↓").append(hhmm.format(new Date(day.moonsetMillis)));
            }
            if (sb.length() > 0) {
                String t = sb.toString();
                g2.setColor(mutedColor);
                g2.drawString(t, x + (w - fm.stringWidth(t)) / 2, textY);
                textY -= fm.getHeight();
            }
        }

        // Ereignisse
        if (showEvents) {
            String ev = null;
            if (day.hasPhase()) {
                switch (day.phaseType) {
                    case FMoonPhaseTimes.NEW_MOON:
                        ev = "Neumond";
                        break;
                    case FMoonPhaseTimes.FIRST_QUARTER:
                        ev = "erstes Viertel";
                        break;
                    case FMoonPhaseTimes.FULL_MOON:
                        ev = "Vollmond";
                        break;
                    default:
                        ev = "letztes Viertel";
                        break;
                }
            } else if (day.perigee) {
                ev = "Erdnähe";
            } else if (day.apogee) {
                ev = "Erdferne";
            }
            if (ev != null) {
                g2.setColor(day.hasPhase() ? accentColor : mutedColor);
                g2.drawString(ev, x + (w - fm.stringWidth(ev)) / 2, textY);
            }
        }
    }

    // ------------------------------------------------------------------
    // Ereignisse
    // ------------------------------------------------------------------

    /**
     * Meldet einen Zuhoerer an.
     *
     * @param l Zuhoerer
     */
    public void addFMoonCalendarListener(FMoonCalendarListener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    /**
     * Meldet einen Zuhoerer ab.
     *
     * @param l Zuhoerer
     */
    public void removeFMoonCalendarListener(FMoonCalendarListener l) {
        listeners.remove(l);
    }

    private void fireDaySelected(FMoonDay day) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).daySelected(this, day);
        }
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Angezeigtes Jahr.
     *
     * @return Jahr
     */
    public int getYear() {
        return year;
    }

    /**
     * Setzt das angezeigte Jahr.
     *
     * @param year Jahr
     */
    public void setYear(int year) {
        int old = this.year;
        this.year = year;
        firePropertyChange("year", old, year);
        invalidate0();
    }

    /**
     * Angezeigter Monat, 1 bis 12.
     *
     * @return Monat
     */
    public int getMonth() {
        return month;
    }

    /**
     * Setzt den angezeigten Monat.
     *
     * @param month Monat, 1 bis 12
     */
    public void setMonth(int month) {
        int old = this.month;
        this.month = Math.max(1, Math.min(12, month));
        firePropertyChange("month", old, this.month);
        invalidate0();
    }

    /**
     * Blaettert um einen Monat weiter oder zurueck.
     *
     * @param delta Anzahl Monate, auch negativ
     */
    public void shiftMonths(int delta) {
        int total = year * 12 + (month - 1) + delta;
        setYear(total / 12);
        setMonth(total % 12 + 1);
    }

    /**
     * Beobachtungsort.
     *
     * @return Ort
     */
    public FGeoLocation getObserverLocation() {
        return location;
    }

    /**
     * Setzt den Beobachtungsort. Er bestimmt Zeitzone sowie Auf- und
     * Untergangszeiten, nicht aber die Phase.
     *
     * @param observerLocation Ort, nicht null
     */
    public void setObserverLocation(FGeoLocation observerLocation) {
        if (observerLocation == null) {
            return;
        }
        FGeoLocation old = this.location;
        this.location = observerLocation;
        firePropertyChange("observerLocation", old, observerLocation);
        invalidate0();
    }

    /**
     * Der ausgewaehlte Tag, oder -1.
     *
     * @return Tag im Monat
     */
    public int getSelectedDay() {
        return selectedDay;
    }

    /**
     * Waehlt einen Tag aus.
     *
     * @param selectedDay Tag im Monat, oder -1 fuer keine Auswahl
     */
    public void setSelectedDay(int selectedDay) {
        int old = this.selectedDay;
        this.selectedDay = selectedDay;
        firePropertyChange("selectedDay", old, selectedDay);
        repaint();
        if (selectedDay >= 1 && selectedDay <= getMonthData().getLength()) {
            fireDaySelected(getMonthData().getDay(selectedDay));
        }
    }

    /**
     * Werden Auf- und Untergangszeiten gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowRiseSet() {
        return showRiseSet;
    }

    /**
     * Schaltet die Auf- und Untergangszeiten.
     *
     * @param showRiseSet wahr fuer Zeiten
     */
    public void setShowRiseSet(boolean showRiseSet) {
        boolean old = this.showRiseSet;
        this.showRiseSet = showRiseSet;
        firePropertyChange("showRiseSet", old, showRiseSet);
        repaint();
    }

    /**
     * Wird der beleuchtete Anteil gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowIllumination() {
        return showIllumination;
    }

    /**
     * Schaltet die Prozentangabe des beleuchteten Anteils.
     *
     * @param showIllumination wahr fuer die Angabe
     */
    public void setShowIllumination(boolean showIllumination) {
        boolean old = this.showIllumination;
        this.showIllumination = showIllumination;
        firePropertyChange("showIllumination", old, showIllumination);
        repaint();
    }

    /**
     * Werden Phasen und Apsiden beschriftet?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowEvents() {
        return showEvents;
    }

    /**
     * Schaltet die Beschriftung von Phasen, Erdnaehe und Erdferne.
     *
     * @param showEvents wahr fuer Beschriftung
     */
    public void setShowEvents(boolean showEvents) {
        boolean old = this.showEvents;
        this.showEvents = showEvents;
        firePropertyChange("showEvents", old, showEvents);
        repaint();
    }

    /**
     * Wird der Erdschein gezeichnet?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowEarthshine() {
        return showEarthshine;
    }

    /**
     * Schaltet den Erdschein in den Zellen.
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
