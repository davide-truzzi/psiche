package it.infog.psiche.export.history;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates and times as the writing history writes them, in English, in the time zone of the author's computer. */
final class HistoryDates {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final ZoneId zone;

    HistoryDates(ZoneId zone) {

        this.zone = zone;
    }

    /** As "3 March 2026". */
    String day(Instant time) {

        return DAY.format(time.atZone(zone));
    }

    /** As "3 March 2026, 09:12". */
    String dayAndTime(Instant time) {

        return day(time) + ", " + TIME.format(time.atZone(zone));
    }

    /** As "3 March 2026, 09:12–17:40"; with both days if they differ, and one time if both are the same minute. */
    String span(Instant start, Instant end) {

        if (!LocalDate.ofInstant(start, zone).equals(LocalDate.ofInstant(end, zone)))
            return dayAndTime(start) + " – " + dayAndTime(end);

        String endTime = TIME.format(end.atZone(zone));
        return endTime.equals(TIME.format(start.atZone(zone))) ? dayAndTime(start) : dayAndTime(start) + "–" + endTime;
    }

    /** The time zone's name, as "Europe/Rome". */
    String zone() {

        return zone.getId();
    }
}
