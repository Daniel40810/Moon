import com.dan.fmoon.*;
import com.dan.fmoon.tide.*;
import com.dan.fstyle.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import javax.swing.*;

/**
 * Legt zwei Standbilder ab: den Gegensatz Nordsee gegen Ostsee und die
 * Monatskurve mit dem Mondband, an dem man Spring- und Nippzeit sieht.
 */
public class FMoonTideShot {

    static BufferedImage schuss(JComponent c, int w, int h) throws Exception {
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.getContentPane().add(c);
        f.setSize(w, h);
        f.setVisible(true);
        f.validate();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        c.paint(img.getGraphics());
        f.dispose();
        return img;
    }

    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    FTheme.getInstance().applyPreset(FThemePreset.LABORATORY_DARK);
                    new File("build").mkdirs();

                    // Nordsee gegen Ostsee, gleiche Skala
                    JPanel paar = new JPanel(new GridLayout(1, 2, 10, 0));
                    paar.setBackground(FTheme.getInstance().getBackground());
                    FTideBoard nord = new FTideBoard();
                    nord.setStation(FTideStation.BUESUM);
                    FTideBoard ost = new FTideBoard();
                    ost.setStation(FTideStation.TIMMENDORFER_STRAND);
                    paar.add(nord);
                    paar.add(ost);
                    ImageIO.write(schuss(paar, 1000, 400), "png",
                            new File("build/tide_nord_ost.png"));

                    // Monatskurve mit Mondband
                    FTideCurve monat = new FTideCurve();
                    monat.setStation(FTideStation.BUESUM);
                    monat.setSpanHours(720);
                    monat.setOffsetHours(-120.0);
                    ImageIO.write(schuss(monat, 1100, 380), "png",
                            new File("build/tide_monat.png"));

                    System.out.println("build/tide_nord_ost.png");
                    System.out.println("build/tide_monat.png");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.exit(0);
    }
}
