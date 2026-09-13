package com.dan.fmoon.render;

import com.dan.rayphong.Rasterizer;
import com.dan.rayphong.Vec3;
import java.awt.Color;

/**
 * Der Fragmentschattierer fuer die Mondkugel.
 *
 * <p>Bewusst kein Phong: Der Mond ist kein glaenzender Koerper und auch kein
 * Lambert-Streuer. Ein Lambert-Modell laesst den Vollmond zum Rand hin
 * dunkler werden, was er nachweislich nicht tut - er erscheint als flache
 * Scheibe von fast gleichmaessiger Helligkeit. Verwendet wird deshalb eine
 * Mischung aus dem Lommel-Seeliger-Gesetz, das genau diese Flachheit liefert,
 * und einem kleinen Lambert-Anteil, der der Sichel etwas Rundung zurueckgibt.
 * Das ist die uebliche vereinfachte Form des Hapke-Modells.</p>
 *
 * <p>Die Oberflaechenzeichnung wird nicht ueber eine Texturkoordinate geholt,
 * sondern aus der Normalen zurueckgerechnet: Die Normale wird mit der
 * transponierten Drehmatrix in das Koerpersystem gedreht, daraus ergeben sich
 * selenografische Laenge und Breite, und {@link FMoonSurface} liefert das
 * Albedo. So muss die UV-Belegung der Kugel nicht bekannt sein, und die
 * Libration ist automatisch richtig, weil sie in derselben Drehmatrix steckt.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonShader implements Rasterizer.FragmentShader {

    private final FMoonSurface surface;
    private final Vec3 lightDir;
    private final Vec3 viewDir;
    private final float[] rot;          // Drehmatrix, zeilenweise 3x3
    private final float earthshine;
    private final float baseR;
    private final float baseG;
    private final float baseB;
    private final float shineR;
    private final float shineG;
    private final float shineB;
    private final float exposure;

    /**
     * Legt einen Schattierer an.
     *
     * @param surface    Oberflaechenzeichnung
     * @param lightDir   Richtung vom Mond zur Sonne, normiert
     * @param viewDir    Richtung vom Mond zum Beobachter, normiert
     * @param rotation   Drehmatrix der Libration, zeilenweise als neun Werte
     * @param earthshine Staerke des Erdscheins auf der Nachtseite, 0 bis 1
     * @param sunlit     Farbe der beleuchteten Seite
     * @param shine      Farbe des Erdscheins
     * @param exposure   Helligkeitsfaktor
     */
    public FMoonShader(FMoonSurface surface, Vec3 lightDir, Vec3 viewDir,
            float[] rotation, float earthshine, Color sunlit, Color shine,
            float exposure) {
        this.surface = surface;
        this.lightDir = lightDir.normalize();
        this.viewDir = viewDir.normalize();
        this.rot = rotation;
        this.earthshine = earthshine;
        this.baseR = sunlit.getRed() / 255.0f;
        this.baseG = sunlit.getGreen() / 255.0f;
        this.baseB = sunlit.getBlue() / 255.0f;
        this.shineR = shine.getRed() / 255.0f;
        this.shineG = shine.getGreen() / 255.0f;
        this.shineB = shine.getBlue() / 255.0f;
        this.exposure = exposure;
    }

    @Override
    public int shade(Vec3 worldPos, Vec3 normal) {
        Vec3 n = normal.normalize();

        // Normale zurueck in das Koerpersystem: R hoch minus eins = R transponiert
        float ox = rot[0] * n.x + rot[3] * n.y + rot[6] * n.z;
        float oy = rot[1] * n.x + rot[4] * n.y + rot[7] * n.z;
        float oz = rot[2] * n.x + rot[5] * n.y + rot[8] * n.z;

        double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, oy))));
        double lon = Math.toDegrees(Math.atan2(ox, oz));
        double albedo = surface.albedoAt(lon, lat);

        double mu0 = n.dot(lightDir);     // Kosinus des Einfallswinkels
        double mu = n.dot(viewDir);       // Kosinus des Ausfallswinkels

        double r;
        double g;
        double b;

        if (mu0 > 0.0) {
            // Lommel-Seeliger: mu0 / (mu0 + mu); dazu ein Achtel Lambert.
            //
            // Der Nenner wird nach unten begrenzt. Am Scheibenrand geht mu
            // gegen null, und genau dort liegt die schmale Sichel kurz vor
            // und nach Neumond. Eine harte Bedingung "mu groesser null" laesst
            // die Sichel unter fuenf Prozent Beleuchtung vollstaendig
            // verschwinden - der Mond ist dann schwarz, obwohl am Himmel eine
            // deutliche Sichel steht.
            double ls = mu0 / (mu0 + Math.max(mu, 0.06));
            double lam = mu0;
            double refl = albedo * (0.86 * ls + 0.14 * lam) * 2.0;
            refl *= exposure;
            r = refl * baseR;
            g = refl * baseG;
            b = refl * baseB;
        } else {
            r = 0.0;
            g = 0.0;
            b = 0.0;
        }

        // Erdschein: die Nachtseite wird vom Licht der Erde getroffen, das aus
        // der Richtung des Beobachters kommt. Dadurch ist die dunkle Seite bei
        // schmaler Sichel schwach, aber deutlich zu sehen - das aschgraue Licht.
        if (earthshine > 0.0f && mu > 0.0) {
            double dunkel = (mu0 > 0.0) ? 0.0 : 1.0;
            // weicher Uebergang ueber den Terminator
            if (mu0 > -0.08 && mu0 <= 0.0) {
                dunkel = -mu0 / 0.08;
            }
            double es = earthshine * albedo * mu * dunkel;
            r += es * shineR;
            g += es * shineG;
            b += es * shineB;
        }

        int ir = clamp(r);
        int ig = clamp(g);
        int ib = clamp(b);
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    private static int clamp(double v) {
        int i = (int) Math.round(v * 255.0);
        if (i < 0) {
            return 0;
        }
        if (i > 255) {
            return 255;
        }
        return i;
    }
}
