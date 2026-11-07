package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import static it.infog.psiche.ui.chapters.TextStyles.BROKEN_LINK;
import static it.infog.psiche.ui.chapters.TextStyles.COMMENT;
import static it.infog.psiche.ui.chapters.TextStyles.LINK;
import static it.infog.psiche.ui.chapters.TextStyles.MISSING_IMAGE;
import static it.infog.psiche.ui.chapters.TextStyles.MISTAKE;
import static org.assertj.core.api.Assertions.assertThat;

class TextStylesTest {

    @Test
    void aTextWithNothingToMarkIsPlain() {

        assertThat(spans(styles(12, List.of(), List.of(), List.of()))).containsExactly("[] 12");
    }

    @Test
    void commentsAreGreyAndMistakesUnderlined() {

        // "It began <!-- a note --> at dwan."
        StyleSpans<Collection<String>> styles = styles(33,
                List.of(new TextRange(9, 23)), List.of(new TextRange(27, 31)), List.of());

        assertThat(spans(styles))
                .containsExactly("[] 9", "[" + COMMENT + "] 14", "[] 4", "[" + MISTAKE + "] 4", "[] 2");
    }

    @Test
    void aMistakeInsideACommentHasBothStyles() {

        assertThat(spans(styles(20, List.of(new TextRange(2, 18)), List.of(new TextRange(6, 10)), List.of())))
                .containsExactly("[] 2", "[" + COMMENT + "] 4", "[" + COMMENT + ", " + MISTAKE + "] 4",
                        "[" + COMMENT + "] 8", "[] 2");
    }

    @Test
    void mistakesSideBySideMakeOneStretch() {

        assertThat(spans(styles(10, List.of(), List.of(new TextRange(0, 4), new TextRange(4, 10)), List.of())))
                .containsExactly("[" + MISTAKE + "] 10");
    }

    @Test
    void aPartOfTheSpansIsTheSameAsSpansWithItsStyles() {

        StyleSpans<Collection<String>> styles = styles(20, List.of(new TextRange(2, 18)),
                List.of(new TextRange(6, 10)), List.of());

        assertThat(TextStyles.same(styles.subView(6, 10),
                styles(4, List.of(new TextRange(0, 4)), List.of(new TextRange(0, 4)), List.of()))).isTrue();
        assertThat(TextStyles.same(styles.subView(6, 10),
                styles(4, List.of(new TextRange(0, 4)), List.of(), List.of()))).isFalse();
    }

    @Test
    void anImageWhoseFileIsMissingIsAmberInsideACommentToo() {

        assertThat(spans(styles(30, List.of(new TextRange(0, 10)), List.of(),
                List.of(new TextRange(5, 25)))))
                .containsExactly("[" + COMMENT + "] 5", "[" + COMMENT + ", " + MISSING_IMAGE + "] 5",
                        "[" + MISSING_IMAGE + "] 15", "[] 5");
    }

    @Test
    void aPartPastTheEndIsCutAtIt() {

        assertThat(spans(styles(8, List.of(new TextRange(5, 20)), List.of(), List.of())))
                .containsExactly("[] 5", "[" + COMMENT + "] 3");
    }

    @Test
    void anEmptyTextHasOneEmptySpan() {

        assertThat(spans(styles(0, List.of(), List.of(), List.of()))).containsExactly("[] 0");
    }

    @Test
    void aLinkIsTealAndABrokenOneOrangeWithAMistakeInsideUnderlinedToo() {

        SequencedMap<String, List<TextRange>> parts = new LinkedHashMap<>();
        parts.put(MISTAKE, List.of(new TextRange(2, 5)));
        parts.put(LINK, List.of(new TextRange(0, 10)));
        parts.put(BROKEN_LINK, List.of(new TextRange(12, 20)));

        assertThat(spans(TextStyles.of(20, parts))).containsExactly("[" + LINK + "] 2",
                "[" + MISTAKE + ", " + LINK + "] 3", "[" + LINK + "] 5", "[] 2", "[" + BROKEN_LINK + "] 8");
    }

    /** The styles of comments, mistakes and missing images, in this order, as the editor gives them. */
    private static StyleSpans<Collection<String>> styles(int length, List<TextRange> comments,
                                                         List<TextRange> mistakes, List<TextRange> missingImages) {

        SequencedMap<String, List<TextRange>> parts = new LinkedHashMap<>();
        parts.put(COMMENT, comments);
        parts.put(MISTAKE, mistakes);
        parts.put(MISSING_IMAGE, missingImages);
        return TextStyles.of(length, parts);
    }

    /** Each span as its styles and its length, as "[comment] 14". */
    private static List<String> spans(StyleSpans<Collection<String>> styles) {

        List<String> spans = new ArrayList<>();
        for (StyleSpan<Collection<String>> span : styles)
            spans.add(span.getStyle() + " " + span.getLength());
        return spans;
    }
}
