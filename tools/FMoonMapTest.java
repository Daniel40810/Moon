import com.dan.fmoon.FStationMap;
import com.dan.fmoon.FStationMapBeanInfo;
import com.dan.fmoon.tide.FTideStation;
import com.dan.fmoon.tide.FWaterBody;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Prueft die Karte, ohne eine Datenbank zu brauchen.
 *
 * <p>Die Kuestenlinie kommt aus einem fremden Schema und ist von hier
 * aus nicht erreichbar. Geprueft wird deshalb das, was ohne sie gilt:
 * dass die Ebenen entstehen, dass die Auswahl auf der Kugeloberflaeche
 * richtig rechnet, dass die Wellenstrecken der Reihenfolge der Hafenzeit
 * folgen, und dass die Karte ohne Kueste weder abstuerzt noch so tut,
 * als haette sie eine.</p>
 */
public class FMoonMapTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String was, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-58s %s%n", ok ? "  OK  " : " FEHL ", was, detail);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Karte ohne Datenbank ===");

        FStationMap karte = new FStationMap();
        karte.setSize(900, 600);
        karte.setStations(Arrays.asList(FTideStation.DEFAULTS));

        check("Alle acht Stationen sind auf der Karte",
                zaehlePunkte(karte) == 8, zaehlePunkte(karte) + " Punkte");

        // Nordsee und Ostsee liegen in getrennten Ebenen: Sie sollen
        // verschieden aussehen duerfen, und die Legende soll sie
        // getrennt schalten koennen.
        check("Nordsee und Ostsee sind getrennte Ebenen",
                karte.getChart().getModel().findLayer("Nordsee") != null
                && karte.getChart().getModel().findLayer("Ostsee") != null, "");

        int nord = karte.getChart().getModel().findLayer("Nordsee").size();
        int ost = karte.getChart().getModel().findLayer("Ostsee").size();
        check("Fuenf Nordsee-, drei Ostseestationen",
                nord == 5 && ost == 3, nord + " und " + ost);

        // ---------------------------------------------------------------
        // Die Wellenstrecken
        check("Wellenstrecken sind da", karte.isShowWaves()
                && karte.getChart().getModel().findLayer("Tidewelle") != null, "");
        int strecken = karte.getChart().getModel().findLayer("Tidewelle").size();
        check("Vier Strecken zwischen fuenf Nordseestationen",
                strecken == 4, strecken + " Strecken");

        // JEDE Strecke geht von der zuerst erreichten Station aus. Ein
        // Linienzug in der Reihenfolge der Hafenzeit sah aus wie ein Weg
        // und war keiner - die Zahlen stimmten, das Bild log.
        int nichtVonHelgoland = 0;
        String nachHusum = null;
        for (int i = 0; i < strecken; i++) {
            String n = karte.getChart().getModel().findLayer("Tidewelle")
                    .get(i).getName();
            if (!n.startsWith("Helgoland")) {
                nichtVonHelgoland++;
            }
            if (n.contains("Husum")) {
                nachHusum = n;
            }
        }
        check("Jede Strecke geht von Helgoland aus, kein Linienzug",
                nichtVonHelgoland == 0, nichtVonHelgoland + " andere");
        check("Husum liegt 69 Minuten hinter Helgoland",
                nachHusum != null && nachHusum.endsWith("+69 min"),
                String.valueOf(nachHusum));

        karte.setShowWaves(false);
        check("Abschalten laesst die Strecken verschwinden",
                karte.getChart().getModel().findLayer("Tidewelle") == null, "");
        karte.setShowWaves(true);

        // ---------------------------------------------------------------
        // Die Auswahl
        System.out.println();
        System.out.println("=== Auswahl ===");

        FTideStation bue = FTideStation.BUESUM;
        FTideStation gefunden = karte.nearest(
                bue.getLocation().getLongitudeDeg() + 0.01,
                bue.getLocation().getLatitudeDeg() + 0.01);
        check("Ein Punkt neben Buesum findet Buesum",
                gefunden == bue, String.valueOf(gefunden));

        // Die Laengengrade werden zum Pol hin enger. Ein Punkt, der in
        // Grad naeher an der einen Station liegt, kann in Kilometern
        // naeher an der anderen liegen - ohne den Kosinusfaktor waehlt
        // die Karte in unseren Breiten die falsche.
        //
        // Timmendorf 53,995 N 10,778 O, Niendorf 53,981 N 10,816 O.
        // Ein Punkt 0,02 Grad oestlich und 0,012 Grad noerdlich von
        // Timmendorf liegt in reinen Graden fast gleich weit von beiden,
        // in Wahrheit aber naeher an Niendorf.
        FTideStation tim = FTideStation.TIMMENDORFER_STRAND;
        FTideStation nie = FTideStation.NIENDORF;
        double lon = 10.8000;
        double lat = 53.9880;
        double dTim = kmAbstand(lon, lat, tim);
        double dNie = kmAbstand(lon, lat, nie);
        FTideStation erwartet = (dTim < dNie) ? tim : nie;
        check("Die Auswahl rechnet in Kilometern, nicht in Grad",
                karte.nearest(lon, lat) == erwartet,
                String.format("%.2f km gegen %.2f km", dTim, dNie));

        final FTideStation[] gemeldet = new FTideStation[1];
        karte.addStationMapListener(new FStationMap.FStationMapListener() {
            @Override
            public void stationSelected(FTideStation s) {
                gemeldet[0] = s;
            }
        });
        karte.setSelectedStation(bue);
        check("Die Auswahl wird gemeldet", gemeldet[0] == bue, "");
        check("und gemerkt", karte.getSelectedStation() == bue, "");

        // ---------------------------------------------------------------
        // Ohne Kueste
        System.out.println();
        System.out.println("=== Ohne Kuestenlinie ===");

        karte.setCoast(null, "Ohne Küstenlinie: Leserecht fehlt.");
        check("Die Kuestenebene bleibt leer",
                karte.getChart().getModel().findLayer("Kueste").size() == 0, "");
        check("Die Stationen sind trotzdem da",
                zaehlePunkte(karte) == 8, "");
        check("Und die Karte sagt, warum die Kueste fehlt",
                karte.getNote().length() > 10, karte.getNote());

        // Eine erfundene Ersatzkueste waere die schlechtere Wahl. Diese
        // Pruefung haelt die Entscheidung fest: keine Polygone ohne
        // Herkunft.
        check("Es wird keine Ersatzkueste gezeichnet",
                karte.getChart().getModel().findLayer("Kueste").getFeatures()
                        .isEmpty(), "");

        // ---------------------------------------------------------------
        // Mit Kueste (erfundene Ringe, nur zum Pruefen des Weges)
        List<double[]> ringe = new ArrayList<double[]>();
        ringe.add(new double[] {8.0, 54.0, 9.0, 54.0, 9.0, 55.0, 8.0, 55.0});
        ringe.add(new double[] {10.0, 53.5, 11.0, 53.5, 11.0, 54.5});
        karte.setCoast(ringe, "Prüfdaten");
        check("Uebergebene Umrisse kommen an",
                karte.getChart().getModel().findLayer("Kueste").size() == 2,
                karte.getChart().getModel().findLayer("Kueste").size() + " Ringe");
        check("Die Stationen liegen weiterhin oben",
                zaehlePunkte(karte) == 8, "");

        // ---------------------------------------------------------------
        // Palette
        System.out.println();
        System.out.println("=== Palette ===");
        FStationMapBeanInfo bi = new FStationMapBeanInfo();
        check("Sinnbild 16 und 32 vorhanden",
                bi.getIcon(BeanInfo.ICON_COLOR_16x16) != null
                && bi.getIcon(BeanInfo.ICON_COLOR_32x32) != null, "");
        PropertyDescriptor[] pd = bi.getPropertyDescriptors();
        check("Eigenschaften beschrieben", pd.length >= 6, pd.length + " Stueck");
        int ohneText = 0;
        for (PropertyDescriptor d : pd) {
            if (d.getShortDescription() == null
                    || d.getShortDescription().equals(d.getName())) {
                ohneText++;
            }
        }
        check("Jede Eigenschaft hat einen deutschen Satz",
                ohneText == 0, ohneText + " ohne");

        // ---------------------------------------------------------------
        // Ein Standbild, damit man es ansehen kann
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            JFrame f = new JFrame();
            f.setUndecorated(true);
            f.getContentPane().add(karte);
            f.pack();
            f.setSize(1000, 640);
            f.setVisible(true);
            Thread.sleep(600);
            BufferedImage img = new BufferedImage(1000, 640,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 1000, 640);
            karte.paint(g);
            g.dispose();
            new File("bilder").mkdirs();
            ImageIO.write(img, "png", new File("bilder/karte.png"));
            f.dispose();
            System.out.println();
            System.out.println("Standbild: bilder/karte.png");
        }

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }

    static int zaehlePunkte(FStationMap k) {
        int n = 0;
        if (k.getChart().getModel().findLayer("Nordsee") != null) {
            n += k.getChart().getModel().findLayer("Nordsee").size();
        }
        if (k.getChart().getModel().findLayer("Ostsee") != null) {
            n += k.getChart().getModel().findLayer("Ostsee").size();
        }
        return n;
    }

    /** Grober Abstand in Kilometern, nur fuer die Pruefung. */
    static double kmAbstand(double lon, double lat, FTideStation s) {
        double dLat = (s.getLocation().getLatitudeDeg() - lat) * 111.32;
        double dLon = (s.getLocation().getLongitudeDeg() - lon) * 111.32
                * Math.cos(Math.toRadians(lat));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }
}
