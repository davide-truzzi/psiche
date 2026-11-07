package it.infog.psiche.markdown.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextRangeTest {

    @Test
    void rangesOverlapWhenTheyShareACharacter() {

        TextRange range = new TextRange(4, 10);

        assertThat(range.overlaps(new TextRange(8, 14))).isTrue();
        assertThat(range.overlaps(new TextRange(0, 5))).isTrue();
        assertThat(range.overlaps(new TextRange(5, 7))).isTrue();
    }

    @Test
    void rangesThatOnlyTouchDoNotOverlap() {

        TextRange range = new TextRange(4, 10);

        assertThat(range.overlaps(new TextRange(10, 12))).isFalse();
        assertThat(range.overlaps(new TextRange(0, 4))).isFalse();
    }

    @Test
    void anEmptyRangeOverlapsTheRangeItLiesIn() {

        assertThat(new TextRange(4, 10).overlaps(new TextRange(6, 6))).isTrue();
        assertThat(new TextRange(4, 10).overlaps(new TextRange(4, 4))).isTrue();
        assertThat(new TextRange(4, 10).overlaps(new TextRange(11, 11))).isFalse();
    }
}
