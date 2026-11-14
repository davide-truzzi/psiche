package it.infog.psiche.export.history;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.history.model.CountedRevision;
import it.infog.psiche.export.history.model.HistorySection;
import it.infog.psiche.export.history.model.WritingHistory;
import it.infog.psiche.git.model.BookRevision;

import java.util.List;
import java.util.stream.Collectors;

import static it.infog.psiche.export.XmlText.escape;

/**
 * Writes the writing history as one XHTML body: a cover that says what the document is and how to read it, a summary
 * with a row per revision, then the sections, a revision or a run of small ones each.
 */
final class HistoryRenderer {

    private static final String SUMMARY_ID = "summary";

    private HistoryRenderer() {
    }

    static String body(WritingHistory history, List<HistorySection> sections) {

        HistoryDates dates = new HistoryDates(history.zone());
        SectionRenderer renderer = new SectionRenderer(dates);

        StringBuilder html = new StringBuilder(cover(history, dates));
        html.append(summary(sections, dates));
        sections.forEach(section -> html.append(renderer.render(section)));

        return html.toString();
    }

    /** The PDF's bookmarks, as OpenHTMLtoPDF reads them from the head: the summary, then a bookmark per section. */
    static String bookmarks(List<HistorySection> sections) {

        StringBuilder bookmarks = new StringBuilder("<bookmarks>\n");
        bookmarks.append("<bookmark name=\"Summary\" href=\"#%s\"/>\n".formatted(SUMMARY_ID));
        for (HistorySection section : sections)
            bookmarks.append("<bookmark name=\"%s\" href=\"#%s\"/>\n".formatted(escape(SectionRenderer.name(section)),
                    SectionRenderer.id(section)));

        return bookmarks.append("</bookmarks>\n").toString();
    }

    private static String cover(WritingHistory history, HistoryDates dates) {

        BookProperties properties = history.properties();
        List<BookRevision> revisions = history.revisions();
        StringBuilder html = new StringBuilder("<section class=\"cover\">\n");

        //the book
        html.append("<p class=\"kind\">Writing history</p>\n");
        html.append("<p class=\"book-title\">%s</p>\n".formatted(escape(properties.title())));
        if (!properties.subtitle().isBlank())
            html.append("<p class=\"book-subtitle\">%s</p>\n".formatted(escape(properties.subtitle())));
        if (!properties.authors().isEmpty())
            html.append("<p class=\"book-authors\">%s</p>\n".formatted(escape(String.join(", ",
                    properties.authors()))));

        //the history
        String made = history.appVersion().isBlank() ? "Psiche Writer" : "Psiche Writer " + history.appVersion();
        html.append("<table class=\"facts\">\n");
        html.append(fact("Revisions", "%d, from %s to %s".formatted(revisions.size(),
                dates.day(revisions.getFirst().time()), dates.day(revisions.getLast().time()))));
        html.append(fact("Last revision", "<code>%s</code>".formatted(escape(revisions.getLast().commitId()))));
        html.append(fact("Exported", "%s, with %s".formatted(dates.dayAndTime(history.exportedAt()), escape(made))));
        html.append("</table>\n");

        //what the document is, and how to read it
        html.append("""
                <div class="statement">
                <p>This document lists every revision of the book's chapters recorded in its Git history, from the \
                first to the last. Each revision is named by its commit id, which can be checked against the book's \
                history, such as a Psiche project backup of the book.</p>
                <p>Dates and times are those of the author's computer, in the time zone %s. Revisions that changed \
                only other files, such as images or the book's properties, are not listed. Small revisions made one \
                after another on the same day are shown together, as one stretch of work; the summary lists each of \
                them on its own.</p>
                <p class="legend-title">How to read the changes</p>
                <p>Text written in a revision is <ins>underlined</ins>; text removed is <del>struck through</del>. \
                The chapters are shown as the author typed them, in Markdown, where # starts a heading and \
                asterisks mark emphasis. Each change is shown with one unchanged paragraph before and after it, and \
                […] marks text left out. A chapter copied from another one still in the book shows as new, all its \
                text written at once.</p>
                </div>
                """.formatted(escape(dates.zone())));

        return html.append("</section>\n").toString();
    }

    private static String fact(String name, String xhtml) {

        return "<tr><th>%s</th><td>%s</td></tr>\n".formatted(escape(name), xhtml);
    }

    /** A row per revision, linked to its section: when, its message's first line, its chapters, its words. */
    private static String summary(List<HistorySection> sections, HistoryDates dates) {

        StringBuilder html = new StringBuilder("<section class=\"summary\">\n<h2 id=\"%s\">Summary</h2>\n"
                .formatted(SUMMARY_ID));
        html.append("""
                <table class="revisions">
                <thead><tr><th class="number">No.</th><th class="date">Date</th><th>Revision</th><th>Chapters</th>\
                <th class="words">Written</th><th class="words">Removed</th></tr></thead>
                <tbody>
                """);

        for (HistorySection section : sections) {
            for (CountedRevision counted : section.revisions()) {
                BookRevision revision = counted.revision();
                String chapters = revision.changes().stream()
                        .map(change -> escape(SectionRenderer.chapterTitle(change.path())))
                        .collect(Collectors.joining(", "));
                html.append("<tr><td class=\"number\"><a href=\"#%s\">%d</a></td><td class=\"date\">%s</td>".formatted(
                        SectionRenderer.id(section), counted.number(), dates.dayAndTime(revision.time())));
                html.append("<td>%s <span class=\"short-id\">%s</span></td><td>%s</td>".formatted(
                        escape(revision.message().lines().findFirst().orElse("")), escape(revision.shortId()),
                        chapters.isEmpty() ? "—" : chapters));
                html.append("<td class=\"words\">%d</td><td class=\"words\">%d</td></tr>\n".formatted(
                        counted.wordsAdded(), counted.wordsRemoved()));
            }
        }

        return html.append("</tbody>\n</table>\n</section>\n").toString();
    }
}
