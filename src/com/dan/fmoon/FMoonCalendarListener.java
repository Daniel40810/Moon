package com.dan.fmoon;

/**
 * Wird benachrichtigt, wenn im Kalender ein Tag gewaehlt wird.
 *
 * @author Claude fuer Daniel
 */
public interface FMoonCalendarListener {

    /**
     * Ein Tag wurde ausgewaehlt.
     *
     * @param source der Kalender
     * @param day    der gewaehlte Tag
     */
    void daySelected(FMoonCalendar source, FMoonDay day);
}
