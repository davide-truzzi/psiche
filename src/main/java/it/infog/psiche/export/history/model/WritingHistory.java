package it.infog.psiche.export.history.model;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.git.model.BookRevision;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * What the writing history PDF is made from.
 *
 * @param properties the book's properties now, for its title and authors
 * @param revisions  every revision that changed the chapters, oldest first; never empty
 * @param appVersion Psiche Writer's version, as the author reads it, such as "0.8.0 Beta"; empty if unknown
 * @param exportedAt when the PDF was made
 * @param zone       the time zone the dates are shown in, the author's computer's
 */
public record WritingHistory(BookProperties properties,
                             List<BookRevision> revisions,
                             String appVersion,
                             Instant exportedAt,
                             ZoneId zone) {

    public WritingHistory {
        if (revisions.isEmpty())
            throw new IllegalArgumentException("A writing history needs at least one revision");

        revisions = List.copyOf(revisions);
    }
}
