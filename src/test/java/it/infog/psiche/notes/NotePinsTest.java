package it.infog.psiche.notes;

import it.infog.psiche.notes.model.NotePin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    // Following the text as it changes

    @Test
    void typingBeforeAParagraphMovesItsStartAndTypingAfterItDoesNot() {
        assertThat(NotePins.shifted(10, 3, "", "abc")).isEqualTo(13);
        assertThat(NotePins.shifted(10, 12, "", "abc")).isEqualTo(10);
        assertThat(NotePins.shifted(10, 10, "", "abc")).isEqualTo(10);
    }

    @Test
    void lineBreaksTypedAtAParagraphsStartPushItDown() {
        assertThat(NotePins.shifted(10, 10, "", "\n\n")).isEqualTo(12);
    }

    @Test
    void aParagraphWhoseStartIsTakenAwayStartsWhereTheChangeIs() {
        assertThat(NotePins.shifted(10, 5, "abcdefgh", "")).isEqualTo(5);
        assertThat(NotePins.shifted(10, 8, "\n\n", "")).isEqualTo(8);
    }

    @Test
    void typingInsideAPinnedParagraphKeepsItsPinAndItsNewOpening() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("a", 2));

        editing.change(5, "", "Zero.\n\n");

        assertThat(editing.pins).containsExactly(Map.entry("a", new NotePin(4, "Two.")));
        assertThat(editing.moved).isEmpty();
    }

    @Test
    void twoPinnedParagraphsJoinedKeepTheFirstsPinAndMoveTheSecondToTheNearestFreeAfter() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("first", 0, "second", 2));

        editing.change(4, "\n\n", "");

        assertThat(editing.text).isEqualTo("One.Two.\n\nThree.");
        assertThat(editing.pins).containsOnly(Map.entry("first", new NotePin(0, "One.Two.")),
                Map.entry("second", new NotePin(2, "Three.")));
        assertThat(editing.moved).containsExactly(Map.entry("second", 2));
    }

    @Test
    void undoingTheJoinBringsTheMovedPinBackToItsParagraph() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("first", 0, "second", 2));
        editing.change(4, "\n\n", "");

        editing.change(4, "", "\n\n");

        assertThat(editing.pins).containsOnly(Map.entry("first", new NotePin(0, "One.")),
                Map.entry("second", new NotePin(2, "Two.")));
        assertThat(editing.homes).isEmpty();
    }

    @Test
    void aPinnedParagraphDeletedSendsItsPinToTheNearestFreeParagraph() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("a", 2, "b", 4));

        editing.change(4, "\n\nTwo.", "");

        assertThat(editing.text).isEqualTo("One.\n\nThree.");
        assertThat(editing.pins).containsOnly(Map.entry("a", new NotePin(0, "One.")),
                Map.entry("b", new NotePin(2, "Three.")));
        assertThat(editing.moved).containsExactly(Map.entry("a", 0));
    }

    @Test
    void aParagraphJoinedToAnUnpinnedOneAboveKeepsItsPinOnTheJoinedParagraph() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("a", 2));

        editing.change(4, "\n\n", "");

        assertThat(editing.pins).containsExactly(Map.entry("a", new NotePin(0, "One.Two.")));
        assertThat(editing.moved).isEmpty();
    }

    @Test
    void undoingADeletionBringsThePinBackToItsParagraph() {
        Editing editing = new Editing("One.\n\nTwo.\n\nThree.", Map.of("a", 2));
        editing.change(4, "\n\nTwo.", "");

        editing.change(4, "", "\n\nTwo.");

        assertThat(editing.pins).containsExactly(Map.entry("a", new NotePin(2, "Two.")));
    }

    @Test
    void aPinnedParagraphEmptiedSendsItsPinToTheNearestFreeParagraph() {
        Editing editing = new Editing("One.\nTwo.\nThree.", Map.of("a", 1));

        editing.change(5, "Two.", "");

        assertThat(editing.pins).containsExactly(Map.entry("a", new NotePin(2, "Three.")));
    }

    @Test
    void aPinWithNoFreeParagraphLeftIsLetGo() {
        Editing editing = new Editing("One.", Map.of("a", 0));

        editing.change(0, "One.", "");

        assertThat(editing.pins).isEmpty();
    }

    @Test
    void aMovedPinGoesBackOnlyToAFreeParagraphStartingWithItsOpening() {
        List<String> lines = NotePins.lines("Two.\nOne.\nTwo.");

        assertThat(NotePins.startingWith(lines, Set.of(), new NotePin(1, "Two."))).isEqualTo(OptionalInt.of(0));
        assertThat(NotePins.startingWith(lines, Set.of(0), new NotePin(1, "Two."))).isEqualTo(OptionalInt.of(2));
        assertThat(NotePins.startingWith(lines, Set.of(0, 2), new NotePin(1, "Two."))).isEmpty();
    }

    /** A chapter's text being edited with pins on it, as the editor follows them. */
    private static final class Editing {

        private final Map<String, NotePin> homes = new HashMap<>();
        private final Map<String, Integer> moved = new LinkedHashMap<>();
        private String text;
        private Map<String, NotePin> pins = new LinkedHashMap<>();

        Editing(String text, Map<String, Integer> paragraphs) {
            this.text = text;
            List<String> lines = NotePins.lines(text);
            paragraphs.entrySet().stream().sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> pins.put(entry.getKey(), NotePin.of(entry.getValue(),
                            lines.get(entry.getValue()))));
        }

        /** Replaces {@code removed}, at {@code at}, with {@code inserted}, and moves the pins as the editor does. */
        void change(int at, String removed, String inserted) {
            List<String> before = NotePins.lines(text);
            text = text.substring(0, at) + inserted + text.substring(at + removed.length());

            Map<String, Integer> paragraphs = new LinkedHashMap<>();
            Set<String> uprooted = new HashSet<>();
            pins.forEach((id, pin) -> {
                int start = start(before, pin.paragraph());
                paragraphs.put(id, paragraphOf(NotePins.shifted(start, at, removed, inserted)));
                if (NotePins.uprooted(start, at, removed))
                    uprooted.add(id);
            });
            moved.clear();
            pins = NotePins.settled(NotePins.lines(text), paragraphs, uprooted, pins, homes, moved::put);
        }

        private static int start(List<String> lines, int paragraph) {
            int start = 0;
            for (int i = 0; i < paragraph; i++)
                start += lines.get(i).length() + 1;
            return start;
        }

        private int paragraphOf(int offset) {
            return (int) text.substring(0, offset).chars().filter(character -> character == '\n').count();
        }
    }
}
