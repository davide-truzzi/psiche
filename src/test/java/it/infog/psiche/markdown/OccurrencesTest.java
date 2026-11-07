package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OccurrencesTest {

    @Test
    void findsTheTextInsideLongerWordsWhateverTheCapitals() {

        String text = "Era meraviglioso. Meravigliosa, disse.";

        assertThat(Occurrences.of(text, "meraviglios"))
                .containsExactly(new TextRange(4, 15), new TextRange(18, 29));
    }

    @Test
    void findsOnlyWhatWasSelectedWithItsSpaces() {

        assertThat(Occurrences.of("la sera, la serata, lasera", "la sera"))
                .containsExactly(new TextRange(0, 7), new TextRange(9, 16));
    }

    @Test
    void occurrencesNeverOverlap() {

        assertThat(Occurrences.of("aaaaa", "aa")).containsExactly(new TextRange(0, 2), new TextRange(2, 4));
    }

    @Test
    void anEmptyTextIsNowhere() {

        assertThat(Occurrences.of("some text", "")).isEmpty();
    }

    @Test
    void theWordAtTheCaretIsTheOneItIsInOrJustAfter() {

        String text = "the river ran";

        assertThat(Occurrences.wordAt(text, 6, 4)).contains(new TextRange(4, 9));
        assertThat(Occurrences.wordAt(text, 9, 4)).contains(new TextRange(4, 9));
        assertThat(Occurrences.wordAt(text, 4, 4)).contains(new TextRange(4, 9));
    }

    @Test
    void aWordShorterThanTheMinimumIsNone() {

        assertThat(Occurrences.wordAt("the river ran", 1, 4)).isEmpty();
        assertThat(Occurrences.wordAt("the river ran", 13, 4)).isEmpty();
    }

    @Test
    void aWordIsLettersOnlyWithItsAccents() {

        String text = "dell'anima, perché";

        assertThat(Occurrences.wordAt(text, 7, 4)).contains(new TextRange(5, 10));
        assertThat(Occurrences.wordAt(text, text.length(), 4)).contains(new TextRange(12, 18));
        assertThat(Occurrences.wordAt("città1", 2, 4)).contains(new TextRange(0, 5));
    }

    @Test
    void aCaretOutsideTheTextIsKeptInIt() {

        assertThat(Occurrences.wordAt("river", 99, 4)).contains(new TextRange(0, 5));
        assertThat(Occurrences.wordAt("", 0, 4)).isEmpty();
    }

    @Test
    void placesInOrAcrossASkippedPartAreLeftOut() {

        String text = "The lantern. [Lantern room](Lantern.md) ![lantern](lantern.png)";
        TextRange address = new TextRange(text.indexOf("]("), text.indexOf(")") + 1);
        TextRange image = new TextRange(text.indexOf("!["), text.length());

        assertThat(Occurrences.of(text, "lantern", List.of(address, image)))
                .containsExactly(new TextRange(4, 11), new TextRange(14, 21));
    }
}
