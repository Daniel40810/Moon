package com.dan.fmoon;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.flabel.FLabel;
import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import com.dan.fmoon.render.FMoonRenderer;
import com.dan.fmoon.db.FMoonDao;
import com.dan.fmoon.db.FMoonDataSource;
import com.dan.fmoon.db.FStationProvider;
import com.dan.fmoon.tide.FHarbourTideModel;
import com.dan.fmoon.tide.FTideStation;
import com.dan.ftidalgauge.FStormThreshold;
import com.dan.ftidalgauge.FTidalGauge;
import com.dan.ftidalgauge.FTidalGaugeStyler;
import com.dan.ftidalgauge.FTidalViewMode;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Vorfuehrung der FMoon-Komponenten: Mondkugel und Monatskalender
 * nebeneinander, mit Ortswahl, Datumsregler und Zeitraffer.
 *
 * @author Claude fuer Daniel
 */
public final class FMoonDemo {

    private FMoonGlobe globe;
    private FMoonCalendar calendar;
    private FLabel status;
    private JSlider tagRegler;
    private boolean stumm;
    private FTideBoard board;
    private FTidalGauge gauge;
    private FTideCurve curve;
    private FHarbourTideModel tideModel;
    private FStationProvider stationen;
    private FStationMap karte;
    private FOrbitView bahn;
    private FComboBox pegelWahl;

    /**
     * Legt die Vorfuehrung an. Oeffentlich, damit die Pruefwerkzeuge das
     * Fenster ohne main aufbauen koennen.
     */
    public FMoonDemo() {
    }

    /**
     * Die Karte: Stationen, Kuestenlinie und die Laufstrecken der
     * Tidewelle.
     *
     * <p>Die Kuestenlinie wird aus der Datenbank geholt, wenn sie da ist.
     * Fehlt sie, steht darunter der Grund - die Karte bleibt brauchbar,
     * denn Stationen, Strecken und Gradnetz kommen ohne sie aus.</p>
     *
     * @return fertiges Panel
     */
    private JPanel buildMapSide() {
        karte = new FStationMap();
        karte.setPreferredSize(new Dimension(1240, 520));
        karte.setStations(stationen.getStations());

        final FLabel hinweis = new FLabel(" ");

        // Kueste laden - jeder Fehlschlag ist ein Zustand, kein Absturz.
        String note;
        try {
            FMoonDao dao = new FMoonDao(new FMoonDataSource());
            if (dao.isCoastAvailable()) {
                // Ausschnitt aus den Stationen, mit einem halben Grad Luft.
                double minLon = 180;
                double maxLon = -180;
                double minLat = 90;
                double maxLat = -90;
                for (FTideStation st : stationen.getStations()) {
                    minLon = Math.min(minLon, st.getLocation().getLongitudeDeg());
                    maxLon = Math.max(maxLon, st.getLocation().getLongitudeDeg());
                    minLat = Math.min(minLat, st.getLocation().getLatitudeDeg());
                    maxLat = Math.max(maxLat, st.getLocation().getLatitudeDeg());
                }
                FMoonDao.CoastRings erg = dao.loadCoastRings(
                        minLon - 0.5, minLat - 0.5, maxLon + 0.5, maxLat + 0.5,
                        200.0);
                String satz = erg.rings.isEmpty()
                        ? "Keine Umrisse im Ausschnitt."
                        : erg.rings.size() + " Umrisse aus OGP.GEMEINDEN_SH "
                          + "(OpenStreetMap), geliehen, nicht kopiert.";
                // Was weggelassen wurde, gehört dazugesagt.
                if (erg.skipped > 0) {
                    satz = satz + "  " + erg.skipped
                         + (erg.skipped == 1 ? " Umriss" : " Umrisse")
                         + " übersprungen (über 0,6° ausgedehnt — größer als "
                         + "jede Gemeinde hier, in den Daten fehlerhaft).";
                }
                karte.setCoast(erg.rings, satz);
            } else {
                karte.setCoast(null,
                        "Ohne Küstenlinie: Das Leserecht auf OGP.GEMEINDEN_SH "
                        + "fehlt. db/11_grant_ogp.sql als OGP einspielen.");
            }
        } catch (Throwable t) {
            karte.setCoast(null,
                    "Ohne Küstenlinie: " + kurz(t));
        }
        note = karte.getNote();
        hinweis.setText(note);

        // Klick auf die Karte wählt die Station - und die Gezeitenseite
        // zieht mit. Das ist der Sinn der Karte: nicht nachschlagen,
        // sondern hinzeigen.
        karte.addStationMapListener(new FStationMap.FStationMapListener() {
            @Override
            public void stationSelected(FTideStation st) {
                if (pegelWahl != null) {
                    pegelWahl.setSelectedItem(st);
                }
                board.setStation(st);
                tideModel.setStation(st);
                curve.setStation(st);
                gauge.setTimeZone(st.getLocation().getTimeZone());
                gauge.repaint();
                updateStatus();
            }
        });

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(karte, BorderLayout.CENTER);
        p.add(hinweis, BorderLayout.SOUTH);
        return p;
    }

    /**
     * Das Erklaerbild: die Bahn von oben, daneben der Anblick von der
     * Erde. Es haengt am selben Zeitpunkt wie alles andere - wer im
     * Kalender einen Tag anklickt, sieht hier, warum der Mond an dem
     * Abend so aussieht, wie er aussieht.
     *
     * @return fertiges Panel
     */
    private JPanel buildOrbitSide() {
        bahn = new FOrbitView();
        bahn.setPreferredSize(new Dimension(1240, 500));
        bahn.setObserverLocation(globe.getObserverLocation());
        bahn.setTimeMillis(globe.getTimeMillis());

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(bahn, BorderLayout.CENTER);
        return p;
    }

    private static String kurz(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        int nl = m.indexOf('\n');
        if (nl > 0) {
            m = m.substring(0, nl);
        }
        return m.length() > 140 ? m.substring(0, 140) + "..." : m;
    }

    /**
     * Startet die Vorfuehrung.
     *
     * @param args werden nicht ausgewertet
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                FTheme.getInstance().applyPreset(FThemePreset.LABORATORY_DARK);
                new FMoonDemo().show();
            }
        });
    }

    private void show() {
        FFrame frame = new FFrame("FMoon — Mondkalender");
        frame.setPreferredFrameSize(new Dimension(1320, 880));
        frame.getComponentPane().setLayout(new BorderLayout(10, 10));
        frame.getComponentPane().add(buildContent(), BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Baut den Inhalt der Vorfuehrung. Oeffentlich, damit die Pruefwerkzeuge
     * das Fenster ohne main aufbauen koennen.
     *
     * @return fertiges Panel
     */
    public JPanel buildContent() {
        // Die Stationen kommen aus der Datenbank, wenn sie erreichbar
        // ist, sonst aus den einprogrammierten Konstanten. Der Rueckfall
        // ist Absicht: Die Vorfuehrung soll auf jedem Rechner starten.
        stationen = FStationProvider.load(new FMoonDataSource());

        globe = new FMoonGlobe();
        globe.setPreferredSize(new Dimension(400, 520));
        globe.setShowLibrationGrid(false);

        calendar = new FMoonCalendar();
        calendar.setPreferredSize(new Dimension(820, 520));
        calendar.addFMoonCalendarListener(new FMoonCalendarListener() {
            @Override
            public void daySelected(FMoonCalendar source, FMoonDay day) {
                Calendar c = new GregorianCalendar(
                        calendar.getObserverLocation().getTimeZone());
                c.clear();
                c.set(calendar.getYear(), calendar.getMonth() - 1, day.dayOfMonth,
                        FMoonMonth.REFERENCE_HOUR, 0, 0);
                globe.setTimeMillis(c.getTimeInMillis());
                if (bahn != null) {
                    bahn.setTimeMillis(c.getTimeInMillis());
                }
                if (board != null) {
                    board.setTimeMillis(c.getTimeInMillis());
                    curve.setTimeMillis(c.getTimeInMillis());
                    gauge.setTimeMillis(c.getTimeInMillis());
                }
                stumm = true;
                tagRegler.setValue(day.dayOfMonth);
                stumm = false;
                updateStatus();
            }
        });

        JPanel mondSeite = new JPanel(new BorderLayout(10, 0));
        mondSeite.setOpaque(false);
        mondSeite.add(globe, BorderLayout.WEST);
        mondSeite.add(calendar, BorderLayout.CENTER);

        JTabbedPane mitte = new JTabbedPane();
        mitte.setOpaque(false);
        mitte.addTab("Mond", mondSeite);
        mitte.addTab("Gezeiten", buildTideSide());
        mitte.addTab("Karte", buildMapSide());
        mitte.addTab("Bahn", buildOrbitSide());

        JPanel leisten = new JPanel();
        leisten.setOpaque(false);
        leisten.setLayout(new BoxLayout(leisten, BoxLayout.Y_AXIS));
        leisten.add(buildRowOrtUndMonat());
        leisten.add(buildRowTagUndZeit());
        leisten.add(buildRowAnsicht());
        leisten.add(buildRowKalender());
        leisten.add(buildRowGezeiten());
        leisten.add(buildRowKurve());
        leisten.add(buildRowStatus());

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.add(mitte, BorderLayout.CENTER);
        root.add(leisten, BorderLayout.SOUTH);

        updateStatus();
        return root;
    }

    /**
     * Die Gezeitenseite: links die Ableseanzeige, rechts die vorhandene
     * Pegelkomponente aus FStyle.jar, gespeist aus derselben Hafenzeitrechnung.
     *
     * @return fertiges Panel
     */
    private JPanel buildTideSide() {
        FTideStation start = nordseeStation();

        board = new FTideBoard();
        board.setPreferredSize(new Dimension(440, 520));
        board.setStation(start);

        tideModel = new FHarbourTideModel(start);
        tideModel.setForecastStartMillis(System.currentTimeMillis());
        tideModel.setStormThresholds(
                new FStormThreshold("Erhöhter Wasserstand", 1.5,
                        FStormThreshold.LEVEL_WATCH),
                new FStormThreshold("Sturmflut", 2.5,
                        FStormThreshold.LEVEL_SURGE));

        // Von der vorhandenen Komponente wird nur die PEGELSZENE benutzt.
        // Ihre Kurve wird aus harmonischen Partialtiden gezeichnet, und die
        // hat diese Vorhersage nicht - im Kopf stand dann "0 Partialtiden"
        // und die Kurve blieb leer. Die Szene dagegen liest den Wasserstand
        // aus dem Modell und stimmt.
        gauge = new FTidalGauge();
        gauge.setModel(tideModel);
        gauge.setViewMode(FTidalViewMode.SCENE_ONLY);
        gauge.setTimeZone(start.getLocation().getTimeZone());
        gauge.setShowLegend(false);
        gauge.setPreferredSize(new Dimension(330, 520));
        FTidalGaugeStyler.northSea().apply(gauge);

        curve = new FTideCurve();
        curve.setStation(start);

        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.add(board, BorderLayout.WEST);
        p.add(gauge, BorderLayout.EAST);
        p.add(curve, BorderLayout.CENTER);
        return p;
    }

    /**
     * Die erste Nordseestation aus der Stationsquelle - an der Ostsee
     * zeigt die Vorfuehrung sonst gleich zu Anfang, dass es dort nichts
     * vorherzusagen gibt.
     */
    private FTideStation nordseeStation() {
        for (int i = 0; i < stationen.getStations().size(); i++) {
            FTideStation s = stationen.getStations().get(i);
            if (s.isTidalRangeUseful()) {
                return s;
            }
        }
        return stationen.getStations().get(0);
    }

    private JPanel buildRowGezeiten() {
        JPanel row = newRow();
        row.setName("Gezeiten");

        row.add(caption("Pegel"));
        final FComboBox pegel = new FComboBox(stationen.toArray());
        pegelWahl = pegel;
        pegel.setSelectedItem(stationen.getStations().get(0));
        pegel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FTideStation st = (FTideStation) pegel.getSelectedItem();
                board.setStation(st);
                tideModel.setStation(st);
                curve.setStation(st);
                gauge.setTimeZone(st.getLocation().getTimeZone());
                gauge.repaint();
            }
        });
        row.add(pegel);

        FButton nordOst = new FButton("Nordsee gegen Ostsee");
        nordOst.addActionListener(new ActionListener() {
            private boolean ostsee;

            @Override
            public void actionPerformed(ActionEvent e) {
                ostsee = !ostsee;
                for (int i = 0; i < stationen.getStations().size(); i++) {
                    FTideStation s = stationen.getStations().get(i);
                    if (s.isTidalRangeUseful() != ostsee) {
                        pegel.setSelectedItem(s);
                        return;
                    }
                }
            }
        });
        row.add(nordOst);

        // Woher die Stationen kommen, wird nicht verschwiegen.
        FLabel herkunft = caption(stationen.isFromDatabase()
                ? "Stationen: Datenbank" : "Stationen: eingebaut");
        herkunft.setToolTipText(stationen.getNote());
        row.add(herkunft);

        row.add(schalter("Rechenkette zeigen", true, new Schalt() {
            @Override
            public void set(boolean b) {
                board.setShowChain(b);
            }
        }));
        row.add(schalter("Jetzt-Stand", true, new Schalt() {
            @Override
            public void set(boolean b) {
                board.setShowNow(b);
            }
        }));
        return row;
    }

    private JPanel buildRowKurve() {
        JPanel row = newRow();
        row.setName("Kurve");

        row.add(caption("Zeitraum"));
        final FComboBox spanne = new FComboBox(new String[]{
            "30 Stunden", "3 Tage", "14 Tage", "30 Tage"});
        spanne.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] stunden = {30, 72, 336, 720};
                int i = spanne.getSelectedIndex();
                curve.setSpanHours(stunden[i]);
                curve.setOffsetHours(i == 0 ? -6.0 : -stunden[i] / 6.0);
            }
        });
        row.add(spanne);

        row.add(schalter("Mondband", true, new Schalt() {
            @Override
            public void set(boolean b) {
                curve.setShowMoonBand(b);
            }
        }));

        return row;
    }

    private JPanel newRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        return p;
    }

    private FLabel caption(String text) {
        FLabel l = new FLabel(text);
        l.setFont(FTheme.getInstance().getFont(FFontRole.CAPTION));
        l.setForeground(FTheme.getInstance().getTextMuted());
        return l;
    }

    private JPanel buildRowOrtUndMonat() {
        JPanel row = newRow();
        row.setName("Ort und Monat");

        row.add(caption("Ort"));
        final FComboBox orte = new FComboBox(FGeoLocation.DEFAULTS);
        orte.setSelectedItem(FGeoLocation.TIMMENDORFER_STRAND);
        orte.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FGeoLocation ort = (FGeoLocation) orte.getSelectedItem();
                globe.setObserverLocation(ort);
                calendar.setObserverLocation(ort);
                updateStatus();
            }
        });
        row.add(orte);

        row.add(caption("Monat"));
        FButton zurueck = new FButton("◀");
        zurueck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                calendar.shiftMonths(-1);
                syncTagRegler();
                updateStatus();
            }
        });
        row.add(zurueck);

        FButton vor = new FButton("▶");
        vor.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                calendar.shiftMonths(1);
                syncTagRegler();
                updateStatus();
            }
        });
        row.add(vor);

        FButton heute = new FButton("Heute");
        heute.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Calendar c = new GregorianCalendar(
                        calendar.getObserverLocation().getTimeZone());
                calendar.setYear(c.get(Calendar.YEAR));
                calendar.setMonth(c.get(Calendar.MONTH) + 1);
                syncTagRegler();
                calendar.setSelectedDay(c.get(Calendar.DAY_OF_MONTH));
                globe.setTimeMillis(System.currentTimeMillis());
                if (bahn != null) {
                    bahn.setTimeMillis(System.currentTimeMillis());
                }
                updateStatus();
            }
        });
        row.add(heute);

        FButton naechsterVollmond = new FButton("Nächster Vollmond");
        naechsterVollmond.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                springeZu(FMoonPhaseTimes.FULL_MOON);
            }
        });
        row.add(naechsterVollmond);

        FButton naechsterNeumond = new FButton("Nächster Neumond");
        naechsterNeumond.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                springeZu(FMoonPhaseTimes.NEW_MOON);
            }
        });
        row.add(naechsterNeumond);

        return row;
    }

    private void springeZu(int typ) {
        double jetzt = FAstroTime.julianDayFromMillis(globe.getTimeMillis());
        FMoonPhaseTimes.Phase p = FMoonPhaseTimes.next(jetzt + 0.1, typ);
        globe.setTimeMillis(p.millis());
        if (bahn != null) {
            bahn.setTimeMillis(p.millis());
        }
        Calendar c = new GregorianCalendar(globe.getObserverLocation().getTimeZone());
        c.setTimeInMillis(p.millis());
        calendar.setYear(c.get(Calendar.YEAR));
        calendar.setMonth(c.get(Calendar.MONTH) + 1);
        syncTagRegler();
        calendar.setSelectedDay(c.get(Calendar.DAY_OF_MONTH));
        updateStatus();
    }

    private JPanel buildRowTagUndZeit() {
        JPanel row = newRow();
        row.setName("Tag und Zeit");

        row.add(caption("Tag"));
        tagRegler = new JSlider(1, 31, 1);
        tagRegler.setOpaque(false);
        tagRegler.setPreferredSize(new Dimension(280, 26));
        tagRegler.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (stumm) {
                    return;
                }
                int tag = tagRegler.getValue();
                if (tag <= calendar.getMonthData().getLength()) {
                    calendar.setSelectedDay(tag);
                }
            }
        });
        row.add(tagRegler);

        final JCheckBox lauf = new JCheckBox("Zeitraffer");
        lauf.setOpaque(false);
        lauf.setForeground(FTheme.getInstance().getText());
        lauf.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globe.setAnimated(lauf.isSelected());
            }
        });
        row.add(lauf);

        row.add(caption("Tempo"));
        final JSlider tempo = new JSlider(1, 100, 40);
        tempo.setOpaque(false);
        tempo.setPreferredSize(new Dimension(160, 26));
        tempo.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 1 bis 100 auf 60 s/s bis 24 h/s
                double f = Math.pow(10.0, 1.8 + tempo.getValue() / 100.0 * 3.3);
                globe.setTimeLapseFactor(f);
            }
        });
        row.add(tempo);

        final JCheckBox jetzt = new JCheckBox("Der Uhr folgen");
        jetzt.setOpaque(false);
        jetzt.setForeground(FTheme.getInstance().getText());
        jetzt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globe.setLive(jetzt.isSelected());
            }
        });
        row.add(jetzt);

        return row;
    }

    private JPanel buildRowAnsicht() {
        JPanel row = newRow();
        row.setName("Ansicht");

        row.add(caption("Lage"));
        final FComboBox lage = new FComboBox(FMoonViewMode.values());
        lage.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globe.setViewMode((FMoonViewMode) lage.getSelectedItem());
                updateStatus();
            }
        });
        row.add(lage);

        row.add(schalter("Erdschein", true, new Schalt() {
            @Override
            public void set(boolean b) {
                globe.setShowEarthshine(b);
                calendar.setShowEarthshine(b);
            }
        }));
        row.add(schalter("Sterne", true, new Schalt() {
            @Override
            public void set(boolean b) {
                globe.setShowStars(b);
            }
        }));
        row.add(schalter("Größe nach Entfernung", true, new Schalt() {
            @Override
            public void set(boolean b) {
                globe.setScaleWithDistance(b);
            }
        }));
        row.add(schalter("Librationsmarke", false, new Schalt() {
            @Override
            public void set(boolean b) {
                globe.setShowLibrationGrid(b);
            }
        }));
        return row;
    }

    private JPanel buildRowKalender() {
        JPanel row = newRow();
        row.setName("Kalender und Thema");

        row.add(caption("Kalender"));
        row.add(schalter("Auf-/Untergang", true, new Schalt() {
            @Override
            public void set(boolean b) {
                calendar.setShowRiseSet(b);
            }
        }));
        row.add(schalter("Beleuchtung in Prozent", true, new Schalt() {
            @Override
            public void set(boolean b) {
                calendar.setShowIllumination(b);
            }
        }));
        row.add(schalter("Ereignisse", true, new Schalt() {
            @Override
            public void set(boolean b) {
                calendar.setShowEvents(b);
            }
        }));

        FButton thema = new FButton("Thema wechseln");
        thema.addActionListener(new ActionListener() {
            private int i;

            @Override
            public void actionPerformed(ActionEvent e) {
                FThemePreset[] alle = FThemePreset.values();
                i = (i + 1) % alle.length;
                FTheme.getInstance().applyPreset(alle[i]);
                FMoonRenderer.clearCache();
            }
        });
        row.add(thema);

        return row;
    }

    private JPanel buildRowStatus() {
        JPanel row = newRow();
        row.setName("Statuszeile");
        status = new FLabel(" ");
        status.setFont(FTheme.getInstance().getFont(FFontRole.CAPTION));
        status.setForeground(FTheme.getInstance().getTextMuted());
        row.add(status);
        return row;
    }

    private interface Schalt {
        void set(boolean b);
    }

    private JCheckBox schalter(String text, boolean anfangs, final Schalt s) {
        final JCheckBox cb = new JCheckBox(text, anfangs);
        cb.setOpaque(false);
        cb.setForeground(FTheme.getInstance().getText());
        cb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                s.set(cb.isSelected());
            }
        });
        return cb;
    }

    private void syncTagRegler() {
        stumm = true;
        tagRegler.setMaximum(calendar.getMonthData().getLength());
        stumm = false;
    }

    private void updateStatus() {
        if (status == null) {
            return;
        }
        FMoonEphemeris e = globe.getEphemeris();
        SimpleDateFormat f = new SimpleDateFormat("EEE dd.MM.yyyy HH:mm");
        f.setTimeZone(globe.getObserverLocation().getTimeZone());
        String t = String.format(
                "%s  ·  %s, %.1f %% beleuchtet  ·  %.0f km (%.1f')  "
                + "·  Libration %+.1f° / %+.1f°  "
                + "·  Neigung der Sichel %+.0f°",
                f.format(new Date(globe.getTimeMillis())),
                e.phase.phaseName(), e.phase.illuminatedFraction * 100.0,
                e.moon.distanceKm, e.apparentDiameterArcmin(),
                e.libration.longitude, e.libration.latitude,
                e.limbTiltFromVertical);
        status.setText(t);
    }
}
