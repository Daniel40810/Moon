import com.dan.fmoon.FMoonDemo;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Baut das Demo-Fenster auf, vermisst jede Bedienreihe und legt ein Standbild
 * ab.
 *
 * <p>Die Vermessung gibt es, weil eine zu breite FlowLayout-Reihe in einem
 * BoxLayout umbricht, aber nur die Hoehe EINER Zeile bekommt - die zweite
 * Zeile wird lautlos abgeschnitten, ohne Fehler und ohne Ausnahme. Dieser
 * Fehler hat in einem frueheren Projekt monatelang unbemerkt drei Knoepfe
 * verschluckt.</p>
 */
public class FMoonDemoTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String what, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-52s %s%n", ok ? "  OK  " : " FEHL ", what, detail);
    }

    public static void main(String[] args) throws Exception {
        final int breite = 1320;
        final int hoehe = 880;
        final BufferedImage[] bild = new BufferedImage[1];
        final BufferedImage[] bild2 = new BufferedImage[1];
        final int[] reihen = new int[1];

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                FTheme.getInstance().applyPreset(FThemePreset.LABORATORY_DARK);
                JPanel inhalt = new FMoonDemo().buildContent();
                JFrame f = new JFrame();
                f.setUndecorated(true);
                f.getContentPane().add(inhalt);
                f.setSize(breite, hoehe);
                // Ohne sichtbares Fenster validiert Swing nichts, und
                // doLayout erreicht nur die direkten Kinder. Deshalb wird das
                // Fenster unter Xvfb wirklich angezeigt.
                f.setVisible(true);
                f.validate();

                pruefeReihen(inhalt, breite);
                reihen[0] = zaehleReihen(inhalt);

                BufferedImage img = new BufferedImage(breite, hoehe,
                        BufferedImage.TYPE_INT_RGB);
                inhalt.paint(img.getGraphics());
                bild[0] = img;

                // zweites Standbild: die Gezeitenseite
                javax.swing.JTabbedPane reiter = findeReiter(inhalt);
                if (reiter != null && reiter.getTabCount() > 1) {
                    reiter.setSelectedIndex(1);
                    f.validate();
                    BufferedImage img2 = new BufferedImage(breite, hoehe,
                            BufferedImage.TYPE_INT_RGB);
                    inhalt.paint(img2.getGraphics());
                    bild2[0] = img2;
                }
            }
        });

        check("Bedienreihen gefunden", reihen[0] >= 4, reihen[0] + " Reihen");

        new File("build").mkdirs();
        ImageIO.write(bild[0], "png", new File("build/demo.png"));
        System.out.println("Standbild: build/demo.png");
        if (bild2[0] != null) {
            ImageIO.write(bild2[0], "png", new File("build/demo_gezeiten.png"));
            System.out.println("Standbild: build/demo_gezeiten.png");
            check("Gezeitenseite vorhanden", true, "");
        } else {
            check("Gezeitenseite vorhanden", false, "kein Reiter gefunden");
        }

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
        System.exit(0);
    }

    static javax.swing.JTabbedPane findeReiter(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JTabbedPane) {
                return (javax.swing.JTabbedPane) k;
            }
            if (k instanceof Container) {
                javax.swing.JTabbedPane t = findeReiter((Container) k);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    static int zaehleReihen(Container c) {
        int n = 0;
        for (Component k : c.getComponents()) {
            if (k.getName() != null && k instanceof JPanel) {
                n++;
            }
            if (k instanceof Container) {
                n += zaehleReihen((Container) k);
            }
        }
        return n;
    }

    static void pruefeReihen(Container c, int fensterBreite) {
        for (Component k : c.getComponents()) {
            if (k instanceof JPanel && k.getName() != null) {
                JPanel reihe = (JPanel) k;
                int gebraucht = 0;
                boolean alleHabenGroesse = true;
                int hoechstes = 0;
                for (Component e : reihe.getComponents()) {
                    Dimension d = e.getPreferredSize();
                    if (d.width <= 0 || d.height <= 0) {
                        alleHabenGroesse = false;
                    }
                    gebraucht += d.width + 8;
                    hoechstes = Math.max(hoechstes, d.height);
                }
                int verfuegbar = reihe.getWidth() > 0 ? reihe.getWidth() : fensterBreite;
                double auslastung = gebraucht / (double) verfuegbar;
                check("Reihe \"" + reihe.getName() + "\" passt in ihre Breite",
                        auslastung <= 0.95,
                        String.format("braucht %d von %d px (%.0f %%)",
                                gebraucht, verfuegbar, auslastung * 100.0));
                check("Reihe \"" + reihe.getName() + "\": alle Elemente haben Groesse",
                        alleHabenGroesse, reihe.getComponentCount() + " Elemente");
                check("Reihe \"" + reihe.getName() + "\" ist hoch genug",
                        reihe.getHeight() >= hoechstes,
                        "Reihe " + reihe.getHeight() + " px, hoechstes Element "
                        + hoechstes + " px");
                // nichts ragt seitlich hinaus
                boolean drin = true;
                for (Component e : reihe.getComponents()) {
                    if (e.getX() + e.getWidth() > reihe.getWidth() + 1) {
                        drin = false;
                    }
                }
                check("Reihe \"" + reihe.getName() + "\": nichts ragt hinaus", drin, "");
            }
            if (k instanceof Container) {
                pruefeReihen((Container) k, fensterBreite);
            }
        }
    }
}
