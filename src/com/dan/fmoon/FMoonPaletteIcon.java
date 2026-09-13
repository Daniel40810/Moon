package com.dan.fmoon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * Malt die Sinnbilder fuer die NetBeans-Palette. Keine Bilddateien, sondern
 * gezeichnet - so bleibt die JAR eine einzige Datei ohne Ressourcenordner.
 *
 * <p>Hier wird die Sichel ausnahmsweise doch gemalt statt beleuchtet: Ein
 * Sinnbild von sechzehn Bildpunkten Kantenlaenge soll erkennbar sein, nicht
 * physikalisch richtig, und der volle Renderer waere fuer ein Palettenicon
 * viel zu langsam.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonPaletteIcon {

    private FMoonPaletteIcon() {
    }

    /**
     * Malt eine Mondsichel.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage crescent(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        double d = size * 0.84;
        double x = (size - d) / 2.0;
        double y = (size - d) / 2.0;

        Area scheibe = new Area(new Ellipse2D.Double(x, y, d, d));
        Area schatten = new Area(new Ellipse2D.Double(x + d * 0.30, y - d * 0.04,
                d * 1.02, d * 1.02));
        Area sichel = new Area(scheibe);
        sichel.subtract(schatten);

        g.setColor(new Color(28, 38, 50));
        g.fill(scheibe);
        g.setColor(new Color(232, 226, 208));
        g.fill(sichel);
        g.dispose();
        return img;
    }

    /**
     * Malt eine Tidenkurve mit einem Mond darueber.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    /**
     * Malt eine Kuestenlinie mit zwei Stationspunkten.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage map(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Land: eine Bucht, die von rechts hereinragt - grob die Form
        // der Deutschen Bucht, damit das Sinnbild nach Kueste aussieht
        // und nicht nach beliebigem Fleck.
        java.awt.geom.GeneralPath land = new java.awt.geom.GeneralPath();
        land.moveTo(size, 0);
        land.curveTo(size * 0.55, size * 0.18, size * 0.72, size * 0.46,
                     size * 0.34, size * 0.62);
        land.curveTo(size * 0.16, size * 0.72, size * 0.30, size * 0.88,
                     size * 0.20, size);
        land.lineTo(size, size);
        land.closePath();
        g.setColor(new Color(44, 62, 70));
        g.fill(land);
        g.setColor(new Color(84, 179, 187));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 18f)));
        g.draw(land);

        // Zwei Stationen und die Strecke dazwischen
        double r = Math.max(1.5, size * 0.09);
        double x1 = size * 0.22;
        double y1 = size * 0.30;
        double x2 = size * 0.48;
        double y2 = size * 0.74;
        g.setColor(new Color(127, 212, 222, 170));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 22f)));
        g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        g.setColor(new Color(232, 226, 208));
        g.fill(new Ellipse2D.Double(x1 - r, y1 - r, r * 2, r * 2));
        g.setColor(new Color(184, 148, 63));
        g.fill(new Ellipse2D.Double(x2 - r, y2 - r, r * 2, r * 2));

        g.dispose();
        return img;
    }

    /**
     * Malt die Erde mit einem Mond auf seiner Bahn.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage orbit(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        double cx = size * 0.52;
        double cy = size * 0.5;
        double a = size * 0.40;
        double b = a * 0.88;

        // Die Bahn
        g.setColor(new Color(58, 74, 87));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 22f), 0, 0, 10f,
                new float[] {Math.max(1.5f, size / 12f), Math.max(1.5f, size / 14f)}, 0f));
        g.draw(new Ellipse2D.Double(cx - a, cy - b, 2 * a, 2 * b));
        g.setStroke(new java.awt.BasicStroke(1f));

        // Erde, links beleuchtet
        double er = size * 0.15;
        halbHell(g, cx, cy, er, new Color(46, 110, 142));

        // Mond rechts auf der Bahn, ebenfalls links beleuchtet
        double mr = Math.max(1.5, size * 0.085);
        halbHell(g, cx + a, cy, mr, new Color(232, 226, 208));

        g.dispose();
        return img;
    }

    private static void halbHell(Graphics2D g, double cx, double cy, double r,
            Color hell) {
        Ellipse2D.Double scheibe =
                new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color((int) (hell.getRed() * 0.3),
                (int) (hell.getGreen() * 0.3), (int) (hell.getBlue() * 0.3)));
        g.fill(scheibe);
        Area tag = new Area(scheibe);
        tag.intersect(new Area(new java.awt.geom.Arc2D.Double(cx - r, cy - r,
                2 * r, 2 * r, 90, 180, java.awt.geom.Arc2D.PIE)));
        g.setColor(hell);
        g.fill(tag);
    }

    public static BufferedImage tide(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Wasser
        double basis = size * 0.62;
        java.awt.geom.GeneralPath welle = new java.awt.geom.GeneralPath();
        welle.moveTo(1, basis);
        for (int x = 1; x <= size - 1; x++) {
            double f = (x - 1) / (double) (size - 2);
            welle.lineTo(x, basis + Math.sin(f * Math.PI * 2.0) * size * 0.12);
        }
        welle.lineTo(size - 1, size - 1);
        welle.lineTo(1, size - 1);
        welle.closePath();
        g.setColor(new Color(38, 96, 106));
        g.fill(welle);
        g.setColor(new Color(84, 179, 187));
        g.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 16f)));
        g.draw(welle);

        // Mond darueber
        double d = size * 0.34;
        double mx = size * 0.60;
        double my = size * 0.10;
        Area scheibe = new Area(new Ellipse2D.Double(mx, my, d, d));
        Area schatten = new Area(new Ellipse2D.Double(mx + d * 0.34, my - d * 0.04,
                d * 1.02, d * 1.02));
        scheibe.subtract(schatten);
        g.setColor(new Color(232, 226, 208));
        g.fill(scheibe);

        g.dispose();
        return img;
    }

    /**
     * Malt eine Kurve mit Gitter.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage curve(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(70, 86, 102));
        for (int i = 1; i <= 2; i++) {
            int y = (int) Math.round(size * i / 3.0);
            g.drawLine(2, y, size - 2, y);
        }

        java.awt.geom.GeneralPath p = new java.awt.geom.GeneralPath();
        for (int x = 2; x <= size - 2; x++) {
            double f = (x - 2) / (double) (size - 4);
            double y = size * 0.5 - Math.sin(f * Math.PI * 4.0) * size * 0.30;
            if (x == 2) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        g.setColor(new Color(84, 179, 187));
        g.setStroke(new java.awt.BasicStroke(Math.max(1.2f, size / 14f),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(p);

        g.dispose();
        return img;
    }

    /**
     * Malt ein Kalenderblatt mit drei Mondphasen.
     *
     * @param size Kantenlaenge in Bildpunkten
     * @return Bild mit durchsichtigem Hintergrund
     */
    public static BufferedImage calendar(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(24, 33, 44));
        g.fillRoundRect(1, 2, size - 2, size - 3, 4, 4);
        g.setColor(new Color(96, 120, 146));
        g.drawRoundRect(1, 2, size - 3, size - 4, 4, 4);
        g.fillRect(1, 2, size - 2, Math.max(2, size / 6));

        int n = 3;
        double d = (size - 6) / (double) n;
        double[] anteile = {0.15, 0.55, 1.0};
        for (int i = 0; i < n; i++) {
            double cx = 3 + d * (i + 0.5);
            double cy = size * 0.62;
            double r = Math.max(1.5, d * 0.36);
            Area voll = new Area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(new Color(40, 52, 66));
            g.fill(voll);
            Area hell = new Area(voll);
            double versatz = (1.0 - anteile[i]) * 2.0 * r;
            hell.subtract(new Area(new Ellipse2D.Double(
                    cx - r + versatz, cy - r, 2 * r, 2 * r)));
            g.setColor(new Color(232, 226, 208));
            g.fill(hell);
        }
        g.dispose();
        return img;
    }
}
