package it.infog.psiche.markdown.model;

/**
 * Replaces the text from {@code start} to {@code end} with {@code replacement}, after which the selection runs from
 * {@code selectionStart} to {@code selectionEnd} in the changed text.
 */
public record TextEdit(int start,
                       int end,
                       String replacement,
                       int selectionStart,
                       int selectionEnd) {

    /** Replaces {@code start} to {@code end} and selects the replacement. */
    public static TextEdit selecting(int start, int end, String replacement) {
        return new TextEdit(start, end, replacement, start, start + replacement.length());
    }
}
