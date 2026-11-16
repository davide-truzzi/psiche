package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextRange;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The lines of a text that some of its parts touch, such as its comments or its spelling mistakes, by their index from
 * 0: the lines of the text as written, each a paragraph in the editor however it wraps there, so the line numbers in
 * the editor's margin can show what their lines hold.
 */
public final class MarkedLines {

    private MarkedLines() {
    }

    /**
     * The lines {@code ranges} touch in {@code text}: each range from the line of its first character to the line of
     * its last, a line break being the last character of its line; an empty range touches the line it lies on. Ranges
     * beyond the text are cut to it.
     */
    public static BitSet of(String text, List<TextRange> ranges) {

        int[] lineStarts = lineStarts(text);
        BitSet lines = new BitSet(lineStarts.length);
        for (TextRange range : ranges) {
            int first = Math.clamp(range.start(), 0, text.length());
            int last = Math.clamp(range.end() - 1L, first, text.length());
            lines.set(line(lineStarts, first), line(lineStarts, last) + 1);
        }

        return lines;
    }

    /** Where each line of {@code text} starts, in order: the first at 0, the others after each line break. */
    private static int[] lineStarts(String text) {

        int[] starts = new int[(int) text.chars().filter(c -> c == '\n').count() + 1];
        int line = 1;
        for (int index = 0; index < text.length(); index++)
            if (text.charAt(index) == '\n')
                starts[line++] = index + 1;

        return starts;
    }

    /** The line {@code position} lies on, given where the lines start. */
    private static int line(int[] lineStarts, int position) {

        int found = Arrays.binarySearch(lineStarts, position);
        return found >= 0 ? found : -found - 2;
    }
}
