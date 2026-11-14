package it.infog.psiche.export.history;

import it.infog.psiche.export.history.model.Passage;
import it.infog.psiche.git.TrackedChanges;
import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.Paragraph;
import it.infog.psiche.git.model.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * The passages of a chapter that a revision changed, each with one unchanged paragraph before and after it for
 * context. Changes closer than that share one passage, so no paragraph is shown twice. Blank lines are not paragraphs,
 * and a change of spaces alone is not a change.
 */
final class Passages {

    // Unchanged paragraphs shown before and after a change.
    private static final int CONTEXT = 1;

    private Passages() {
    }

    /** @return the passages in reading order; empty if only spaces or blank lines changed */
    static List<Passage> of(Comparison comparison) {

        List<Paragraph> paragraphs = comparison.paragraphs();
        List<Passage> passages = new ArrayList<>();
        int next = 0;
        while (next < paragraphs.size()) {
            int first = nextChange(paragraphs, next);
            if (first < 0)
                break;

            //take in the changes whose context would touch this one's
            int last = first;
            for (int change = nextChange(paragraphs, last + 1);
                 change >= 0 && textBetween(paragraphs, last + 1, change) <= 2 * CONTEXT;
                 change = nextChange(paragraphs, last + 1))
                last = change;

            int from = contextBefore(paragraphs, first);
            int to = contextAfter(paragraphs, last);
            passages.add(new Passage(from, to, words(paragraphs, from, to)));
            next = to;
        }

        return passages;
    }

    /** True if text was written or removed in {@code paragraph}, not only spaces. */
    static boolean changed(Paragraph paragraph) {

        return paragraph.segments().stream()
                .anyMatch(segment -> segment.change() != Segment.Change.UNCHANGED && !segment.text().isBlank());
    }

    /** True if {@code paragraph} has any text, changed or not, rather than being a blank line. */
    static boolean hasText(Paragraph paragraph) {

        return paragraph.segments().stream().anyMatch(segment -> !segment.text().isBlank());
    }

    /** The paragraphs with text in {@code [from, to)}. */
    static int textBetween(List<Paragraph> paragraphs, int from, int to) {

        return (int) paragraphs.subList(from, to).stream().filter(Passages::hasText).count();
    }

    /** The first changed paragraph from {@code from} on; -1 if there is none. */
    private static int nextChange(List<Paragraph> paragraphs, int from) {

        for (int i = from; i < paragraphs.size(); i++)
            if (changed(paragraphs.get(i)))
                return i;
        return -1;
    }

    /** Where the passage starts: {@link #CONTEXT} paragraphs with text before {@code first}, as far as there are. */
    private static int contextBefore(List<Paragraph> paragraphs, int first) {

        int from = first;
        int found = 0;
        for (int i = first - 1; i >= 0 && found < CONTEXT; i--) {
            if (hasText(paragraphs.get(i))) {
                from = i;
                found++;
            }
        }
        return from;
    }

    /** Where the passage ends, exclusive: {@link #CONTEXT} paragraphs with text after {@code last}, as there are. */
    private static int contextAfter(List<Paragraph> paragraphs, int last) {

        int to = last + 1;
        int found = 0;
        for (int i = last + 1; i < paragraphs.size() && found < CONTEXT; i++) {
            if (hasText(paragraphs.get(i))) {
                to = i + 1;
                found++;
            }
        }
        return to;
    }

    /** The words written and removed in {@code [from, to)}. */
    private static int words(List<Paragraph> paragraphs, int from, int to) {

        return paragraphs.subList(from, to).stream()
                .flatMap(paragraph -> paragraph.segments().stream())
                .filter(segment -> segment.change() != Segment.Change.UNCHANGED)
                .mapToInt(segment -> TrackedChanges.countWords(segment.text()))
                .sum();
    }
}
