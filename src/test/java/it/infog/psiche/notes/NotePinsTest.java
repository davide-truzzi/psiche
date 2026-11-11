package it.infog.psiche.notes;

import it.infog.psiche.notes.model.NotePin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotePinsTest {

    private static final String CHAPTER = """
            # Dawn

            Marco opened the letter by the window.

            Lucia waited by the gate.

            The salt pans shone.""";

    @Test
    void aPinStillAtItsParagraphStaysThere() {
        NotePin pin = new NotePin(2, "Marco opened the letter by the window.");

        assertThat(NotePins.find(CHAPTER, Map.of("a", pin))).containsExactly(Map.entry("a", pin));
    }

    @Test
    void aPinFollowsItsParagraphMovedElsewhere() {
        String text = "Added at the start.\n\n" + CHAPTER;

        assertThat(NotePins.find(text, Map.of("a", new NotePin(4, "Lucia waited"))))
                .containsExactly(Map.entry("a", new NotePin(6, "Lucia waited by the gate.")));
    }

    @Test
    void ofTwoParagraphsStartingTheSameThePinTakesTheNearestToItsNumber() {
        String text = "Rain.\nRain.\nSun.\nSun.\nSun.\nRain.";

        assertThat(NotePins.find(text, Map.of("a", new NotePin(4, "Rain."))))
                .containsExactly(Map.entry("a", new NotePin(5, "Rain.")));
    }

    @Test
    void aParagraphChangedInCapitalsAndSpacingIsFoundAgain() {
        String text = CHAPTER.replace("Lucia waited by the gate.", "lucia  waited by   the Gate.");

        assertThat(NotePins.find(text, Map.of("a", new NotePin(0, "Lucia waited by the gate."))))
                .containsExactly(Map.entry("a", new NotePin(4, "lucia  waited by   the Gate.")));
    }

    @Test
    void aParagraphAtLeastEightyPercentAlikeIsFoundAgain() {
        String text = CHAPTER.replace("Marco opened the letter by the window.", "Marco opened a letter by the window.");

        assertThat(NotePins.find(text, Map.of("a", new NotePin(2, "Marco opened the letter by the window."))))
                .containsExactly(Map.entry("a", new NotePin(2, "Marco opened a letter by the window.")));
    }

    @Test
    void aParagraphLessThanEightyPercentAlikeIsNotFound() {
        String text = CHAPTER.replace("Marco opened the letter by the window.", "Marco opened a letter by the door.");

        assertThat(NotePins.find(text, Map.of("a", new NotePin(2, "Marco opened the letter by the window."))))
                .isEmpty();
    }

    @Test
    void aPinWhoseParagraphIsGoneIsLeftOut() {
        assertThat(NotePins.find(CHAPTER, Map.of("a", new NotePin(2, "Nothing like any paragraph here at all."))))
                .isEmpty();
    }

    @Test
    void theOpeningKeptIsTheParagraphsFirstSixtyCharacters() {
        String line = "Marco opened the letter by the window, read it twice, and folded it back into its envelope.";

        assertThat(NotePins.find(line, Map.of("a", new NotePin(0, line.substring(0, 60)))))
                .containsExactly(Map.entry("a", new NotePin(0, line.substring(0, 60))));
    }

    @Test
    void twoPinsOnOneParagraphKeepTheOneAtItsNumberAndMoveTheOtherToTheNearestFree() {
        String text = "One.\nTwo.\nTwo.\nThree.";
        NotePin atNumber = new NotePin(1, "Two.");
        NotePin moved = new NotePin(7, "Two.");

        assertThat(NotePins.find(text, Map.of("kept", atNumber, "other", moved))).containsOnly(
                Map.entry("kept", atNumber),
                Map.entry("other", new NotePin(2, "Two.")));
    }

    @Test
    void twoPinsOfTheSameParagraphShareItOutToItsNeighbours() {
        String text = "One.\nTwo.\nThree.";

        assertThat(NotePins.find(text, Map.of("a", new NotePin(1, "Two."), "b", new NotePin(1, "Two."))))
                .containsOnly(Map.entry("a", new NotePin(1, "Two.")), Map.entry("b", new NotePin(2, "Three.")));
    }

    @Test
    void theNearestFreeParagraphIsItselfThenAfterThenBeforeSkippingBlankOnes() {
        List<String> lines = NotePins.lines("A\n\nB\nC\n\nD");

        assertThat(NotePins.nearestFree(lines, Set.of(), 2)).isEqualTo(OptionalInt.of(2));
        assertThat(NotePins.nearestFree(lines, Set.of(2), 2)).isEqualTo(OptionalInt.of(3));
        assertThat(NotePins.nearestFree(lines, Set.of(2, 3), 2)).isEqualTo(OptionalInt.of(0));
        assertThat(NotePins.nearestFree(lines, Set.of(), 4)).isEqualTo(OptionalInt.of(5));
        assertThat(NotePins.nearestFree(lines, Set.of(0, 2, 3, 5), 2)).isEmpty();
    }

    @Test
    void aPinBeyondTheTextsEndGoesToItsLastFreeParagraph() {
        List<String> lines = NotePins.lines("A\nB\n");

        assertThat(NotePins.nearestFree(lines, Set.of(), 9)).isEqualTo(OptionalInt.of(1));
    }
}
