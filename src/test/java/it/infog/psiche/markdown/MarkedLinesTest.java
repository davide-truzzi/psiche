package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextRange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkedLinesTest {

    // Lines 0 to 4: "## Alba" from 0, "" at 8, "Era una notte." from 9, "" at 24, "Fine." from 25 to 30.
    private static final String TEXT = "## Alba\n\nEra una notte.\n\nFine.";

    @Test
    void aRangeOnOneLineMarksThatLineOnly() {

        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(13, 18))).stream()).containsExactly(2);
    }

    @Test
    void aRangeOverSeveralLinesMarksEveryLineItCovers() {

        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(3, 12))).stream()).containsExactly(0, 1, 2);
    }

    @Test
    void aRangeEndingWithALineBreakDoesNotMarkTheNextLine() {

        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(9, 24))).stream()).containsExactly(2);
    }

    @Test
    void anEmptyRangeMarksTheLineItLiesOn() {

        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(8, 8))).stream()).containsExactly(1);
        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(30, 30))).stream()).containsExactly(4);
    }

    @Test
    void severalRangesMarkTheirLinesEachOnce() {

        List<TextRange> ranges = List.of(new TextRange(0, 2), new TextRange(3, 7), new TextRange(25, 30));

        assertThat(MarkedLines.of(TEXT, ranges).stream()).containsExactly(0, 4);
    }

    @Test
    void theLastLineAfterAFinalLineBreakCanBeMarked() {

        assertThat(MarkedLines.of("Uno\n", List.of(new TextRange(4, 4))).stream()).containsExactly(1);
        assertThat(MarkedLines.of("Uno\n", List.of(new TextRange(0, 4))).stream()).containsExactly(0);
    }

    @Test
    void rangesBeyondTheTextAreCutToIt() {

        assertThat(MarkedLines.of(TEXT, List.of(new TextRange(27, 99))).stream()).containsExactly(4);
        assertThat(MarkedLines.of("", List.of(new TextRange(0, 5))).stream()).containsExactly(0);
    }

    @Test
    void noRangesMarkNoLines() {

        assertThat(MarkedLines.of(TEXT, List.of()).stream()).isEmpty();
    }
}
