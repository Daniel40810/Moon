package com.dan.fmoon;

import com.dan.fgeochart.DefaultFGeoChartModel;
import com.dan.fgeochart.FGeoChart;
import com.dan.fgeochart.FGeoChartStyler;
import com.dan.fgeochart.FGeoFeature;
import com.dan.fgeochart.FGeoLayer;
import com.dan.fmaplegend.FLayerGeometryType;
import com.dan.fmoon.tide.FTideStation;
import com.dan.fmoon.tide.FWaterBody;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * Die Pegelstationen auf einer Karte, mit der Kuestenlinie dahinter und
 * den Laufstrecken der Tidewelle dazwischen.
 *
 * <h3>Was hier NICHT gebaut wurde</h3>
 *
 * <p>Der Kartenbetrachter. {@code com.dan.fgeochart.FGeoChart} steht
 * bereits in FStyle.jar - mit Ebenen, Projektion, Gitter, Zoom,
 * Schwebeanzeige und einem Styler fuer Seekartenfarben. Diese Komponente
 * fuellt ihn und legt darueber, was er nicht kann.</p>
 *
 * <p>Was er nicht kann, ist die <b>Auswahl</b>: FGeoChart meldet keinen
 * Klick. Er gibt aber {@link FGeoChart#toLon} und {@link FGeoChart#toLat}
 * heraus, und damit wird aus einem Mausklick ein Punkt auf der Erde. Die
 * Frage, welche Station dort am naechsten liegt, beantwortet dann die
 * Datenbank mit {@code SDO_NN} - oder, wenn keine da ist, die
 * Rueckfallrechnung in dieser Klasse.</p>
 *
 * <h3>Die Kuestenlinie ist geliehen</h3>
 *
 * <p>Sie stammt aus den OpenStreetMap-Daten im Schema OGP und wird
 * ausdruecklich nicht kopiert. Fehlt das Leserecht oder die Datenbank,
 * zeigt die Karte Stationen, Strecken und Gradnetz - und sagt in der
 * Fusszeile, warum die Kueste fehlt. Eine gezeichnete Ersatzkueste waere
 * die schlechtere Wahl: Sie saehe aus wie eine Aussage ueber die
 * Wirklichkeit, ohne eine zu sein.</p>
 *
 * @author Claude fuer Daniel
 */
public class FStationMap extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Wer erfahren will, welche Station angeklickt wurde. */
    public interface FStationMapListener {

        /**
         * Eine Station wurde ausgewaehlt.
         *
         * @param station die Station, niemals null
         */
        void stationSelected(FTideStation station);
    }

    private final FGeoChart chart = new FGeoChart();
    private final DefaultFGeoChartModel model = new DefaultFGeoChartModel();
    private final List<FStationMapListener> listeners =
            new ArrayList<FStationMapListener>();

    private final List<FTideStation> stations = new ArrayList<FTideStation>();
    private FTideStation selected;
    private String note = "";
    private boolean showWaves = true;

    private Color northSeaColor = new Color(0x3FA9B8);
    private Color balticColor = new Color(0xB8943F);
    private Color coastColor = new Color(0x2C3E46);
    private Color waveColor = new Color(0x7FD4DE);

    /** Legt eine leere Karte an. */
    public FStationMap() {
        super(new BorderLayout());
        chart.setModel(model);
        FGeoChartStyler.ocean().apply(chart);
        chart.setShowGrid(true);
        chart.setShowStatusBar(true);
        chart.setShowZoomControls(true);
        add(chart, BorderLayout.CENTER);

        chart.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                double lon = chart.toLon(e.getX());
                double lat = chart.toLat(e.getY());
                FTideStation s = nearest(lon, lat);
                if (s != null) {
                    setSelectedStation(s);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Fuellen
    // ------------------------------------------------------------------

    /**
     * Setzt die Stationen.
     *
     * @param neue Stationen, darf null sein
     */
    public void setStations(List<FTideStation> neue) {
        stations.clear();
        if (neue != null) {
            stations.addAll(neue);
        }
        rebuild();
    }

    /**
     * Setzt die Umrisse der Kueste.
     *
     * @param ringe je Ring ein Feld aus abwechselnd Laenge und Breite;
     *              null oder leer bedeutet: keine Kueste
     * @param hinweis Satz fuer die Fusszeile, warum sie da ist oder fehlt
     */
    public void setCoast(List<double[]> ringe, String hinweis) {
        this.note = (hinweis == null) ? "" : hinweis;
        FGeoLayer kueste = model.findLayer("Kueste");
        if (kueste == null) {
            kueste = new FGeoLayer("Kueste", FLayerGeometryType.POLYGON);
            model.addLayer(kueste);
        }
        kueste.clear();
        kueste.setColor(coastColor);
        kueste.setFillAlpha(0.85f);
        kueste.setStrokeWidth(1.0f);
        if (ringe != null) {
            long id = 1;
            for (int i = 0; i < ringe.size(); i++) {
                kueste.add(FGeoFeature.polygon(id++, "", ringe.get(i)));
            }
        }
        // Die Kueste gehoert nach hinten. FGeoChart zeichnet in der
        // Reihenfolge der Ebenen, also muss sie zuerst hinein - deshalb
        // wird hier neu aufgebaut statt nur ergaenzt.
        rebuild();
    }

    private void rebuild() {
        List<double[]> kuestenRinge = new ArrayList<double[]>();
        FGeoLayer alt = model.findLayer("Kueste");
        if (alt != null) {
            for (int i = 0; i < alt.size(); i++) {
                FGeoFeature f = alt.get(i);
                double[] p = new double[f.getPointCount() * 2];
                for (int k = 0; k < f.getPointCount(); k++) {
                    p[2 * k] = f.getLon(k);
                    p[2 * k + 1] = f.getLat(k);
                }
                kuestenRinge.add(p);
            }
        }

        model.clear();

        FGeoLayer kueste = new FGeoLayer("Kueste", FLayerGeometryType.POLYGON);
        kueste.setColor(coastColor);
        kueste.setFillAlpha(0.85f);
        kueste.setStrokeWidth(1.0f);
        for (int i = 0; i < kuestenRinge.size(); i++) {
            kueste.add(FGeoFeature.polygon(i + 1, "", kuestenRinge.get(i)));
        }
        model.addLayer(kueste);

        if (showWaves) {
            model.addLayer(buildWaves());
        }

        FGeoLayer nord = new FGeoLayer("Nordsee", FLayerGeometryType.POINT);
        nord.setColor(northSeaColor);
        nord.setPointRadius(7);
        FGeoLayer ost = new FGeoLayer("Ostsee", FLayerGeometryType.POINT);
        ost.setColor(balticColor);
        ost.setPointRadius(7);

        long id = 1000;
        for (int i = 0; i < stations.size(); i++) {
            FTideStation s = stations.get(i);
            FGeoFeature f = FGeoFeature.point(id, s.getName(),
                    s.getLocation().getLongitudeDeg(),
                    s.getLocation().getLatitudeDeg());
            id++;
            if (s.getWaterBody() == FWaterBody.BALTIC_SEA) {
                ost.add(f);
            } else {
                nord.add(f);
            }
        }
        model.addLayer(nord);
        model.addLayer(ost);
        model.fireDataChanged();
        chart.zoomToFit();
        repaint();
    }

    /**
     * Wie viel spaeter die Tidewelle jede Nordseestation erreicht,
     * gemessen an der Station, die sie zuerst erreicht.
     *
     * <h3>Warum ein Faecher und keine Kette</h3>
     *
     * <p>Der erste Versuch verband die Stationen in der Reihenfolge ihrer
     * Hafenzeit zu einem Linienzug. Die Zahlen daran stimmten, aber das
     * Bild log: Ein Linienzug sieht aus wie ein Weg, und der gezeichnete
     * Weg - Helgoland, List auf Sylt, Buesum, Cuxhaven, Husum - ist nicht
     * der Weg der Welle, sondern nur die Folge der Ankunftszeiten. Er
     * zickzackt quer durch die Bucht.</p>
     *
     * <p>Jetzt geht von der zuerst erreichten Station ein Strahl zu jeder
     * anderen, und daran steht der Verzug. Das ist genau die Aussage, die
     * in den Daten steckt - nicht mehr und nicht weniger.</p>
     *
     * <p>Die Ostsee bekommt keine Strahlen: Dort gibt es keine Welle, der
     * man zusehen koennte.</p>
     */
    private FGeoLayer buildWaves() {
        FGeoLayer wellen = new FGeoLayer("Tidewelle", FLayerGeometryType.LINE);
        wellen.setColor(waveColor);
        wellen.setStrokeWidth(1.6f);

        FTideStation erste = null;
        List<FTideStation> nordsee = new ArrayList<FTideStation>();
        for (int i = 0; i < stations.size(); i++) {
            FTideStation s = stations.get(i);
            if (s.getWaterBody() != FWaterBody.NORTH_SEA) {
                continue;
            }
            nordsee.add(s);
            if (erste == null || s.getHighWaterIntervalHours()
                                 < erste.getHighWaterIntervalHours()) {
                erste = s;
            }
        }
        if (erste == null) {
            return wellen;
        }

        long id = 500;
        for (int i = 0; i < nordsee.size(); i++) {
            FTideStation b = nordsee.get(i);
            if (b == erste) {
                continue;
            }
            double dt = (b.getHighWaterIntervalHours()
                       - erste.getHighWaterIntervalHours()) * 60.0;
            // Der Pfeil als Escape, nicht als Zeichen: Eine Quelldatei
            // mit Sonderzeichen haengt an der Zeichensatzeinstellung des
            // Uebersetzers, und die ist auf einem Windows-Rechner nicht
            // dieselbe wie hier.
            wellen.add(FGeoFeature.line(id++,
                    erste.getName() + " \u2192 " + b.getName() + ": "
                    + String.format("%+.0f min", dt),
                    new double[] {
                        erste.getLocation().getLongitudeDeg(),
                        erste.getLocation().getLatitudeDeg(),
                        b.getLocation().getLongitudeDeg(),
                        b.getLocation().getLatitudeDeg() }));
        }
        return wellen;
    }

    // ------------------------------------------------------------------
    // Auswahl
    // ------------------------------------------------------------------

    /**
     * Die Station, die einem Punkt am naechsten liegt - gerechnet in
     * Java.
     *
     * <p>Das ist der Rueckfall. Liegt eine Datenbank an, soll
     * {@code FMoonDao.nearestStation} gefragt werden; die Karte selbst
     * kennt keine Verbindung, damit sie auch ohne eine benutzt werden
     * kann.</p>
     *
     * @param lon Laenge
     * @param lat Breite
     * @return naechste Station, oder null wenn keine da ist
     */
    public FTideStation nearest(double lon, double lat) {
        FTideStation best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < stations.size(); i++) {
            FTideStation s = stations.get(i);
            double dLat = s.getLocation().getLatitudeDeg() - lat;
            // Laengengrade werden zum Pol hin enger. Ohne diesen Faktor
            // liegt die Auswahl in unseren Breiten um 40 Prozent daneben.
            double dLon = (s.getLocation().getLongitudeDeg() - lon)
                        * Math.cos(Math.toRadians(lat));
            double d = dLat * dLat + dLon * dLon;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /**
     * Die ausgewaehlte Station.
     *
     * @return Station, oder null
     */
    public FTideStation getSelectedStation() {
        return selected;
    }

    /**
     * Waehlt eine Station aus und benachrichtigt die Zuhoerer.
     *
     * @param s Station, darf null sein
     */
    public void setSelectedStation(FTideStation s) {
        FTideStation alt = selected;
        selected = s;
        firePropertyChange("selectedStation", alt, s);
        if (s != null) {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).stationSelected(s);
            }
        }
        repaint();
    }

    /**
     * Meldet einen Zuhoerer an.
     *
     * @param l Zuhoerer
     */
    public void addStationMapListener(FStationMapListener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    /**
     * Meldet einen Zuhoerer ab.
     *
     * @param l Zuhoerer
     */
    public void removeStationMapListener(FStationMapListener l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------
    // Eigenschaften
    // ------------------------------------------------------------------

    /**
     * Der Kartenbetrachter darunter, falls jemand am Ausschnitt drehen
     * will.
     *
     * @return der FGeoChart
     */
    public FGeoChart getChart() {
        return chart;
    }

    /**
     * Der Hinweis zur Kuestenlinie.
     *
     * @return Hinweistext, niemals null
     */
    public String getNote() {
        return note;
    }

    /**
     * Werden die Laufstrecken der Tidewelle gezeigt?
     *
     * @return wahr, wenn ja
     */
    public boolean isShowWaves() {
        return showWaves;
    }

    /**
     * Schaltet die Laufstrecken der Tidewelle.
     *
     * @param b wahr fuer sichtbar
     */
    public void setShowWaves(boolean b) {
        boolean alt = showWaves;
        showWaves = b;
        firePropertyChange("showWaves", alt, b);
        rebuild();
    }

    /**
     * Farbe der Nordseestationen.
     *
     * @return Farbe
     */
    public Color getNorthSeaColor() {
        return northSeaColor;
    }

    /**
     * Setzt die Farbe der Nordseestationen.
     *
     * @param c Farbe
     */
    public void setNorthSeaColor(Color c) {
        Color alt = northSeaColor;
        northSeaColor = c;
        firePropertyChange("northSeaColor", alt, c);
        rebuild();
    }

    /**
     * Farbe der Ostseestationen.
     *
     * @return Farbe
     */
    public Color getBalticColor() {
        return balticColor;
    }

    /**
     * Setzt die Farbe der Ostseestationen.
     *
     * @param c Farbe
     */
    public void setBalticColor(Color c) {
        Color alt = balticColor;
        balticColor = c;
        firePropertyChange("balticColor", alt, c);
        rebuild();
    }

    /**
     * Farbe der Landflaechen.
     *
     * @return Farbe
     */
    public Color getCoastColor() {
        return coastColor;
    }

    /**
     * Setzt die Farbe der Landflaechen.
     *
     * @param c Farbe
     */
    public void setCoastColor(Color c) {
        Color alt = coastColor;
        coastColor = c;
        firePropertyChange("coastColor", alt, c);
        rebuild();
    }

    /**
     * Farbe der Wellenstrecken.
     *
     * @return Farbe
     */
    public Color getWaveColor() {
        return waveColor;
    }

    /**
     * Setzt die Farbe der Wellenstrecken.
     *
     * @param c Farbe
     */
    public void setWaveColor(Color c) {
        Color alt = waveColor;
        waveColor = c;
        firePropertyChange("waveColor", alt, c);
        rebuild();
    }
}
