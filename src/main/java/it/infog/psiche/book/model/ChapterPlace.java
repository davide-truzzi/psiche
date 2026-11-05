package it.infog.psiche.book.model;

/**
 * Where the author left a chapter: the caret, as a character offset into its text, and the paragraph at the top of
 * the view, by its index.
 */
public record ChapterPlace(int caret,
                           int topParagraph) {

    /** The start of the chapter, where a chapter not opened before shows. */
    public static final ChapterPlace START = new ChapterPlace(0, 0);
}
