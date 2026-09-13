package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FOrbitView}.
 *
 * @author Claude fuer Daniel
 */
public class FOrbitViewBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FOrbitView.class);
        bd.setDisplayName("FOrbitView");
        bd.setShortDescription("Die Bahn von oben und derselbe Augenblick von der Erde aus");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.orbit(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("timeMillis", "Dargestellter Zeitpunkt"),
                p("observerLocation", "Beobachtungsort für die rechte Seite"),
                p("showSunRays", "Sonnenstrahlen"),
                p("showElongation", "Winkel Sonne–Erde–Mond"),
                p("showLabels", "Beschriftungen"),
                p("showScaleNote", "Hinweis, was am Bild nicht maßstäblich ist"),
                p("earthColor", "Farbe der Erde"),
                p("moonColor", "Farbe des Mondes"),
                p("orbitColor", "Farbe der Bahnlinie"),
                p("sunColor", "Farbe der Sonnenstrahlen"),
                p("textColor", "Farbe der Beschriftung")
            };
        } catch (IntrospectionException e) {
            return new PropertyDescriptor[0];
        }
    }

    private PropertyDescriptor p(String name, String text)
            throws IntrospectionException {
        PropertyDescriptor d = new PropertyDescriptor(name, FOrbitView.class);
        d.setShortDescription(text);
        return d;
    }
}
