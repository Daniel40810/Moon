package com.dan.fmoon;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * Palettenbeschreibung fuer {@link FMoonCalendar}.
 *
 * @author Claude fuer Daniel
 */
public class FMoonCalendarBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor bd = new BeanDescriptor(FMoonCalendar.class);
        bd.setDisplayName("FMoonCalendar");
        bd.setShortDescription("Monatskalender, in dem jede Zelle ihren eigenen Mond trägt");
        return bd;
    }

    @Override
    public Image getIcon(int kind) {
        int size = (kind == BeanInfo.ICON_COLOR_32x32
                || kind == BeanInfo.ICON_MONO_32x32) ? 32 : 16;
        return FMoonPaletteIcon.calendar(size);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[]{
                p("year", "Angezeigtes Jahr"),
                p("month", "Angezeigter Monat, 1 bis 12"),
                p("observerLocation", "Beobachtungsort für Zeitzone und Auf-/Untergang"),
                p("selectedDay", "Ausgewählter Tag, oder -1"),
                p("showRiseSet", "Auf- und Untergangszeiten in den Zellen"),
                p("showIllumination", "Beleuchteter Anteil in Prozent"),
                p("showEvents", "Phasen, Erdnähe und Erdferne beschriften"),
                p("showEarthshine", "Aschgraues Licht in den Zellen"),
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
        PropertyDescriptor pd = new PropertyDescriptor(name, FMoonCalendar.class);
        pd.setShortDescription(beschreibung);
        return pd;
    }
}
