package it.infog.psiche.export.history;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.history.model.HistorySection;
import it.infog.psiche.export.history.model.WritingHistory;
import it.infog.psiche.export.pdf.PdfLayout;

import java.nio.file.Path;
import java.util.List;

/**
 * Writes the book's writing history as a PDF: every revision of its chapters, from the first, with what each one
 * changed as tracked changes, for a jury or a publisher who asks to see how the book was written. The look is in
 * {@code history.css}.
 */
public final class HistoryPdf {

    private final String stylesheet = PdfLayout.readResource(HistoryPdf.class, "history.css");

    public void write(WritingHistory history, Path target) {

        BookProperties properties = history.properties();
        List<HistorySection> sections = RevisionGroups.of(history.revisions(), history.zone());
        String header = PdfLayout.cssString(properties.title() + " · Writing history");

        PdfLayout.write(properties.title() + " – Writing history", properties,
                stylesheet.replace("{{header}}", header), HistoryRenderer.bookmarks(sections),
                HistoryRenderer.body(history, sections), target.toAbsolutePath().getParent(), target);
    }
}
