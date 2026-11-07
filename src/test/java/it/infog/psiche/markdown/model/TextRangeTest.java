package it.infog.psiche.markdown.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class TextRangeTest {

    // The range every other is compared with.
    private static final TextRange RANGE = new TextRange(4, 10);

    @ParameterizedTest
    @MethodSource("ranges")
    void rangesOverlapWhenTheyShareACharacter(TextRange other, boolean overlaps) {

        assertThat(RANGE.overlaps(other)).isEqualTo(overlaps);
    }

    private static Stream<Arguments> ranges() {

        return Stream.of(
                argumentSet("one running past its end", new TextRange(8, 14), true),
                argumentSet("one starting before it", new TextRange(0, 5), true),
                argumentSet("one inside it", new TextRange(5, 7), true),
                argumentSet("one touching its end only", new TextRange(10, 12), false),
                argumentSet("one touching its start only", new TextRange(0, 4), false),
                argumentSet("an empty one inside it", new TextRange(6, 6), true),
                argumentSet("an empty one at its start", new TextRange(4, 4), true),
                argumentSet("an empty one past it", new TextRange(11, 11), false));
    }
}
