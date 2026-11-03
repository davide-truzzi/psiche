package it.infog.psiche.export.docx;

import it.infog.psiche.export.document.model.enums.Style;

/**
 * The ids a DOCX's XML refers to its styles by, all defined in {@code styles.xml}. Where Word has a style of its own
 * for the same purpose (the headings, Title, Subtitle, the contents' heading and lines, Emphasis, Strong), the
 * manuscript uses it, so Word's navigator and its own table of contents recognise them.
 */
final class StyleIds {

    /** The paragraph style of the page header. */
    static final String HEADER = "Header";
    /** The character style of a web or mail link. */
    static final String HYPERLINK = "Hyperlink";

    private StyleIds() {
    }

    /** {@code style}'s id; a list's style is its numbering in {@code numbering.xml}, not a style, so it has none. */
    static String of(Style style) {

        return switch (style) {
            case TEXT_BODY -> "BodyText";
            case FIRST_PARAGRAPH -> "FirstParagraph";
            case QUOTATIONS -> "Quotations";
            case LIST -> "List";
            case SCENE_BREAK -> "SceneBreak";
            case FIGURE -> "Figure";
            case PREFORMATTED -> "PreformattedText";
            case HEADING_1 -> "Heading1";
            case HEADING_2 -> "Heading2";
            case HEADING_3 -> "Heading3";
            case HEADING_4 -> "Heading4";
            case COVER -> "Cover";
            case TITLE -> "Title";
            case SUBTITLE -> "Subtitle";
            case BOOK_AUTHORS -> "BookAuthors";
            case CONTENTS_HEADING -> "TOCHeading";
            case CONTENTS_1 -> "TOC1";
            case EMPHASIS -> "Emphasis";
            case STRONG_EMPHASIS -> "Strong";
            case SOURCE_TEXT -> "SourceText";
            case INDEX_LINK -> "IndexLink";
            case BULLETS, NUMBERING -> throw new IllegalArgumentException(style + " is a numbering, not a style");
        };
    }
}
