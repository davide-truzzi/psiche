package it.infog.psiche.notes;

import it.infog.psiche.common.EditDistance;
import it.infog.psiche.notes.model.NotePin;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Chapter notes' pins found again in their chapter's text, which may have changed while the chapter was closed, such as
 * by a restore from History or a Revert. A pin's paragraph is, in this order: the one of its number, if it still starts
 * with the pin's opening text; the nearest one that starts with it; the one most like it, nearest first, if its
 * opening is the same but for capitals and spacing, or at least {@value #ALIKE_PERCENT}% alike; otherwise none, and
 * the note is unpinned. One paragraph holds one pin: a pin whose paragraph another holds goes to the nearest free one.
 * <p>
 * A paragraph is an editor line, as the editor and the Media tab count them; a blank one never holds a pin.
 */
public final class NotePins {

    private static final int ALIKE_PERCENT = 80;
    private static final Pattern SPACING = Pattern.compile("\\s+");

    private NotePins() {
    }

    /**
     * The pins of {@code pins}, by note id, as found again in {@code text}, each with its paragraph's number and
     * opening text now; a note whose paragraph cannot be found is left out.
     */
    public static Map<String, NotePin> find(String text, Map<String, NotePin> pins) {

        List<String> lines = lines(text);

        // Pins still at their paragraph keep it; the others go after, in the order of the text.
        Comparator<Map.Entry<String, NotePin>> order = Comparator
                .comparing((Map.Entry<String, NotePin> entry) -> !isAtItsNumber(lines, entry.getValue()))
                .thenComparingInt(entry -> entry.getValue().paragraph())
                .thenComparing(Map.Entry::getKey);

        Map<String, NotePin> found = new LinkedHashMap<>();
        Set<Integer> taken = new HashSet<>();
        pins.entrySet().stream().sorted(order).forEach(entry -> {
            OptionalInt paragraph = paragraph(lines, entry.getValue());
            if (paragraph.isEmpty())
                return;

            OptionalInt free = nearestFree(lines, taken, paragraph.getAsInt());
            if (free.isEmpty())
                return;

            taken.add(free.getAsInt());
            found.put(entry.getKey(), NotePin.of(free.getAsInt(), lines.get(free.getAsInt())));
        });

        return found;
    }

    /**
     * The paragraph nearest to {@code paragraph} that is neither blank nor in {@code taken}: that one, then the one
     * after, the one before, and further out each way; none if every paragraph is blank or taken.
     */
    public static OptionalInt nearestFree(List<String> lines, Set<Integer> taken, int paragraph) {

        for (int distance = 0; distance < lines.size() + Math.abs(paragraph); distance++)
            for (int each : new int[] {paragraph + distance, paragraph - distance})
                if (each >= 0 && each < lines.size() && !taken.contains(each) && !lines.get(each).isBlank())
                    return OptionalInt.of(each);

        return OptionalInt.empty();
    }

    /** The editor's lines of {@code text}, the last one too, even if empty. */
    public static List<String> lines(String text) {

        return List.of(text.split("\n", -1));
    }

    /** The paragraph {@code pin} belongs to in {@code lines}, taken or not; none if no paragraph is near enough. */
    private static OptionalInt paragraph(List<String> lines, NotePin pin) {

        if (isAtItsNumber(lines, pin))
            return OptionalInt.of(pin.paragraph());

        //the nearest paragraph that starts with the pin's text
        OptionalInt same = nearest(lines, pin.paragraph(), line -> line.startsWith(pin.text()) ? 0 : -1);
        if (same.isPresent())
            return same;

        //the paragraph most like it
        String wanted = normalized(pin.text());
        return nearest(lines, pin.paragraph(), line -> distance(normalized(opening(line)), wanted));
    }

    private static boolean isAtItsNumber(List<String> lines, NotePin pin) {

        return pin.paragraph() < lines.size() && !lines.get(pin.paragraph()).isBlank()
                && lines.get(pin.paragraph()).startsWith(pin.text());
    }

    /**
     * The paragraph whose text is the least far from the one looked for, by {@code distance}, which gives -1 for a
     * paragraph that is not it, and then nearest to {@code paragraph}; blank ones are left out.
     */
    private static OptionalInt nearest(List<String> lines, int paragraph, ToIntFunction<String> distance) {

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank())
                continue;

            int far = distance.applyAsInt(lines.get(index));
            boolean nearer = far >= 0 && (far < bestDistance || far == bestDistance
                    && Math.abs(index - paragraph) < Math.abs(best - paragraph));
            if (nearer) {
                best = index;
                bestDistance = far;
            }
        }

        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** The edits between two openings, or -1 if they are less than {@value #ALIKE_PERCENT}% alike. */
    private static int distance(String opening, String wanted) {

        int max = Math.max(opening.length(), wanted.length()) * (100 - ALIKE_PERCENT) / 100;
        int distance = EditDistance.between(opening, wanted, max);
        return distance > max ? -1 : distance;
    }

    private static String opening(String line) {

        return line.substring(0, Math.min(line.length(), NotePin.TEXT_LENGTH));
    }

    /** {@code text} in small letters, with each run of spaces as one, and none at its ends. */
    private static String normalized(String text) {

        return SPACING.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
