package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.TextEdit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FootnoteEditsTest {

    @Test
    void insertsANoteFromTheDialogAfterTheParagraphWithTheCaretAfterTheReference() {
        String text = "The moon rose.\n\nNext.";

        TextEdit edit = FootnoteEdits.insert(text, 8, "Full.\n\nAnd red.");

        assertThat(applied(text, edit)).isEqualTo("The moon[^1] rose.\n\n[^1]: Full.\n\n    And red.\n\nNext.");
        assertThat(edit.selectionStart()).isEqualTo(12);
    }

    @Test
    void refersToANoteTheChapterHasWithTheCaretAfterTheReference() {
        TextEdit edit = FootnoteEdits.referenced(4, "moon");

        assertThat(applied("Also rose.", edit)).isEqualTo("Also[^moon] rose.");
        assertThat(edit.selectionStart()).isEqualTo(11);
    }

    @Test
    void copiesTheNotesOfTheReferencesInTheSelectionAfterIt() {
        String text = "Moon[^1] and sun[^2].\n\n[^1]: First\n    line.\n\n[^2]: Second.";

        assertThat(FootnoteEdits.copied(text, 0, 8)).isEqualTo("Moon[^1]\n\n[^1]: First\n    line.");
    }

    @Test
    void copiesANoteOnlyOnceAndNotWhenTheSelectionHoldsIt() {
        String text = "Moon[^1] and[^1].\n\n[^1]: A note.";

        assertThat(FootnoteEdits.copied(text, 0, 17)).isEqualTo("Moon[^1] and[^1].\n\n[^1]: A note.");
        assertThat(FootnoteEdits.copied(text, 0, text.length())).isEqualTo(text);
    }

    @Test
    void pastesTheNotesAfterTheParagraphTheTextLandsIn() {
        String text = "Before here.\n\nAfter.";

        TextEdit edit = FootnoteEdits.pasted(text, 6, 6, " Moon[^1]\n\n[^1]: A note.").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Before Moon[^1] here.\n\n[^1]: A note.\n\nAfter.");
        assertThat(edit.selectionStart()).isEqualTo(15);
    }

    @Test
    void numbersAPastedNoteAnewWhenItsLabelIsTaken() {
        String text = "Sun[^1].\n\n[^1]: Sun note.";

        TextEdit edit = FootnoteEdits.pasted(text, 8, 8, " Moon[^1]\n\n[^1]: Moon note.").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun[^1]. Moon[^2]\n\n[^2]: Moon note.\n\n[^1]: Sun note.");
    }

    @Test
    void leadsAPastedReferenceToTheNoteTheChapterHasWithTheSameLabelAndText() {
        String text = "Sun[^1] and stars.\n\n[^1]: A note.";
        String cut = FootnoteEdits.copied("Moon[^1]. " + text, 0, 10);

        TextEdit edit = FootnoteEdits.pasted(text, 18, 18, cut).orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun[^1] and stars.Moon[^1]. \n\n[^1]: A note.");
    }

    @Test
    void pastesTextWithoutNotesAsItIs() {
        assertThat(FootnoteEdits.pasted("Text.", 0, 0, "Moon[^1] ")).isEmpty();
    }

    @Test
    void setsInTheLinesAReplacementStartsInANote() {
        assertThat(FootnoteEdits.inNote("one\ntwo")).isEqualTo("one\n    two");
    }

    @Test
    void setsInEveryLineOfANoteAfterTheFirstAndKeepsOneBlankLineBetweenParagraphs() {
        String text = "Moon[^1].\n\n[^1]: Old.";
        FootnoteMark note = Footnotes.of(text).notes().getFirst();

        TextEdit edit = FootnoteEdits.modified(note, "\nOne line\nand the next.\n\n\n\nSecond.\n\n");

        assertThat(applied(text, edit)).isEqualTo("Moon[^1].\n\n[^1]: One line\n    and the next.\n\n    Second.");
    }

    @Test
    void givesANotesTextWithoutItsOpeningNorItsIndent() {
        String text = "Moon[^1].\n\n[^1]: One line\n    and the next.\n\n    Second.\n\nAfter.";

        assertThat(FootnoteEdits.noteText(text, Footnotes.of(text).notes().getFirst()))
                .isEqualTo("One line\nand the next.\n\nSecond.");
    }

    @Test
    void writesANoteAsItWasReadBackUnchanged() {
        String text = "Moon[^1].\n\n[^1]: One line\n    and the next.\n\n    Second.";
        FootnoteMark note = Footnotes.of(text).notes().getFirst();

        assertThat(applied(text, FootnoteEdits.modified(note, FootnoteEdits.noteText(text, note)))).isEqualTo(text);
    }

    @Test
    void removesEveryNoteOfALabelAndEveryReferenceToIt() {
        String text = "Moon[^1] and sun[^1].\n\n[^1]: A note.\n\nAfter[^2].\n\n[^2]: Other.";

        assertThat(applied(text, FootnoteEdits.removed(text, "1")))
                .isEqualTo("Moon and sun.\n\nAfter[^2].\n\n[^2]: Other.");
    }

    @Test
    void removesAWrongNoteOrAReferenceToNoNoteAlone() {
        String text = "Moon[^none].\n\n[^b]: Unused.\n\nAfter.";
        FootnoteMark unused = Footnotes.of(text).notes().getFirst();
        FootnoteMark missing = Footnotes.of(text).missing().getFirst();

        assertThat(applied(text, FootnoteEdits.removedNote(text, unused))).isEqualTo("Moon[^none].\n\nAfter.");
        assertThat(applied(text, FootnoteEdits.removedReference(text, missing)))
                .isEqualTo("Moon.\n\n[^b]: Unused.\n\nAfter.");
    }

    @Test
    void writesANoteForAReferenceToNoNoteAfterItsParagraph() {
        String text = "Moon[^x] rose.\n\nAfter.";
        FootnoteMark missing = Footnotes.of(text).missing().getFirst();

        assertThat(applied(text, FootnoteEdits.added(text, missing, "Now it has one.")))
                .isEqualTo("Moon[^x] rose.\n\n[^x]: Now it has one.\n\nAfter.");
    }

    @Test
    void insertsAReferenceAtThePositionAndItsNoteAfterTheParagraphWithTheCaretThere() {
        String text = "The moon rose.\nIt was full.\n\nNext.";

        TextEdit edit = FootnoteEdits.insert(text, 8);
        String changed = text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());

        assertThat(changed).isEqualTo("The moon[^1] rose.\nIt was full.\n\n[^1]: \n\nNext.");
        assertThat(edit.selectionStart()).isEqualTo(edit.selectionEnd()).isEqualTo(changed.indexOf("[^1]: ") + 6);
    }

    @Test
    void insertsANoteAtTheEndOfTheTextAfterItsLastParagraph() {
        TextEdit edit = FootnoteEdits.insert("Last.", 5);

        assertThat(edit.replacement()).isEqualTo("[^1]\n\n[^1]: ");
    }

    @Test
    void numbersANewNoteOneAfterTheHighestNumberUsed() {
        String text = "A[^2] b[^moon] c[^7].\n\n[^2]: Two.";

        assertThat(FootnoteEdits.insert(text, 1).replacement()).startsWith("[^8]");
    }

    @Test
    void removingTheOnlyReferenceRemovesItsNoteWithABlankLine() {
        String text = "Moon[^1] rose.\n\n[^1]: A note.\n\nAfter.";

        assertThat(applied(text, FootnoteEdits.withNotes(text, 4, 8, "").orElseThrow()))
                .isEqualTo("Moon rose.\n\nAfter.");
    }

    @Test
    void removingOneOfTwoReferencesKeepsTheNote() {
        String text = "Moon[^1] and sun[^1].\n\n[^1]: A note.";

        assertThat(FootnoteEdits.withNotes(text, 4, 8, "")).isEmpty();
    }

    @Test
    void removingANoteRemovesEveryReferenceToItAndPutsTheCaretWhereTheNoteWas() {
        String text = "Moon[^1] and sun[^1].\n\n[^1]: A note.\n    More.\n\nAfter.";
        int note = text.indexOf("[^1]:");

        int end = note + "[^1]: A note.\n    More.".length();

        TextEdit edit = FootnoteEdits.withNotes(text, note, end, "").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Moon and sun.\n\n\n\nAfter.");
        assertThat(edit.selectionStart()).isEqualTo("Moon and sun.\n\n".length());
    }

    @Test
    void removingASecondNoteOfALabelKeepsItsReferences() {
        String text = "Moon[^1].\n\n[^1]: First.\n\n[^1]: Second.";
        int second = text.indexOf("[^1]: Second.");

        assertThat(FootnoteEdits.withNotes(text, second, text.length(), "")).isEmpty();
    }

    @Test
    void removingAReferenceAndItsNoteTogetherLeavesTheRestAsItIs() {
        String text = "Moon[^1].\n\n[^1]: A note.\n\nAfter.";

        assertThat(FootnoteEdits.withNotes(text, 0, text.indexOf("After"), "")).isEmpty();
    }

    @Test
    void typingOverAReferenceKeepsWhatIsTypedAndRemovesTheNote() {
        String text = "Moon[^1] rose.\n\n[^1]: A note.";

        TextEdit edit = FootnoteEdits.withNotes(text, 0, 8, "Sun").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun rose.");
        assertThat(edit.selectionStart()).isEqualTo(3);
    }

    private static String applied(String text, TextEdit edit) {
        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }
}
