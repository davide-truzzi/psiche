package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.export.model.TitlePage;

import java.util.Optional;

/**
 * Draws the book's {@link TitlePage} on the document's first page, without the manuscript's header: the cover, then
 * a centred paragraph per line, each in a named style of its own, so an editor can restyle it.
 */
final class TitlePageRenderer {

    private TitlePageRenderer() {
    }

    /**
     * Writes the title page at the start of {@code body}.
     *
     * @param cover the title page's cover, packed into the document
     */
    static void write(DocumentBody body, TitlePage page, Optional<Picture> cover) {

        body.startPage(PageStart.TITLE_PAGE, null);

        cover.ifPresent(picture -> {
            body.openParagraph(Style.COVER);
            body.image(picture, page.title());
            body.closeParagraph();
        });

        for (TitlePage.Line line : page.lines()) {
            Style style = switch (line.role()) {
                case TITLE -> Style.TITLE;
                case SUBTITLE -> Style.SUBTITLE;
                case AUTHORS -> Style.BOOK_AUTHORS;
            };

            body.openParagraph(style);
            body.text(line.text(), false);
            body.closeParagraph();
        }
    }
}
