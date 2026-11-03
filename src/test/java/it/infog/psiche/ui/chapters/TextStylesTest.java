package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static it.infog.psiche.ui.chapters.TextStyles.COMMENT;
import static it.infog.psiche.ui.chapters.TextStyles.MISTAKE;
import static org.assertj.core.api.Assertions.assertThat;

class TextStylesTest {

    @Test
    void aTextWithNothingToMarkIsPlain() {

        assertThat(spans(TextStyles.of(12, List.of(), List.of()))).containsExactly("[] 12");
    }

    @Test
    void commentsAreGreyAndMistakesUnderlined() {

        // "It began <!-- a note --> at dwan."
        StyleSpans<Collection<String>> styles = TextStyles.of(33,
                List.of(new TextRange(9, 23)), List.of(new TextRange(27, 31)));

        assertThat(spans(styles))
                .containsExactly("[] 9", "[" + COMMENT + "] 14", "[] 4", "[" + MISTAKE + "] 4", "[] 2");
    }

    @Test
    void aMistakeInsideACommentHasBothStyles() {

        assertThat(spans(TextStyles.of(20, List.of(new TextRange(2, 18)), List.of(new TextRange(6, 10)))))
                .containsExactly("[] 2", "[" + COMMENT + "] 4", "[" + COMMENT + ", " + MISTAKE + "] 4",
                        "[" + COMMENT + "] 8", "[] 2");
    }

    @Test
    void mistakesSideBySideMakeOneStretch() {

        assertThat(spans(TextStyles.of(10, List.of(), List.of(new TextRange(0, 4), new TextRange(4, 10)))))
                .containsExactly("[" + MISTAKE + "] 10");
    }

    @Test
    void aPartPastTheEndIsCutAtIt() {

        assertThat(spans(TextStyles.of(8, List.of(new TextRange(5, 20)), List.of())))
                .containsExactly("[] 5", "[" + COMMENT + "] 3");
    }

    @Test
    void anEmptyTextHasOneEmptySpan() {

        assertThat(spans(TextStyles.of(0, List.of(), List.of()))).containsExactly("[] 0");
    }

    /** Each span as its styles and its length, as "[comment] 14". */
    private static List<String> spans(StyleSpans<Collection<String>> styles) {

        List<String> spans = new ArrayList<>();
        for (StyleSpan<Collection<String>> span : styles)
            spans.add(span.getStyle() + " " + span.getLength());
        return spans;
    }
}
