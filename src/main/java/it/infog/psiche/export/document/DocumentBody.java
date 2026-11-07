package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;

/**
 * The text of a word processor's document as it is written, in a format of its own, ODT or DOCX: what
 * {@link ChapterRenderer}, {@link TitlePageRenderer} and {@link ContentsRenderer} write a manuscript with. Everything
 * is named by its {@link Style}, so the look stays in the format's stylesheet; each format only decides how to write
 * it. A paragraph, heading, span, link, list or list item is opened, filled and closed, as in the XML of both formats.
 */
public interface DocumentBody {

    /**
     * Starts a new page with the next paragraph or heading.
     *
     * @param bookmark the name to mark the page's start with, such as a chapter's id, which links lead to; null for
     *                 none
     */
    void startPage(PageStart start, String bookmark);

    void openParagraph(Style style);

    void closeParagraph();

    /**
     * Opens a heading of {@code style} at {@code outlineLevel}, from 1 to 4, which a navigator lists it at, marked with
     * a bookmark named {@code bookmark}, which links lead to.
     */
    void openHeading(Style style, int outlineLevel, String bookmark);

    void closeHeading();

    /** Opens a span of {@code style}; spans nest, so bold inside italic is both. */
    void openSpan(Style style);

    void closeSpan();

    /** Opens a link to the web or mail address {@code address}. */
    void openLink(String address);

    /** Opens a link to the bookmark named {@code bookmark}, a place of the document; {@link #closeLink} closes it. */
    void openInternalLink(String bookmark);

    void closeLink();

    /**
     * Opens a list of {@link Style#BULLETS} or {@link Style#NUMBERING}; a nested list is opened inside an item.
     *
     * @param start the number of the first item, for a numbered list; each numbered list counts from its own
     */
    void openList(Style style, int start);

    void openListItem();

    void closeListItem();

    void closeList();

    /**
     * Writes {@code text} as it is, every space kept.
     *
     * @param lineStart true if {@code text} starts a line of code, whose leading spaces must show too
     */
    void text(String text, boolean lineStart);

    /** A line break inside a paragraph. */
    void lineBreak();

    /**
     * Draws {@code picture} in the line like a large character.
     *
     * @param title the text a screen reader reads for it, such as an image's alt text
     */
    void image(Picture picture, String title);

    /**
     * A line of the book's contents: the chapter's {@code title}, linked to the {@code bookmark} at its start, and
     * whatever else the format can show of it, such as its page.
     */
    void contentsLine(String title, String bookmark);
}
