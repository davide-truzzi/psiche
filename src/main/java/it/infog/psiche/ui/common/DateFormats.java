package it.infog.psiche.ui.common;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * How the interface shows dates and times, the same whatever the system's language: the day, the month and the year
 * as numbers, DD/MM/YYYY, the order most of the world reads, and the time on a 24-hour clock, in the system's time
 * zone. Dates in file and folder names are not shown to be read, and keep an order that sorts.
 */
public final class DateFormats {

    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    /** A day in a file's name, as 2026-10-27, so files named after days sort in their order. */
    public static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private DateFormats() {
    }
}
