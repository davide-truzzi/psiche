package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The style classes of the manuscript's text, which writer.css draws: its comments in grey, the ones the preview
 * leaves out, its spelling mistakes with a dotted line under them, and its images whose file is missing in orange; a
 * mistake inside a comment, for the moment it takes a check to see it is one, has both.
 */
final class TextStyles {

    static final String COMMENT = "comment";
    static final String MISTAKE = "misspelled";
    static final String MISSING_IMAGE = "missing-image";

    private TextStyles() {
    }

    /**
     * The styles of a text of {@code length} characters with {@code comments}, {@code mistakes} and
     * {@code missingImages}, each in order and none overlapping another of its kind; parts past the end are cut at it.
     */
    static StyleSpans<Collection<String>> of(int length, List<TextRange> comments, List<TextRange> mistakes,
                                             List<TextRange> missingImages) {

        //where a style starts or ends
        TreeSet<Integer> cuts = new TreeSet<>(List.of(0, length));
        for (List<TextRange> parts : List.of(comments, mistakes, missingImages))
            for (TextRange part : parts) {
                cuts.add(Math.clamp(part.start(), 0, length));
                cuts.add(Math.clamp(part.end(), 0, length));
            }

        //each stretch between two cuts, with the styles of the parts it lies in
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        Cover inComment = new Cover(comments);
        Cover inMistake = new Cover(mistakes);
        Cover inMissingImage = new Cover(missingImages);
        int from = 0;
        for (int cut : cuts.tailSet(0, false)) {
            List<String> styles = new ArrayList<>(3);
            if (inComment.covers(from))
                styles.add(COMMENT);
            if (inMistake.covers(from))
                styles.add(MISTAKE);
            if (inMissingImage.covers(from))
                styles.add(MISSING_IMAGE);
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
