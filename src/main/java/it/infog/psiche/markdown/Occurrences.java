package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FoundPlace;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.LetterCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Where a word or a piece of text comes back in a chapter, so the author can spot a repetition or find it: every place
 * the text appears, capitals or not, inside longer words too; the word the author is at; and the place to go to next
 * or back among those found.
 * <p>
 * A line break in the text looked for is a character like the others, and finds a line ending however it is written:
 * {@code \r\n} in files from Windows, {@code \n} from Linux and macOS, a lone {@code \r} from older systems; so a
 * place may be longer than the text looked for.
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

        return of(text, wanted, skipped, new TextRange(0, text.length()));
    }

    /**
     * Every place {@code wanted} appears in {@code text}, as {@link #of(String, String, List)} finds them, but only
     * those all inside {@code within}, a part of the text.
     */
    public static List<TextRange> of(String text, String wanted, List<TextRange> skipped, TextRange within) {

        return of(text, wanted, LetterCase.IGNORED, skipped, within);
    }

    /**
     * Every place {@code wanted} appears in {@code text}, as {@link #of(String, String, List)} finds them, telling
     * capitals from small letters or not, as {@code letterCase} says.
     */
    public static List<TextRange> of(String text, String wanted, LetterCase letterCase, List<TextRange> skipped) {

        return of(text, wanted, letterCase, skipped, new TextRange(0, text.length()));
    }

    /**
     * The place to go to forward from {@code position} among {@code places}, in the order of the text: the first that
     * starts there or after it, or else the first of all, round the text's end. None if there are no places.
     */
    public static Optional<FoundPlace> next(List<TextRange> places, int position) {

        if (places.isEmpty())
            return Optional.empty();

        return Optional.of(IntStream.range(0, places.size())
                .filter(index -> places.get(index).start() >= position)
                .mapToObj(index -> new FoundPlace(index, false))
                .findFirst()
                .orElse(new FoundPlace(0, true)));
    }

    /**
     * The place to go back to from {@code position} among {@code places}, in the order of the text: the last that
     * starts before it, or else the last of all, round the text's start. None if there are no places.
     */
    public static Optional<FoundPlace> previous(List<TextRange> places, int position) {

        if (places.isEmpty())
            return Optional.empty();

        return Optional.of(IntStream.iterate(places.size() - 1, index -> index >= 0, index -> index - 1)
                .filter(index -> places.get(index).start() < position)
                .mapToObj(index -> new FoundPlace(index, false))
                .findFirst()
                .orElse(new FoundPlace(places.size() - 1, true)));
    }

    /** True if {@code range} neither is in nor goes across one of {@code parts}. */
    public static boolean outside(TextRange range, List<TextRange> parts) {

        return parts.stream().noneMatch(part -> part.overlaps(range));
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

    private static List<TextRange> of(String text, String wanted, LetterCase letterCase, List<TextRange> skipped,
                                      TextRange within) {

        List<TextRange> found = new ArrayList<>();
        if (wanted.isEmpty())
            return found;

        // The lines of what is looked for, each compared at once, and between them a line ending however written.
        String[] lines = wanted.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean ignoreCase = letterCase == LetterCase.IGNORED;
        int from = within.start();
        int limit = within.end();
        while (from < limit) {
            int end = matchEnd(text, from, lines, ignoreCase, limit);
            if (end >= 0) {
                TextRange place = new TextRange(from, end);
                if (outside(place, skipped))
                    found.add(place);
                from = end;
            } else
                from++;
        }

        return found;
    }

    /**
     * Where the text looked for, as its {@code lines}, ends if it appears in {@code text} from {@code from}, all before
     * {@code limit}; -1 if it does not.
     */
    private static int matchEnd(String text, int from, String[] lines, boolean ignoreCase, int limit) {

        int at = from;
        for (int index = 0; index < lines.length; index++) {
            if (index > 0)
                at = lineEndAfter(text, at, limit);
            String line = lines[index];
            if (at < 0 || at + line.length() > limit || !text.regionMatches(ignoreCase, at, line, 0, line.length()))
                return -1;

            at += line.length();
        }

        return at;
    }

    /** Where the line ending at {@code at} ends, however it is written, if it is all before {@code limit}; else -1. */
    private static int lineEndAfter(String text, int at, int limit) {

        if (at >= limit)
            return -1;

        return switch (text.charAt(at)) {
            case '\n' -> at + 1;
            case '\r' -> at + 1 < limit && text.charAt(at + 1) == '\n' ? at + 2 : at + 1;
            default -> -1;
        };
    }

    /** True for a letter, or a mark that goes on one, as the accent written after its letter does. */
    private static boolean isLetter(char character) {

        return switch (Character.getType(character)) {
            case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true;
            default -> Character.isLetter(character);
        };
    }
}
