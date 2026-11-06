package it.infog.psiche.export.document.model.enums;

/**
 * The named styles a manuscript's text refers to, all defined in each format's stylesheet. A word processor lists them
 * by their display names (Text Body, First Paragraph…), and changing one restyles every paragraph or span that uses it.
 */
public enum Style {

    // Paragraphs
    /** An indented paragraph, double-spaced, as manuscripts set the body text. */
    TEXT_BODY("Text_20_body"),
    /** A paragraph without the indent: the chapter's first, and the one after a heading, scene break, quote or list. */
    FIRST_PARAGRAPH("First_20_Paragraph"),
    QUOTATIONS("Quotations"),
    /** A paragraph in a list item; the list style draws its bullet or number. */
    LIST("List"),
    /** The centred {@code * * *} between scenes. */
    SCENE_BREAK("Scene_20_Break"),
    /** A paragraph holding only an image, centred, with room above it. */
    FIGURE("Figure"),
    /** An image's caption, centred under it, smaller and in italics. */
    CAPTION("Caption"),
    /** Code, in a monospaced font with its spaces kept. */
    PREFORMATTED("Preformatted_20_Text"),
    /** The chapter's title, a third of the way down its first page. */
    HEADING_1("Heading_20_1"),
    HEADING_2("Heading_20_2"),
    HEADING_3("Heading_20_3"),
    /** Headings below level 4 use this one too. */
    HEADING_4("Heading_20_4"),

    // The book's title page
    /** The cover image, centred above the title. */
    COVER("Cover"),
    TITLE("Title"),
    SUBTITLE("Subtitle"),
    BOOK_AUTHORS("Book_20_Authors"),

    // The book's contents, named as LibreOffice names its own table of contents' styles
    CONTENTS_HEADING("Contents_20_Heading"),
    /** A line of the contents: the chapter's title, and in the ODT dot leaders and the page it starts on. */
    CONTENTS_1("Contents_20_1"),

    // Spans
    EMPHASIS("Emphasis"),
    STRONG_EMPHASIS("Strong_20_Emphasis"),
    /** Inline code. */
    SOURCE_TEXT("Source_20_Text"),
    /** A contents line's link to its chapter, in the text's own colour, as in the PDF. */
    INDEX_LINK("Index_20_Link"),

    // Lists
    BULLETS("Bullets"),
    NUMBERING("Numbering");

    private final String odtName;

    Style(String odtName) {

        this.odtName = odtName;
    }

    /** The heading style for a Markdown heading of {@code level} 1 to 6; levels below 4 share the level 4 style. */
    public static Style heading(int level) {

        return switch (level) {
            case 1 -> HEADING_1;
            case 2 -> HEADING_2;
            case 3 -> HEADING_3;
            default -> HEADING_4;
        };
    }

    /** The name an ODT's XML refers to the style by; spaces in the display name are written as {@code _20_}. */
    public String odtName() {

        return odtName;
    }
}
