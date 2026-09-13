package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FMoonGlobe}.
 *
 * @author Claude fuer Daniel
 */
public class FMoonGlobeBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FMoonGlobe.class);
        bd.setDisplayName("FMoonGlobe");
        bd.setShortDescription("Der Mond als beleuchtete Kugel, zu Zeitpunkt und Ort");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.crescent(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("timeMillis", "Dargestellter Zeitpunkt in Millisekunden"),
                p("observerLocation", "Beobachtungsort (nicht 'location' - der Name gehört Component)"),
                p("viewMode", "Kartenlage oder Himmelslage"),
                p("animated", "Zeitraffer läuft"),
                p("timeLapseFactor", "Geschwindigkeit des Zeitraffers"),
                p("live", "Der Uhr folgen"),
                p("showLabels", "Beschriftung unter der Scheibe"),
                p("showStars", "Sternenhintergrund"),
                p("showEarthshine", "Aschgraues Licht auf der Nachtseite"),
                p("earthshineStrength", "Stärke des Erdscheins"),
                p("scaleWithDistance", "Scheibengröße folgt der Entfernung"),
                p("showLibrationGrid", "Marke auf dem gegenüberliegenden Punkt"),
                p("diskFill", "Anteil der Fläche, den die Scheibe einnimmt"),
                p("sunlitColor", "Farbe der beleuchteten Seite"),
                p("earthshineColor", "Farbe des Erdscheins"),
                p("themeAware", "Folgt dem FTheme")
            };
        } catch (IntrospectionException e) {
            return super.getPropertyDescriptors();
        }
    }

    private PropertyDescriptor p(String name, String beschreibung)
            throws IntrospectionException {
        PropertyDescriptor pd = new PropertyDescriptor(name, FMoonGlobe.class);
        pd.setShortDescription(beschreibung);
        return pd;
    }
}
