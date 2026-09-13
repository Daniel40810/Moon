package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FTideBoard}.
 *
 * @author Claude fuer Daniel
 */
public class FTideBoardBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FTideBoard.class);
        bd.setDisplayName("FTideBoard");
        bd.setShortDescription("Hoch- und Niedrigwasser eines Tages, mit der Rechenkette");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.tide(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("station", "Pegelort"),
                p("timeMillis", "Angezeigter Zeitpunkt"),
                p("showChain", "Rechenkette Mond - Hafenzeit - Hochwasser"),
                p("showNow", "Augenblicklicher Wasserstand"),
                p("highColor", "Farbe für Hochwasser"),
                p("lowColor", "Farbe für Niedrigwasser"),
                p("themeAware", "Folgt dem FTheme")
            };
        } catch (IntrospectionException e) {
            return super.getPropertyDescriptors();
        }
    }

    private PropertyDescriptor p(String name, String beschreibung)
            throws IntrospectionException {
        PropertyDescriptor pd = new PropertyDescriptor(name, FTideBoard.class);
        pd.setShortDescription(beschreibung);
        return pd;
    }
}
