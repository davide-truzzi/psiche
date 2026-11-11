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

    /** Replaces {@code start} to {@code end}, with the caret, and no selection, at {@code caret}. */
    public static TextEdit caretAt(int start, int end, String replacement, int caret) {

        return new TextEdit(start, end, replacement, caret, caret);
    }

    /**
     * The edit that turns {@code text} into {@code changed}, replacing only the stretch between the first and the last
     * character that differ, and selecting what it puts there.
     */
    public static TextEdit between(String text, String changed) {

        int limit = Math.min(text.length(), changed.length());
        int start = 0;
        while (start < limit && text.charAt(start) == changed.charAt(start))
            start++;
        int common = 0;
        while (common < limit - start
                && text.charAt(text.length() - 1 - common) == changed.charAt(changed.length() - 1 - common))
            common++;

        return selecting(start, text.length() - common, changed.substring(start, changed.length() - common));
    }

    /** True if the edit changes nothing. */
    public boolean empty() {

        return start == end && replacement.isEmpty();
    }
}
