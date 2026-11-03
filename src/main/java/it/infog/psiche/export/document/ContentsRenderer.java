package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.export.model.Contents;

/**
 * Draws the book's {@link Contents} on a page of its own after the title page, without the manuscript's header: the
 * heading, then a line per chapter, linked to the bookmark at its chapter's start, in the styles LibreOffice gives its
 * own table of contents. What else a line shows, such as the chapter's page, is the format's to decide.
 */
final class ContentsRenderer {

    // The bookmark at the contents' start.
    private static final String BOOKMARK = "toc";

    private ContentsRenderer() {
    }

    /** Writes the contents into {@code body}, after the title page. */
    static void write(DocumentBody body, Contents contents) {

        body.startPage(PageStart.CONTENTS, BOOKMARK);
        body.openParagraph(Style.CONTENTS_HEADING);
        body.text(contents.heading(), false);
        body.closeParagraph();

        for (Contents.Entry entry : contents.entries())
            body.contentsLine(entry.title(), entry.id());
    }
}
