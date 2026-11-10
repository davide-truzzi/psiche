package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FoundPlace;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.LetterCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

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

    @Test
    void onlyPlacesAllInsideThePartLookedInAreFound() {

        String text = "mare, il mare, amare il mare";

        assertThat(Occurrences.of(text, "mare", List.of(), new TextRange(2, 20)))
                .containsExactly(new TextRange(9, 13), new TextRange(16, 20));
        assertThat(Occurrences.of(text, "mare", List.of(), new TextRange(2, 19)))
                .containsExactly(new TextRange(9, 13));
    }

    @Test
    void aRangeIsOutsidePartsItNeitherIsInNorGoesAcross() {

        List<TextRange> parts = List.of(new TextRange(5, 10), new TextRange(20, 30));

        assertThat(Occurrences.outside(new TextRange(10, 20), parts)).isTrue();
        assertThat(Occurrences.outside(new TextRange(6, 8), parts)).isFalse();
        assertThat(Occurrences.outside(new TextRange(15, 21), parts)).isFalse();
    }

    @Test
    void capitalsCountOnlyWhenMatched() {

        String text = "Rome, ROME, rome";

        assertThat(Occurrences.of(text, "Rome", LetterCase.MATCHED, List.of())).containsExactly(new TextRange(0, 4));
        assertThat(Occurrences.of(text, "Rome", LetterCase.IGNORED, List.of())).hasSize(3);
    }

    @Test
    void aLineBreakFindsALineEndingHoweverItIsWritten() {

        assertThat(Occurrences.of("The end.\nNew", "end.\nNew", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(4, 12));
        assertThat(Occurrences.of("The end.\r\nNew", "end.\nNew", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(4, 13));
        assertThat(Occurrences.of("The end.\rNew", "end.\nNew", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(4, 12));
    }

    @Test
    void aLineBreakLookedForIsOneHoweverItIsWritten() {

        String text = "one.\n\ntwo, one.\r\n\r\ntwo";

        assertThat(Occurrences.of(text, "one.\r\n\r\ntwo", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(0, 9), new TextRange(11, 22));
        assertThat(Occurrences.of(text, "one.\n\ntwo", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(0, 9), new TextRange(11, 22));
    }

    @Test
    void aLineBreakAloneFindsEveryLineEnding() {

        assertThat(Occurrences.of("a\nb\r\nc\rd", "\n", LetterCase.IGNORED, List.of()))
                .containsExactly(new TextRange(1, 2), new TextRange(3, 5), new TextRange(6, 7));
    }

    @Test
    void placesInALinksTextOrACaptionAreFoundButNotInTheirMarkdownNorAcrossIt() {

        String text = "A storm. See [the storm](storm.md) now.\n\n![storm at sea](storm.png)\n";
        List<TextRange> markers = Stream.concat(TextLinks.of(text).stream().map(TextLink::markers),
                ImageLines.of(text).stream().map(ImageLine::markers)).flatMap(List::stream).toList();

        assertThat(Occurrences.of(text, "storm", LetterCase.IGNORED, markers)).containsExactly(
                new TextRange(2, 7), new TextRange(18, 23), new TextRange(text.indexOf("storm at"),
                        text.indexOf("storm at") + 5));
        assertThat(Occurrences.of(text, "[the", LetterCase.IGNORED, markers)).isEmpty();
        assertThat(Occurrences.of(text, "See the", LetterCase.IGNORED, markers)).isEmpty();
        assertThat(Occurrences.of(text, "storm.md", LetterCase.IGNORED, markers)).isEmpty();
    }

    @Test
    void nextIsThePlaceAtOrAfterThePositionElseTheFirstRoundTheEnd() {

        List<TextRange> places = List.of(new TextRange(5, 9), new TextRange(20, 24), new TextRange(40, 44));

        assertThat(Occurrences.next(places, 0)).contains(new FoundPlace(0, false));
        assertThat(Occurrences.next(places, 20)).contains(new FoundPlace(1, false));
        assertThat(Occurrences.next(places, 24)).contains(new FoundPlace(2, false));
        assertThat(Occurrences.next(places, 41)).contains(new FoundPlace(0, true));
        assertThat(Occurrences.next(List.of(), 0)).isEmpty();
    }

    @Test
    void previousIsThePlaceBeforeThePositionElseTheLastRoundTheStart() {

        List<TextRange> places = List.of(new TextRange(5, 9), new TextRange(20, 24), new TextRange(40, 44));

        assertThat(Occurrences.previous(places, 40)).contains(new FoundPlace(1, false));
        assertThat(Occurrences.previous(places, 99)).contains(new FoundPlace(2, false));
        assertThat(Occurrences.previous(places, 5)).contains(new FoundPlace(2, true));
        assertThat(Occurrences.previous(List.of(), 10)).isEmpty();
    }
}
