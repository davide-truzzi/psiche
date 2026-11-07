package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a word or a piece of text comes back in a chapter, so the author can spot a repetition: every place the text
 * appears, capitals or not, inside longer words too, and the word the author is at.
 */
public final class Occurrences {

    private Occurrences() {
    }

    /**
     * Every place {@code wanted} appears in {@code text}, in order and none overlapping another, whatever the capitals:
     * "meraviglios" is in "meraviglioso" and in "Meravigliosa". None if {@code wanted} is empty.
     */
    public static List<TextRange> of(String text, String wanted) {

        return of(text, wanted, List.of());
    }

    /**
     * Every place {@code wanted} appears in {@code text}, as {@link #of(String, String)} finds them, but those in or
     * across one of {@code skipped}, parts of the text that are no prose, as an image's line or a link's address.
     */
    public static List<TextRange> of(String text, String wanted, List<TextRange> skipped) {

        List<TextRange> found = new ArrayList<>();
        if (wanted.isEmpty())
            return found;

        int length = wanted.length();
        int from = 0;
        while (from + length <= text.length()) {
            if (text.regionMatches(true, from, wanted, 0, length)) {
                TextRange place = new TextRange(from, from + length);
                if (skipped.stream().noneMatch(part -> part.overlaps(place)))
                    found.add(place);
                from += length;
            } else
                from++;
        }

        return found;
    }

    /**
     * The word {@code index} is in, or right at the end of, as the caret is after typing it; none if it has fewer
     * than {@code minLetters} letters. A word is letters only, so "l'anima" gives "anima", which "un'anima" has too.
     */
    public static Optional<TextRange> wordAt(String text, int index, int minLetters) {

        int start = Math.clamp(index, 0, text.length());
        int end = start;
        while (start > 0 && isLetter(text.charAt(start - 1)))
            start--;
        while (end < text.length() && isLetter(text.charAt(end)))
            end++;

        return end - start >= minLetters ? Optional.of(new TextRange(start, end)) : Optional.empty();
    }

    /** True for a letter, or a mark that goes on one, as the accent written after its letter does. */
    private static boolean isLetter(char character) {

        return switch (Character.getType(character)) {
            case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true;
            default -> Character.isLetter(character);
        };
    }
}
