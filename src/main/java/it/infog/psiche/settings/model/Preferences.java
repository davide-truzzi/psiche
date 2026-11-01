package it.infog.psiche.settings.model;

import it.infog.psiche.settings.model.enums.PreviewTheme;

/**
 * What the author chose in Preferences.
 *
 * @param reopenLastBook  true to open the book opened last when the app starts
 * @param newBookLanguage the language a new book is written in, as a BCP 47 tag such as {@code en-US}
 * @param newBookAuthor   the author a new book starts with; blank for none
 * @param previewTheme    how the preview shows the page
 */
public record Preferences(boolean reopenLastBook,
                          String newBookLanguage,
                          String newBookAuthor,
                          PreviewTheme previewTheme) {

    /** The preferences of an author who has not changed any. */
    public static Preferences defaults() {

        return new Preferences(false, "en-US", "", PreviewTheme.DEFAULT);
    }
}
