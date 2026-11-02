package it.infog.psiche.settings.model;

import it.infog.psiche.settings.model.enums.PreviewTheme;

import java.util.List;

/**
 * What the author chose in Preferences.
 *
 * @param reopenLastBook  true to open the book opened last when the app starts
 * @param newBookLanguage the language a new book is written in, as a BCP 47 tag such as {@code en-US}
 * @param newBookAuthor   the author a new book starts with; blank for none
 * @param previewTheme    how the preview shows the page
 * @param editorFontSize  the size of the editor's text, in pixels, one of {@link #FONT_SIZES}
 * @param previewFontSize the size of the preview's text, in pixels, one of {@link #FONT_SIZES}; headings and the
 *                        rest of the page grow and shrink with it
 */
public record Preferences(boolean reopenLastBook,
                          String newBookLanguage,
                          String newBookAuthor,
                          PreviewTheme previewTheme,
                          int editorFontSize,
                          int previewFontSize) {

    /** The sizes the editor's and the preview's text can take, in pixels. */
    public static final List<Integer> FONT_SIZES = List.of(12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 28, 32);
    // The size writer.css gives the manuscript and preview.css the page.
    private static final int DEFAULT_FONT_SIZE = 16;

    /** The preferences of an author who has not changed any. */
    public static Preferences defaults() {

        return new Preferences(false, "en-US", "", PreviewTheme.DEFAULT, DEFAULT_FONT_SIZE, DEFAULT_FONT_SIZE);
    }
}
