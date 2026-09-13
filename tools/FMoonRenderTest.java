import com.dan.fmoon.render.FMoonRenderer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Prueft den Renderer ohne Fenster: Deckt die beleuchtete Flaeche das ab, was
 * der Phasenwinkel verlangt, liegt sie auf der richtigen Seite, dreht die
 * Libration in die richtige Richtung?
 */
public class FMoonRenderTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String what, double actual, double expected, double tol, String unit) {
        double diff = Math.abs(actual - expected);
        boolean ok = diff <= tol;
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("%s %-50s ist=%-12.4f soll=%-12.4f %s%n",
                ok ? "  OK  " : " FEHL ", what, actual, expected, unit);
    }

    static final Color SUNLIT = new Color(236, 229, 211);
    static final Color SHINE = new Color(60, 78, 96);

    /**
     * Anteil der GEZEICHNETEN Scheibe, der heller als die Schwelle ist.
     * Bezugsgroesse sind die deckenden Bildpunkte, nicht ein angenommener
     * Kreis - sonst misst man mit, wieviel Rand die Projektion frei laesst.
     */
    static double litFraction(BufferedImage img, int threshold) {
        int lit = 0;
        int disk = 0;
        int n = img.getWidth();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                disk++;
                int lum = ((argb >> 16 & 255) * 30 + (argb >> 8 & 255) * 59
                        + (argb & 255) * 11) / 100;
                if (lum > threshold) {
                    lit++;
                }
            }
        }
        return disk == 0 ? 0.0 : lit / (double) disk;
    }

    /** Schwerpunkt der hellen Flaeche, in Bildkoordinaten relativ zur Mitte. */
    static double[] litCentroid(BufferedImage img, int threshold) {
        double sx = 0.0;
        double sy = 0.0;
        double w = 0.0;
        int n = img.getWidth();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                int lum = ((argb >> 16 & 255) * 30 + (argb >> 8 & 255) * 59
                        + (argb & 255) * 11) / 100;
                if (lum > threshold) {
                    sx += (x - n / 2.0);
                    sy += (y - n / 2.0);
                    w += 1.0;
                }
            }
        }
        if (w == 0.0) {
            return new double[]{0, 0};
        }
        // Bild-y zeigt nach unten, deshalb Vorzeichen drehen
        return new double[]{sx / w / (n / 2.0), -sy / w / (n / 2.0)};
    }

    /** Mittlere Helligkeit eines kleinen Feldes um eine relative Bildstelle. */
    static double patchLuminance(BufferedImage img, double relX, double relY, int radius) {
        int n = img.getWidth();
        int cx = (int) Math.round(n / 2.0 + relX * n / 2.0);
        int cy = (int) Math.round(n / 2.0 - relY * n / 2.0);
        double sum = 0.0;
        int count = 0;
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || y < 0 || x >= n || y >= n) {
                    continue;
                }
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                sum += ((argb >> 16 & 255) * 30 + (argb >> 8 & 255) * 59
                        + (argb & 255) * 11) / 100.0;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    /** Hellster Bildpunkt der Scheibe. */
    static double maxLuminance(BufferedImage img) {
        double max = 0.0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                double lum = ((argb >> 16 & 255) * 30 + (argb >> 8 & 255) * 59
                        + (argb & 255) * 11) / 100.0;
                max = Math.max(max, lum);
            }
        }
        return max;
    }

    public static void main(String[] args) throws Exception {
        int n = 240;

        System.out.println("=== Beleuchtete Flaeche gegen den Phasenwinkel ===");
        // Der beleuchtete Anteil der SCHEIBE ist k = (1 + cos i)/2. Die
        // gezeichnete helle Flaeche muss dem folgen. Die Schwelle liegt
        // absichtlich dicht ueber null: Nahe am Terminator faellt die
        // Helligkeit gegen null, obwohl die Stelle geometrisch noch
        // beleuchtet ist - mit einer hohen Schwelle misst man den Abfall
        // und nicht die Flaeche.
        double[] winkel = {0.0, 45.0, 90.0, 135.0, 170.0};
        for (int w = 0; w < winkel.length; w++) {
            double i = winkel[w];
            BufferedImage img = FMoonRenderer.render(n, i, 90.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
            double k = (1.0 + Math.cos(Math.toRadians(i))) / 2.0;
            check("Phasenwinkel " + (int) i + " Grad: helle Flaeche",
                    litFraction(img, 3), k, 0.05, "");
        }

        System.out.println();
        System.out.println("=== Schmale Sichel ===");
        // Diese Pruefung steht hier, weil genau das einmal kaputt war: Mit
        // einer tesselierten Kugel verschwand die Sichel unter fuenf Prozent
        // Beleuchtung vollstaendig, weil sie schmaler war als eine Facette
        // am Scheibenrand. Eine Sichel von einem halben Prozent ist am Himmel
        // schwer, aber moeglich zu sehen - schwarz darf sie nie sein.
        double[] duenn = {0.005, 0.012, 0.024, 0.05};
        for (int w = 0; w < duenn.length; w++) {
            double i = Math.toDegrees(Math.acos(2.0 * duenn[w] - 1.0));
            BufferedImage img = FMoonRenderer.render(300, i, 61.0, 0, 0, 0,
                    0.0, SUNLIT, SHINE);
            check("Sichel bei " + (duenn[w] * 100.0) + " % ist deutlich sichtbar",
                    (maxLuminance(img) > 120.0) ? 1 : 0, 1, 0, "");
        }

        System.out.println();
        System.out.println("=== Lage der hellen Seite ===");
        // limbAngle 90 heisst: helle Seite nach links (oestlich).
        BufferedImage links = FMoonRenderer.render(n, 90.0, 90.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
        double[] cl = litCentroid(links, 12);
        check("helle Seite bei 90 Grad liegt links", cl[0], -0.42, 0.15, "");
        check("helle Seite bei 90 Grad liegt mittig in der Hoehe", cl[1], 0.0, 0.08, "");

        BufferedImage rechts = FMoonRenderer.render(n, 90.0, 270.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
        double[] cr = litCentroid(rechts, 12);
        check("helle Seite bei 270 Grad liegt rechts", cr[0], 0.42, 0.15, "");

        BufferedImage oben = FMoonRenderer.render(n, 90.0, 0.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
        double[] co = litCentroid(oben, 12);
        check("helle Seite bei 0 Grad liegt oben", co[1], 0.42, 0.15, "");
        check("helle Seite bei 0 Grad liegt mittig in der Breite", co[0], 0.0, 0.08, "");

        System.out.println();
        System.out.println("=== Erdschein ===");
        BufferedImage ohne = FMoonRenderer.render(n, 160.0, 90.0, 0, 0, 0, 0.00, SUNLIT, SHINE);
        BufferedImage mit = FMoonRenderer.render(n, 160.0, 90.0, 0, 0, 0, 0.30, SUNLIT, SHINE);
        double dunkelOhne = patchLuminance(ohne, 0.45, 0.0, 8);
        double dunkelMit = patchLuminance(mit, 0.45, 0.0, 8);
        check("Nachtseite ohne Erdschein ist schwarz", dunkelOhne, 0.0, 1.0, "");
        check("Nachtseite mit Erdschein leuchtet schwach",
                (dunkelMit > 4.0 && dunkelMit < 70.0) ? 1 : 0, 1, 0, "");
        // Die beleuchtete Seite darf sich nicht aendern. Geprueft wird das
        // am Halbmond und nicht an der schmalen Sichel: Bei einer Sichel von
        // drei Prozent liegt jeder Bildpunkt dicht am Terminator, und dort
        // setzt der Erdschein zu Recht schon ein.
        BufferedImage halbOhne = FMoonRenderer.render(n, 90.0, 90.0, 0, 0, 0, 0.00,
                SUNLIT, SHINE);
        BufferedImage halbMit = FMoonRenderer.render(n, 90.0, 90.0, 0, 0, 0, 0.30,
                SUNLIT, SHINE);
        check("Erdschein laesst die Tagseite unveraendert (hellster Punkt)",
                maxLuminance(halbMit), maxLuminance(halbOhne), 0.01, "");
        check("Erdschein laesst die Tagseite unveraendert (Mitte der Sichel)",
                patchLuminance(halbMit, -0.5, 0.0, 6),
                patchLuminance(halbOhne, -0.5, 0.0, 6), 0.01, "");
        check("Erdschein hellt die Nachtseite des Halbmondes auf",
                (patchLuminance(halbMit, 0.5, 0.0, 6)
                > patchLuminance(halbOhne, 0.5, 0.0, 6) + 3.0) ? 1 : 0, 1, 0, "");

        System.out.println();
        System.out.println("=== Oberflaechenzeichnung und Libration ===");
        // Mare Crisium liegt bei 59 Grad oestlicher Laenge, also am linken
        // Rand der Scheibe. Dort muss es dunkler sein als im Hochland darunter.
        BufferedImage voll = FMoonRenderer.render(n, 0.0, 0.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
        double crisium = patchLuminance(voll, -0.72, 0.28, 5);
        double hochland = patchLuminance(voll, -0.62, -0.55, 5);
        check("Mare Crisium ist dunkler als das Hochland",
                (crisium < hochland - 8.0) ? 1 : 0, 1, 0, "");

        // Tycho mit seinem Strahlensystem liegt sued-westlich und ist hell.
        double tycho = patchLuminance(voll, 0.14, -0.66, 4);
        check("Tycho ist heller als das umgebende Hochland",
                (tycho > hochland + 4.0) ? 1 : 0, 1, 0, "");

        // Libration nach Osten schiebt die Zeichnung sichtbar zur Seite.
        BufferedImage libOst = FMoonRenderer.render(n, 0.0, 0.0, 7.5, 0, 0, 0.0, SUNLIT, SHINE);
        double unterschied = 0.0;
        for (int y = 40; y < n - 40; y += 3) {
            for (int x = 40; x < n - 40; x += 3) {
                unterschied += Math.abs((voll.getRGB(x, y) & 255)
                        - (libOst.getRGB(x, y) & 255));
            }
        }
        unterschied /= ((n - 80) / 3.0) * ((n - 80) / 3.0);
        check("Libration veraendert das Bild sichtbar",
                (unterschied > 2.0) ? 1 : 0, 1, 0, "");

        System.out.println();
        System.out.println("=== Rand und Durchsichtigkeit ===");
        BufferedImage scheibe = FMoonRenderer.render(n, 0.0, 0.0, 0, 0, 0, 0.0, SUNLIT, SHINE);
        int eckeAlpha = scheibe.getRGB(2, 2) >>> 24;
        check("Ecke ist durchsichtig", eckeAlpha, 0, 0, "");
        int mitteAlpha = scheibe.getRGB(n / 2, n / 2) >>> 24;
        check("Mitte ist deckend", mitteAlpha, 255, 0, "");
        check("Vollmond ist ueberall hell", litFraction(scheibe, 3), 1.0, 0.01, "");

        System.out.println();
        System.out.println("=== Zwischenspeicher ===");
        long t0 = System.nanoTime();
        FMoonRenderer.render(200, 33.0, 77.0, 1, 2, 3, 0.2, SUNLIT, SHINE);
        long ersteMs = (System.nanoTime() - t0) / 1000000;
        t0 = System.nanoTime();
        FMoonRenderer.render(200, 33.0, 77.0, 1, 2, 3, 0.2, SUNLIT, SHINE);
        long zweiteMs = (System.nanoTime() - t0) / 1000000;
        System.out.printf("       erstes Bild %d ms, zweites %d ms%n", ersteMs, zweiteMs);
        check("zweites Bild kommt aus dem Zwischenspeicher",
                (zweiteMs <= 2) ? 1 : 0, 1, 0, "");

        // Kontaktbogen zum Ansehen
        int kachel = 150;
        BufferedImage blatt = new BufferedImage(kachel * 8, kachel * 2,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blatt.createGraphics();
        g.setColor(new Color(12, 18, 25));
        g.fillRect(0, 0, blatt.getWidth(), blatt.getHeight());
        for (int s = 0; s < 8; s++) {
            double lon = s / 8.0 * 360.0;
            double i = Math.abs(180.0 - lon);
            double limb = (lon < 180.0) ? 90.0 : 270.0;
            BufferedImage m = FMoonRenderer.render(kachel - 16, i, limb,
                    0, 0, 0, 0.22, SUNLIT, SHINE);
            g.drawImage(m, s * kachel + 8, 8, null);
        }
        for (int s = 0; s < 8; s++) {
            double l = -7.5 + s * 15.0 / 7.0;
            BufferedImage m = FMoonRenderer.render(kachel - 16, 25.0, 60.0,
                    l, l * 0.8, s * 4.0 - 14.0, 0.0, SUNLIT, SHINE);
            g.drawImage(m, s * kachel + 8, kachel + 8, null);
        }
        g.dispose();
        new File("build").mkdirs();
        ImageIO.write(blatt, "png", new File("build/moon_contact.png"));
        System.out.println("Kontaktbogen: build/moon_contact.png");

        System.out.println();
        System.out.printf("Ergebnis: %d von %d Pruefungen gruen%n", pass, pass + fail);
        if (fail > 0) {
            System.out.println("ES GIBT FEHLER.");
            System.exit(1);
        }
    }
}
