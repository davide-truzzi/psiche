package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextCount;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class TextCounterTest {

    @ParameterizedTest
    @MethodSource("texts")
    void countsTheCharactersAndWordsAReaderSees(String markdown, TextCount expected) {

        assertThat(TextCounter.count(markdown)).isEqualTo(expected);
    }

    private static Stream<Arguments> texts() {

        return Stream.of(
                argumentSet("bold without its markers", "**Ciao** mondo", new TextCount(10, 2)),
                argumentSet("a heading without its marker", "# Il temporale", new TextCount(12, 2)),
                argumentSet("a quoted list item without markers", "> - *Run*, she said.", new TextCount(14, 3)),
                argumentSet("spaces and accented letters count one each", "È così, però.", new TextCount(13, 3)),
                argumentSet("a line break inside a paragraph is a space, a paragraph break nothing",
                        "one\ntwo\n\nthree", new TextCount(12, 3)),
                argumentSet("dashes and ellipses between spaces are no words", "Lei — forse … sì",
                        new TextCount(16, 3)),
                argumentSet("an apostrophe keeps a word whole", "l'uomo dell'anno", new TextCount(16, 2)),
                argumentSet("a link's text counts, not its address", "See [the map](https://example.com/map).",
                        new TextCount(12, 3)),
                argumentSet("an image is left out", "![A map of the coast](../media/map.png)",
                        new TextCount(0, 0)),
                argumentSet("a comment is left out", "Before<!-- a note --> after", new TextCount(12, 2)),
                argumentSet("scene breaks and escaped markers", "One.\n\n---\n\nTwo \\*not\\* bold.",
                        new TextCount(19, 4)),
                argumentSet("an empty chapter has nothing to count", "", new TextCount(0, 0)));
    }
}
