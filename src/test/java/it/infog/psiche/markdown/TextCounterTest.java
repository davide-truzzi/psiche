package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextCount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCounterTest {

    @Test
    void countsWhatAReaderSeesWithoutMarkdownMarkers() {
        assertThat(TextCounter.count("**Ciao** mondo")).isEqualTo(new TextCount(10, 2));
        assertThat(TextCounter.count("# Il temporale")).isEqualTo(new TextCount(12, 2));
        assertThat(TextCounter.count("> - *Run*, she said.")).isEqualTo(new TextCount(14, 3));
    }

    @Test
    void countsSpacesAndAccentedLettersAsOneCharacterEach() {
        assertThat(TextCounter.count("È così, però.")).isEqualTo(new TextCount(13, 3));
    }

    @Test
    void aLineBreakInsideAParagraphCountsAsASpaceAndParagraphBreaksDoNotCount() {
        assertThat(TextCounter.count("one\ntwo\n\nthree")).isEqualTo(new TextCount(12, 3));
    }

    @Test
    void dashesAndEllipsesBetweenSpacesAreNotWords() {
        assertThat(TextCounter.count("Lei — forse … sì")).isEqualTo(new TextCount(16, 3));
    }

    @Test
    void anApostropheKeepsAWordWhole() {
        assertThat(TextCounter.count("l'uomo dell'anno")).isEqualTo(new TextCount(16, 2));
    }

    @Test
    void countsALinksTextButNotItsAddressAndLeavesOutImagesAndHtml() {
        assertThat(TextCounter.count("See [the map](https://example.com/map).")).isEqualTo(new TextCount(12, 3));
        assertThat(TextCounter.count("![A map of the coast](../media/map.png)")).isEqualTo(new TextCount(0, 0));
        assertThat(TextCounter.count("Before<!-- a note --> after")).isEqualTo(new TextCount(12, 2));
    }

    @Test
    void sceneBreaksAndEscapedMarkers() {
        assertThat(TextCounter.count("One.\n\n---\n\nTwo \\*not\\* bold.")).isEqualTo(new TextCount(19, 4));
    }

    @Test
    void anEmptyChapterHasNothingToCount() {
        assertThat(TextCounter.count("")).isEqualTo(new TextCount(0, 0));
    }
}
