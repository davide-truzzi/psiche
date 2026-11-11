package it.infog.psiche.notes;

import it.infog.psiche.common.EditDistance;
import it.infog.psiche.notes.model.NotePin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;

/**
 * Chapter notes' pins found again in their chapter's text, which may have changed while the chapter was closed, such as
 * by a restore from History or a Revert. A pin's paragraph is, in this order: the one of its number, if it still starts
 * with the pin's opening text; the nearest one that starts with it; the one most like it, nearest first, if its
 * opening is the same but for capitals and spacing, or at least {@value #ALIKE_PERCENT}% alike; otherwise none, and
 * the note is unpinned. One paragraph holds one pin: a pin whose paragraph another holds goes to the nearest free one.
 * <p>
 * While the chapter is open, the pins follow their paragraphs as the text changes: see {@link #shifted} and
 * {@link #settled}.
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

    /**
     * The free paragraph nearest to {@code pin}'s number that starts with its opening text, neither blank nor in
     * {@code taken}, as a pin moved off its paragraph goes back there once Undo brings it back; none if there is none.
     */
    public static OptionalInt startingWith(List<String> lines, Set<Integer> taken, NotePin pin) {

        return nearest(lines, pin.paragraph(),
                index -> !taken.contains(index) && lines.get(index).startsWith(pin.text()) ? 0 : -1);
    }

    /**
     * Where a paragraph starting at {@code start} starts after a change at {@code at} that took {@code removed} away
     * and put {@code inserted} there: where it was, if before the change; where the change is, if the change took its
     * start away; moved by the change otherwise, as by line breaks typed at its start, which push it down.
     */
    public static int shifted(int start, int at, String removed, String inserted) {

        int removedEnd = at + removed.length();
        boolean pushedDown = removed.isEmpty() && inserted.endsWith("\n");
        if (start < at || start == at && !pushedDown)
            return start;

        return start < removedEnd ? at : start + inserted.length() - removed.length();
    }

    /**
     * True if a change at {@code at} that took {@code removed} away took away the start of a paragraph starting at
     * {@code start}, and so the paragraph, its pin left on another; a paragraph joined to the one before it is not.
     */
    public static boolean uprooted(int start, int at, String removed) {

        return start > at && start < at + removed.length();
    }

    /**
     * The pins on {@code paragraphs}, the notes' paragraphs in {@code lines} after a change, in the order of the text
     * before it, one to a paragraph. A pin keeps its paragraph if it is free and not blank, the first one in the text
     * first, as when two pinned paragraphs are joined, unless the change {@code uprooted} its own. A pin moved off its
     * own paragraph, kept in {@code homes} as it was, goes back there once a free paragraph starts with its opening
     * again, as after an Undo. Any other goes to the nearest free paragraph, which {@code moved} is told, and is kept
     * in {@code homes} as it was in {@code before}; or is left out if there is none.
     *
     * @param uprooted the ids of the notes whose paragraphs the change took away; see {@link #uprooted}
     * @param homes    the pins moved off their paragraphs, as they were, by note id; changed as pins leave and come
     *                 back
     */
    public static Map<String, NotePin> settled(List<String> lines, Map<String, Integer> paragraphs,
                                               Set<String> uprooted, Map<String, NotePin> before,
                                               Map<String, NotePin> homes, BiConsumer<String, Integer> moved) {

        Set<Integer> taken = new HashSet<>();
        List<String> displaced = new ArrayList<>();
        Map<String, Integer> placed = new LinkedHashMap<>();

        //pins on their own paragraphs, first in the order of the text
        paragraphs.forEach((id, paragraph) -> {
            if (homes.containsKey(id))
                return;
            if (!uprooted.contains(id) && !lines.get(paragraph).isBlank() && taken.add(paragraph))
                placed.put(id, paragraph);
            else
                displaced.add(id);
        });

        //pins moved off theirs, back there if it is back, or kept where they are
        paragraphs.forEach((id, paragraph) -> {
            if (!homes.containsKey(id))
                return;
            OptionalInt home = startingWith(lines, taken, homes.get(id));
            if (home.isPresent()) {
                homes.remove(id);
                taken.add(home.getAsInt());
                placed.put(id, home.getAsInt());
            } else if (!uprooted.contains(id) && !lines.get(paragraph).isBlank() && taken.add(paragraph))
                placed.put(id, paragraph);
            else
                displaced.add(id);
        });

        //the others, to the nearest free paragraph
        for (String id : displaced) {
            OptionalInt free = nearestFree(lines, taken, paragraphs.get(id));
            if (free.isEmpty())
                continue;

            homes.putIfAbsent(id, before.get(id));
            taken.add(free.getAsInt());
            placed.put(id, free.getAsInt());
            moved.accept(id, free.getAsInt());
        }

        Map<String, NotePin> settled = new LinkedHashMap<>();
        placed.forEach((id, paragraph) -> settled.put(id, NotePin.of(paragraph, lines.get(paragraph))));
        return settled;
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
        OptionalInt same = nearest(lines, pin.paragraph(), index -> lines.get(index).startsWith(pin.text()) ? 0 : -1);
        if (same.isPresent())
            return same;

        //the paragraph most like it
        String wanted = normalized(pin.text());
        return nearest(lines, pin.paragraph(), index -> distance(normalized(opening(lines.get(index))), wanted));
    }

    private static boolean isAtItsNumber(List<String> lines, NotePin pin) {

        return pin.paragraph() < lines.size() && !lines.get(pin.paragraph()).isBlank()
                && lines.get(pin.paragraph()).startsWith(pin.text());
    }

    /**
     * The paragraph whose text is the least far from the one looked for, by {@code distance} of its number, which gives
     * -1 for a paragraph that is not it, and then nearest to {@code paragraph}; blank ones are left out.
     */
    private static OptionalInt nearest(List<String> lines, int paragraph, IntUnaryOperator distance) {

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank())
                continue;

            int far = distance.applyAsInt(index);
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
