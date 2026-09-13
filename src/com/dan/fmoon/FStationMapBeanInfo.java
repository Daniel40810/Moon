package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FStationMap}.
 *
 * @author Claude fuer Daniel
 */
public class FStationMapBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FStationMap.class);
        bd.setDisplayName("FStationMap");
        bd.setShortDescription("Pegelstationen auf der Karte, mit der Laufstrecke der Tidewelle");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.map(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("selectedStation", "Ausgewählte Station"),
                p("showWaves", "Laufstrecken der Tidewelle"),
                p("northSeaColor", "Farbe der Nordseestationen"),
                p("balticColor", "Farbe der Ostseestationen"),
                p("coastColor", "Farbe der Landflächen"),
                p("waveColor", "Farbe der Wellenstrecken")
            };
        } catch (IntrospectionException e) {
            return new PropertyDescriptor[0];
        }
    }

    private PropertyDescriptor p(String name, String text)
            throws IntrospectionException {
        PropertyDescriptor d = new PropertyDescriptor(name, FStationMap.class);
        d.setShortDescription(text);
        return d;
    }
}
