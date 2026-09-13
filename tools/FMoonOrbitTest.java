import com.dan.fmoon.FOrbitView;
import com.dan.fmoon.FOrbitViewBeanInfo;
import com.dan.fmoon.astro.FAstroTime;
import com.dan.fmoon.astro.FGeoLocation;
import com.dan.fmoon.astro.FMoonEphemeris;
import com.dan.fmoon.astro.FMoonPhaseTimes;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Prueft das Erklaerbild - und zwar nicht, ob es huebsch ist, sondern ob
 * es die Wahrheit sagt.
 *
 * <p>Ein Schaubild laesst sich schlecht pruefen, eine Zahl gut. Deshalb
 * gibt {@code FOrbitView} heraus, was es zeichnet: den Winkel
 * Sonne-Erde-Mond und den Bahnradius an dieser Stelle. Daran haengen die
 * beiden Aussagen, die dieses Bild ueberhaupt macht.</p>
 */
public class FMoonOrbitTest {

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

    static void checkNum(String was, double ist, double soll, double tol,
            String einheit) {
        boolean ok = Math.abs(ist - soll) <= tol;
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-58s ist=%-12.4f soll=%-12.4f %s%n",
                ok ? "  OK  " : " FEHL ", was, ist, soll, einheit);
    }

    public static void main(String[] args) throws Exception {
        FOrbitView v = new FOrbitView();

        // ---------------------------------------------------------------
        // Die eine Aussage: der Winkel links erklaert die Phase rechts.
        //
        // Wenn beide Bilder aus derselben Rechnung stammen, MUSS der
        // Winkel bei Neumond null sein, bei Vollmond 180 und bei den
        // Vierteln 90 und 270. Stimmte das nicht, waere das Bild eine
        // huebsche Behauptung.
        System.out.println("=== Winkel und Phase gehoeren zusammen ===");
        List<FMoonPhaseTimes.Phase> phasen = FMoonPhaseTimes.between(
                FAstroTime.julianDay(2026, 1, 1.0),
                FAstroTime.julianDay(2027, 1, 1.0));
        double groessteAbw = 0.0;
        int geprueft = 0;
        for (int i = 0; i < phasen.size(); i++) {
            FMoonPhaseTimes.Phase p = phasen.get(i);
            v.setTimeMillis(p.millis());
            double soll = p.type * 90.0;
            double ist = v.getElongationDeg();
            double d = Math.abs(((ist - soll + 540.0) % 360.0) - 180.0);
            groessteAbw = Math.max(groessteAbw, d);
            geprueft++;
        }
        check("Alle Phasen eines Jahres geprueft", geprueft >= 48,
                geprueft + " Zeitpunkte");
        checkNum("Groesste Abweichung des Winkels", groessteAbw, 0.0, 0.02,
                "Grad");

        // ---------------------------------------------------------------
        // Die andere Aussage: der Mond liegt auf seiner Bahn.
        //
        // Die gezeichnete Ellipse hat die echte Exzentrizitaet und ist in
        // die echte Richtung der Erdnaehe gedreht. Der Mond wird mit
        // seinem echten Abstand gesetzt. Beides muss zusammenpassen -
        // sonst zeichnet das Bild eine Bahn und setzt den Koerper daneben.
        //
        // Ganz genau kann es nicht stimmen, und das ist der Witz daran:
        // Die Sonne stoert die Mondbahn um mehrere tausend Kilometer. Die
        // Abweichung wird deshalb nicht auf null geprueft, sondern auf
        // die Groessenordnung, die die Stoerungen haben.
        System.out.println();
        System.out.println("=== Der Mond liegt auf der gezeichneten Bahn ===");
        double summe = 0.0;
        double max = 0.0;
        int n = 0;
        for (double jd = FAstroTime.julianDay(2026, 1, 1.0);
                jd < FAstroTime.julianDay(2028, 1, 1.0); jd += 0.5) {
            long ms = FAstroTime.millisFromJulianDay(jd);
            v.setTimeMillis(ms);
            FMoonEphemeris e = FMoonEphemeris.at(ms,
                    FGeoLocation.TIMMENDORFER_STRAND);
            double ist = e.moon.distanceKm / FOrbitView.MEAN_DISTANCE_KM;
            double abw = Math.abs(ist - v.getEllipseRadiusFactor());
            summe += abw;
            max = Math.max(max, abw);
            n++;
        }
        double mittelKm = summe / n * FOrbitView.MEAN_DISTANCE_KM;
        double maxKm = max * FOrbitView.MEAN_DISTANCE_KM;
        check("Ueber zwei Jahre gemessen", n > 1400, n + " Zeitpunkte");
        check("Im Mittel liegt der Mond auf der Linie", mittelKm < 4000,
                String.format("%.0f km", mittelKm));
        check("Und im schlimmsten Fall nicht weiter weg als die Stoerungen",
                maxKm < 9000, String.format("%.0f km", maxKm));
        // Und sie darf auch nicht NULL sein: Waere die Abweichung null,
        // haetten wir den Abstand aus der Ellipse genommen statt aus der
        // Rechnung - das Bild zeigte dann eine Bahn, die es nicht gibt.
        check("Aber sie ist auch nicht null - der Mond folgt keiner Ellipse",
                mittelKm > 500, String.format("%.0f km", mittelKm));

        // ---------------------------------------------------------------
        // Erdnaehe und Erdferne
        System.out.println();
        System.out.println("=== Erdnaehe und Erdferne ===");
        double naeheFaktor = Double.MAX_VALUE;
        double ferneFaktor = 0.0;
        for (double jd = FAstroTime.julianDay(2026, 1, 1.0);
                jd < FAstroTime.julianDay(2026, 3, 1.0); jd += 0.25) {
            v.setTimeMillis(FAstroTime.millisFromJulianDay(jd));
            double f = v.getEllipseRadiusFactor();
            naeheFaktor = Math.min(naeheFaktor, f);
            ferneFaktor = Math.max(ferneFaktor, f);
        }
        checkNum("Kleinster Bahnradius ist 1 minus e", naeheFaktor,
                1.0 - FOrbitView.ECCENTRICITY, 0.002, "Halbachsen");
        checkNum("Groesster Bahnradius ist 1 plus e", ferneFaktor,
                1.0 + FOrbitView.ECCENTRICITY, 0.002, "Halbachsen");

        // ---------------------------------------------------------------
        // Palette
        System.out.println();
        System.out.println("=== Palette ===");
        FOrbitViewBeanInfo bi = new FOrbitViewBeanInfo();
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
        check("Jede Eigenschaft hat einen deutschen Satz", ohneText == 0,
                ohneText + " ohne");

        // Der Hinweis zum Massstab ist voreingestellt an. Wer ihn
        // abschaltet, macht das Bild huebscher und unehrlicher - aber
        // voreingestellt aus waere die falsche Entscheidung.
        check("Der Massstabshinweis ist voreingestellt sichtbar",
                new FOrbitView().isShowScaleNote(), "");

        // ---------------------------------------------------------------
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            FOrbitView bild = new FOrbitView();
            bild.setTimeMillis(phasen.get(3).millis());
            JFrame f = new JFrame();
            f.setUndecorated(true);
            f.getContentPane().add(bild);
            f.pack();
            f.setSize(1000, 380);
            f.setVisible(true);
            Thread.sleep(500);
            BufferedImage img = new BufferedImage(1000, 380,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 1000, 380);
            bild.paint(g);
            g.dispose();
            new File("bilder").mkdirs();
            ImageIO.write(img, "png", new File("bilder/bahn_einzeln.png"));
            f.dispose();
            System.out.println();
            System.out.println("Standbild: bilder/bahn_einzeln.png");
        }

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }
}
