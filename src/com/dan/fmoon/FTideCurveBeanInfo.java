package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FTideCurve}.
 *
 * @author Claude fuer Daniel
 */
public class FTideCurveBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FTideCurve.class);
        bd.setDisplayName("FTideCurve");
        bd.setShortDescription("Wasserstandskurve mit einem Band aus gerechneten Monden");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.curve(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("station", "Pegelort"),
                p("timeMillis", "Bezugszeitpunkt"),
                p("spanHours", "Dargestellte Zeitspanne in Stunden"),
                p("offsetHours", "Verschiebung des Fensteranfangs"),
                p("showMoonBand", "Band mit gerechneten Monden über der Kurve"),
                p("showEvents", "Marken für Hoch- und Niedrigwasser"),
                p("showNowLine", "Senkrechte am Bezugszeitpunkt"),
                p("showGrid", "Gitter"),
                p("curveColor", "Farbe der Kurve"),
                p("themeAware", "Folgt dem FTheme")
            };
        } catch (IntrospectionException e) {
            return super.getPropertyDescriptors();
        }
    }

    private PropertyDescriptor p(String name, String beschreibung)
            throws IntrospectionException {
        PropertyDescriptor pd = new PropertyDescriptor(name, FTideCurve.class);
        pd.setShortDescription(beschreibung);
        return pd;
    }
}
