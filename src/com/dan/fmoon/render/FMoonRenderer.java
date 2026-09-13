package com.dan.fmoon.render;

import com.dan.rayphong.Mat4;
import com.dan.rayphong.Vec3;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zeichnet die Mondscheibe als beleuchtete Kugel.
 *
 * <p>Die Phase wird nicht gemalt, sondern beleuchtet: Eine Kugel aus
 * {@link MeshFactory#sphere} wird mit dem {@link FMoonShader} schattiert, und
 * die Sonne steht dabei unter genau dem Phasenwinkel, den der Rechenkern
 * liefert. Terminator, Sichelform und die Verkuerzung der Oberflaechenzeichnung
 * zum Rand hin ergeben sich daraus von selbst.</p>
 *
 * <p>Die Abbildung ist orthografisch, nicht perspektivisch: Der Mond ist
 * 384000 km entfernt und 1737 km gross, das Verhaeltnis betraegt 1 zu 220.
 * Eine perspektivische Kamera wuerde ihn merklich falsch verzerren.</p>
 *
 * <p>Fertige Bilder werden zwischengespeichert. Der Schluessel enthaelt die
 * gerundeten Winkel; fuer den Kalender, der zwoelf Monde nebeneinander stellt,
 * faellt dadurch fast jede Zeichnung nur einmal an.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonRenderer {

    private static final FMoonSurface SURFACE = new FMoonSurface();

    private static final int CACHE_LIMIT = 96;

    private static final Map<String, BufferedImage> CACHE =
            new LinkedHashMap<String, BufferedImage>(32, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    private FMoonRenderer() {
    }

    /**
     * Zeichnet eine Mondscheibe.
     *
     * @param size       Kantenlaenge des quadratischen Bildes in Bildpunkten
     * @param phaseAngle Phasenwinkel in Grad (0 = Vollmond, 180 = Neumond)
     * @param limbAngle  Richtung der hellen Seite im Bild in Grad, von oben
     *                   gegen den Uhrzeigersinn gezaehlt
     * @param librLon    Libration in Laenge in Grad
     * @param librLat    Libration in Breite in Grad
     * @param axisAngle  Positionswinkel der Rotationsachse in Grad
     * @param earthshine Staerke des Erdscheins, 0 bis 1
     * @param sunlit     Farbe der beleuchteten Seite
     * @param shine      Farbe des Erdscheins
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage render(int size, double phaseAngle, double limbAngle,
            double librLon, double librLat, double axisAngle,
            double earthshine, Color sunlit, Color shine) {
        String key = size + "|" + Math.round(phaseAngle * 4.0)
                + "|" + Math.round(limbAngle * 2.0)
                + "|" + Math.round(librLon * 4.0)
                + "|" + Math.round(librLat * 4.0)
                + "|" + Math.round(axisAngle * 2.0)
                + "|" + Math.round(earthshine * 50.0)
                + "|" + sunlit.getRGB() + "|" + shine.getRGB();
        synchronized (CACHE) {
            BufferedImage hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
        }

        BufferedImage img = draw(size, phaseAngle, limbAngle, librLon, librLat,
                axisAngle, earthshine, sunlit, shine);
        synchronized (CACHE) {
            CACHE.put(key, img);
        }
        return img;
    }

    /**
     * Leert den Bildzwischenspeicher. Nach einem Themenwechsel aufzurufen.
     */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static BufferedImage draw(int size, double phaseAngle, double limbAngle,
            double librLon, double librLat, double axisAngle,
            double earthshine, Color sunlit, Color shine) {

        // Zweifache Ueberabtastung gegen Treppen am Scheibenrand. Das kostet
        // die vierfache Rechenzeit, faellt wegen des Zwischenspeichers aber
        // nur einmal je Bild an.
        int ss = 2;
        int big = size * ss;
        BufferedImage img = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);

        // Drehung der Libration. Sie wird mit den Erzeugern der Bibliothek
        // gebaut und anschliessend als reine 3x3-Matrix abgelesen, damit die
        // Drehrichtung der Bibliothek nicht vorausgesetzt werden muss.
        Mat4 model = Mat4.rotationZ((float) -Math.toRadians(axisAngle))
                .multiply(Mat4.rotationX((float) Math.toRadians(librLat)))
                .multiply(Mat4.rotationY((float) -Math.toRadians(librLon)));
        float[] rot = matrixOf(model);

        // Richtung zur Sonne: um den Phasenwinkel aus der Blickrichtung
        // herausgedreht, und zwar in die Richtung, in der die helle Seite
        // im Bild liegen soll. Oben ist +y, links ist -x (oestliche Seite).
        double i = Math.toRadians(phaseAngle);
        double th = Math.toRadians(limbAngle);
        Vec3 light = new Vec3(
                (float) (-Math.sin(i) * Math.sin(th)),
                (float) (Math.sin(i) * Math.cos(th)),
                (float) Math.cos(i));
        Vec3 viewDir = new Vec3(0f, 0f, 1f);

        FMoonShader shader = new FMoonShader(SURFACE, light, viewDir, rot,
                (float) earthshine, sunlit, shine, 0.86f);

        // Die Kugel wird NICHT tesseliert und rasterisiert, sondern je
        // Bildpunkt geschnitten. Bei orthografischer Abbildung ist das eine
        // Wurzel: Zu jedem Punkt (x, y) im Einheitskreis gehoert die Normale
        // (x, y, Wurzel aus 1 - x^2 - y^2), und die ist exakt.
        //
        // Der Umweg ueber MeshFactory und Rasterizer wurde ausprobiert und
        // verworfen: Eine Kugel aus 128 mal 96 Flaechen hat am Rand Facetten
        // von knapp drei Grad Breite, und genau dort liegt die Sichel kurz
        // vor und nach Neumond. Ihre Kante bekam dadurch eine sichtbare
        // Treppe, und unter fuenf Prozent Beleuchtung verschwand die Sichel
        // vollstaendig - der Mond war schwarz, obwohl am Himmel eine deutliche
        // Sichel stand. Der Schnitt ist zudem schneller, weil weder Dreiecke
        // noch ein Tiefenspeicher anfallen.
        double halb = 1.0 / 0.98;      // etwas Luft zum Bildrand
        int[] zeile = new int[big];
        for (int py = 0; py < big; py++) {
            double y = (1.0 - 2.0 * (py + 0.5) / big) * halb;
            for (int px = 0; px < big; px++) {
                double x = (2.0 * (px + 0.5) / big - 1.0) * halb;
                double r2 = x * x + y * y;
                if (r2 >= 1.0) {
                    zeile[px] = 0;
                    continue;
                }
                float nz = (float) Math.sqrt(1.0 - r2);
                Vec3 n = new Vec3((float) x, (float) y, nz);
                zeile[px] = shader.shade(n, n);
            }
            img.setRGB(0, py, big, 1, zeile, 0, big);
        }

        return downsample(img, size, ss);
    }

    /**
     * Mittelt das ueberabgetastete Bild auf die gewuenschte Groesse herunter.
     * Gemittelt wird mit dem Alphawert gewichtet, sonst zoege der
     * durchsichtige Hintergrund die Randfarben ins Schwarze.
     */
    private static BufferedImage downsample(BufferedImage src, int size, int ss) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int n = ss * ss;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sa = 0;
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        int p = src.getRGB(x * ss + dx, y * ss + dy);
                        int a = p >>> 24;
                        sa += a;
                        sr += (p >> 16 & 255) * a;
                        sg += (p >> 8 & 255) * a;
                        sb += (p & 255) * a;
                    }
                }
                if (sa == 0) {
                    out.setRGB(x, y, 0);
                } else {
                    int a = sa / n;
                    int r = sr / sa;
                    int g = sg / sa;
                    int b = sb / sa;
                    out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        return out;
    }

    /**
     * Liest die 3x3-Drehung einer Mat4 ab, indem die drei Basisvektoren
     * transformiert werden. Die Speicherordnung der Bibliothek muss dafuer
     * nicht bekannt sein.
     *
     * @param m Drehmatrix als Mat4
     * @return dieselbe Drehung als neun Werte, zeilenweise
     */
    static float[] matrixOf(Mat4 m) {
        Vec3 bx = m.transformDirection(new Vec3(1f, 0f, 0f));
        Vec3 by = m.transformDirection(new Vec3(0f, 1f, 0f));
        Vec3 bz = m.transformDirection(new Vec3(0f, 0f, 1f));
        return new float[]{
            bx.x, by.x, bz.x,
            bx.y, by.y, bz.y,
            bx.z, by.z, bz.z
        };
    }

    /**
     * Die Oberflaechenzeichnung, die alle gezeichneten Monde teilen.
     *
     * @return Oberflaeche
     */
    public static FMoonSurface surface() {
        return SURFACE;
    }
}
