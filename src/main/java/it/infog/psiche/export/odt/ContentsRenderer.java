package it.infog.psiche.export.odt;

import it.infog.psiche.export.model.Contents;
import it.infog.psiche.export.odt.model.enums.PageStart;
import it.infog.psiche.export.odt.model.enums.Style;

/**
 * Draws the book's {@link Contents} on a page of its own after the title page, without the manuscript's header: the
 * heading, then a line per chapter in the styles LibreOffice gives its own table of contents.
 * <p>
 * Each line links to the bookmark at its chapter's start, and ends, past dot leaders, with a field holding the page
 * that bookmark is on. A word processor works the page out when it lays the book out, and again whenever the text
 * changes; until then the field shows {@value #UNKNOWN_PAGE}.
 */
final class ContentsRenderer {

    // What a page field shows until a word processor works the page out.
    private static final String UNKNOWN_PAGE = "?";

    private ContentsRenderer() {
    }

    /** Writes the contents into {@code body}, after the title page. */
    static void write(OdtBody body, Contents contents) {

        body.startPage(PageStart.CONTENTS, "toc");
        body.openParagraph(Style.CONTENTS_HEADING);
        body.text(contents.heading(), false);
        body.append("</text:p>\n");

        for (Contents.Entry entry : contents.entries()) {
            String bookmark = OdtText.escape(entry.id());

            body.openParagraph(Style.CONTENTS_1);
            body.append("<text:a xlink:type=\"simple\" xlink:href=\"#%s\" text:style-name=\"%s\" "
                    .formatted(bookmark, Style.INDEX_LINK.xmlName())
                    + "text:visited-style-name=\"%s\">".formatted(Style.INDEX_LINK.xmlName()));
            body.text(entry.title(), false);
            body.append("</text:a><text:tab/>");
            body.append("<text:bookmark-ref text:reference-format=\"page\" text:ref-name=\"%s\">%s</text:bookmark-ref>"
                    .formatted(bookmark, UNKNOWN_PAGE));
            body.append("</text:p>\n");
        }
    }
}
