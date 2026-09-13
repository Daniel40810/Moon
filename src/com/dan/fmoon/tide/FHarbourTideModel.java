package com.dan.fmoon.tide;

import com.dan.ftidalgauge.FStormThreshold;
import com.dan.ftidalgauge.FTidalGaugeModel;
import com.dan.ftidalgauge.FTidalHarmonic;
import com.dan.ftidalgauge.FTideEvent;
import com.dan.ftidalgauge.FTideEventType;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Laesst die Hafenzeitrechnung von der vorhandenen Komponente
 * {@link com.dan.ftidalgauge.FTidalGauge} anzeigen.
 *
 * <p>Die Anzeige war bereits in FStyle.jar vorhanden - mit Wasserflaeche,
 * Pegelhaus, Kurve und Ereignismarken. Sie erwartet ein
 * {@link FTidalGaugeModel}, und genau das ist diese Klasse: Sie liefert
 * dieselben Auskuenfte, rechnet sie aber nach der Hafenzeit statt aus
 * harmonischen Partialtiden. Es musste also keine zweite Gezeitenanzeige
 * gebaut werden.</p>
 *
 * <h3>Warum ein Zwischenspeicher noetig ist</h3>
 *
 * <p>{@code getLevelAt} wird beim Zeichnen der Kurve fuer jede Bildspalte
 * aufgerufen, also mehrere hundert Mal je Bild. Jeder Aufruf der rohen
 * Rechnung wuerde Meridiandurchgaenge suchen und dafuer den Mond hunderte
 * Male stellen - das waere unbrauchbar langsam. Deshalb werden die Ereignisse
 * fuer ein Fenster einmal berechnet und beim Zeichnen nur noch zwischen ihnen
 * interpoliert. Das Fenster waechst selbstaendig mit, wenn die Anzeige
 * daraus hinauslaeuft.</p>
 *
 * <p>{@code getHarmonics} liefert bewusst eine leere Liste: Diese Vorhersage
 * hat keine Partialtiden. Wer sie sehen will, nimmt das harmonische Modell der
 * Bibliothek.</p>
 *
 * @author Claude fuer Daniel
 */
public class FHarbourTideModel implements FTidalGaugeModel {

    /** Wieviel Zeit ueber den angefragten Bereich hinaus vorgerechnet wird. */
    private static final long MARGIN_MILLIS = 3L * 24L * 3600000L;

    /**
     * Groesster Zeitraum, den der Zwischenspeicher haelt. Sechzig Tage sind
     * rund 240 Ereignisse - das rechnet sich in etwa fuenfzig Millisekunden
     * und deckt jede Ansicht, die die Kurve zeigen kann.
     */
    private static final long MAX_SPAN_MILLIS = 60L * 24L * 3600000L;

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private FTideStation station;
    private List<FTideMoment> cache = new ArrayList<FTideMoment>();
    private long cacheFrom = 0L;
    private long cacheTo = -1L;
    private long forecastStartMillis;
    private double historicHighMeters;
    private String historicHighLabel = "";
    private final List<FStormThreshold> thresholds = new ArrayList<FStormThreshold>();

    /**
     * Legt ein Modell fuer eine Station an.
     *
     * @param station Station, nicht null
     */
    public FHarbourTideModel(FTideStation station) {
        setStation(station);
    }

    /**
     * Die Station, fuer die gerechnet wird.
     *
     * @return Station
     */
    public FTideStation getStation() {
        return station;
    }

    /**
     * Setzt die Station und verwirft den Zwischenspeicher.
     *
     * @param station Station, nicht null
     */
    public final void setStation(FTideStation station) {
        if (station == null) {
            return;
        }
        FTideStation old = this.station;
        this.station = station;
        clearCache();
        support.firePropertyChange(PROP_DATA, old, station);
    }

    private void clearCache() {
        cache = new ArrayList<FTideMoment>();
        cacheFrom = 0L;
        cacheTo = -1L;
    }

    /**
     * Stellt sicher, dass der Zwischenspeicher den Zeitraum abdeckt.
     */
    private synchronized void ensure(long from, long to) {
        if (cacheTo >= cacheFrom && from >= cacheFrom && to <= cacheTo) {
            return;
        }
        long neuVon = Math.min(from, (cacheTo >= cacheFrom) ? cacheFrom : from) - MARGIN_MILLIS;
        long neuBis = Math.max(to, (cacheTo >= cacheFrom) ? cacheTo : to) + MARGIN_MILLIS;
        // Der Zwischenspeicher waechst, und bei jedem Wachsen wird der GANZE
        // Zeitraum neu gerechnet - nicht nur das Stueck, das dazukommt. Wer
        // den Ablauf tagweise vorwaerts laufen laesst, bezahlt das sonst
        // quadratisch: nach einem halben Jahr dauert ein Schritt laenger als
        // der erste hundert zusammen. Deshalb eine Obergrenze. Sie wird
        // ueberschritten, nicht gedehnt: Der Speicher faellt dann auf das
        // zurueck, was gerade gebraucht wird.
        if (neuBis - neuVon > MAX_SPAN_MILLIS) {
            neuVon = from - MARGIN_MILLIS;
            neuBis = to + MARGIN_MILLIS;
        }
        cache = FHarbourTide.events(station, neuVon, neuBis);
        cacheFrom = neuVon;
        cacheTo = neuBis;
    }

    // ------------------------------------------------------------------
    // FTidalGaugeModel
    // ------------------------------------------------------------------

    @Override
    public String getStationName() {
        return station.getName();
    }

    @Override
    public double getDatumMeters() {
        // Bezugsflaeche ist das Mittelwasser, nicht Seekartennull.
        return 0.0;
    }

    @Override
    public List<FTidalHarmonic> getHarmonics() {
        return Collections.emptyList();
    }

    @Override
    public double getLevelAt(long timeMillis) {
        ensure(timeMillis - 3600000L, timeMillis + 3600000L);
        FTideMoment vor = null;
        FTideMoment nach = null;
        for (int i = 0; i < cache.size(); i++) {
            FTideMoment m = cache.get(i);
            if (m.getTimeMillis() <= timeMillis) {
                vor = m;
            } else {
                nach = m;
                break;
            }
        }
        if (vor == null && nach == null) {
            return 0.0;
        }
        if (vor == null) {
            return nach.getLevelMeters();
        }
        if (nach == null) {
            return vor.getLevelMeters();
        }
        double t = (timeMillis - vor.getTimeMillis())
                / (double) (nach.getTimeMillis() - vor.getTimeMillis());
        double a = vor.getLevelMeters();
        double b = nach.getLevelMeters();
        return a + (b - a) * 0.5 * (1.0 - Math.cos(Math.PI * t));
    }

    @Override
    public List<FTideEvent> findEvents(long fromMillis, long toMillis) {
        ensure(fromMillis, toMillis);
        List<FTideEvent> out = new ArrayList<FTideEvent>();
        for (int i = 0; i < cache.size(); i++) {
            FTideMoment m = cache.get(i);
            if (m.getTimeMillis() < fromMillis || m.getTimeMillis() > toMillis) {
                continue;
            }
            out.add(new FTideEvent(m.getTimeMillis(), m.getLevelMeters(),
                    m.isHighWater() ? FTideEventType.HIGH_WATER : FTideEventType.LOW_WATER,
                    m.getTimeMillis() >= forecastStartMillis));
        }
        return out;
    }

    @Override
    public List<FStormThreshold> getStormThresholds() {
        return thresholds;
    }

    /**
     * Setzt Sturmflutschwellen fuer die Anzeige.
     *
     * @param values Schwellen
     */
    public void setStormThresholds(FStormThreshold... values) {
        thresholds.clear();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                thresholds.add(values[i]);
            }
        }
        support.firePropertyChange(PROP_DATA, null, thresholds);
    }

    @Override
    public double getHistoricHighMeters() {
        return historicHighMeters;
    }

    @Override
    public String getHistoricHighLabel() {
        return historicHighLabel;
    }

    /**
     * Setzt eine historische Marke fuer die Anzeige.
     *
     * @param meters Hoehe ueber Mittelwasser
     * @param label  Beschriftung
     */
    public void setHistoricHigh(double meters, String label) {
        this.historicHighMeters = meters;
        this.historicHighLabel = (label == null) ? "" : label;
        support.firePropertyChange(PROP_DATA, null, label);
    }

    @Override
    public long getForecastStartMillis() {
        return forecastStartMillis;
    }

    /**
     * Ab diesem Zeitpunkt gelten die Werte als Vorhersage und werden von der
     * Anzeige entsprechend gekennzeichnet.
     *
     * @param forecastStartMillis Zeitpunkt
     */
    public void setForecastStartMillis(long forecastStartMillis) {
        this.forecastStartMillis = forecastStartMillis;
        support.firePropertyChange(PROP_DATA, null, forecastStartMillis);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        support.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        support.removePropertyChangeListener(l);
    }

    /** Name der Eigenschaft, die bei Datenaenderungen gemeldet wird. */
    public static final String PROP_DATA = "data";
}
