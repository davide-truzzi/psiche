package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeSet;

/**
 * The style classes of the manuscript's text, which writer.css draws: its comments in grey, the ones the preview
 * leaves out, its spelling mistakes with a dotted line under them, its images in green, its links in teal, and its
 * images whose file is missing and its links that lead nowhere in orange. A part can have several, as a mistake
 * inside a comment, for the moment it takes a check to see it is one, has both.
 */
final class TextStyles {

    static final String COMMENT = "comment";
    static final String MISTAKE = "misspelled";
    static final String IMAGE = "image";
    static final String MISSING_IMAGE = "missing-image";
    static final String LINK = "link";
    static final String BROKEN_LINK = "broken-link";

    private TextStyles() {
    }

    /**
     * The styles of a text of {@code length} characters with {@code parts}: each style with the parts that have it,
     * in order and none overlapping another of its style; a part with several styles has them in the map's order.
     * Parts past the end are cut at it.
     */
    static StyleSpans<Collection<String>> of(int length, SequencedMap<String, List<TextRange>> parts) {

        //where a style starts or ends
        TreeSet<Integer> cuts = new TreeSet<>(List.of(0, length));
        for (List<TextRange> styled : parts.values())
            for (TextRange part : styled) {
                cuts.add(Math.clamp(part.start(), 0, length));
                cuts.add(Math.clamp(part.end(), 0, length));
            }

        //each stretch between two cuts, with the styles of the parts it lies in
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        Map<String, Cover> covers = new LinkedHashMap<>();
        parts.forEach((style, styled) -> covers.put(style, new Cover(styled)));
        int from = 0;
        for (int cut : cuts.tailSet(0, false)) {
            List<String> styles = new ArrayList<>(covers.size());
            for (Map.Entry<String, Cover> cover : covers.entrySet())
                if (cover.getValue().covers(from))
                    styles.add(cover.getKey());
            spans.add(List.copyOf(styles), cut - from);
            from = cut;
        }
        if (length == 0)
            spans.add(List.of(), 0);

        return spans.create();
    }

    /**
     * True if {@code a} and {@code b} give the same styles to the same lengths of text, span by span. A part of
     * spans, as {@link StyleSpans#subView} gives, is never equal to other spans, even with the same styles.
     */
    static boolean same(StyleSpans<Collection<String>> a, StyleSpans<Collection<String>> b) {

        if (a.getSpanCount() != b.getSpanCount())
            return false;

        for (int span = 0; span < a.getSpanCount(); span++)
            if (!a.getStyleSpan(span).equals(b.getStyleSpan(span)))
                return false;
        return true;
    }

    /** Tells which positions, asked in order, lie in one of its parts, walking through them once. */
    private static final class Cover {

        private final List<TextRange> parts;
        private int next;

        Cover(List<TextRange> parts) {

            this.parts = parts;
        }

        boolean covers(int position) {

            while (next < parts.size() && parts.get(next).end() <= position)
                next++;

            return next < parts.size() && parts.get(next).start() <= position;
        }
    }
}
