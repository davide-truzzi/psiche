package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class FootnotesTest {

    private static final String TEXT =
            "Moon[^moon] and sun[^Moon], code `[^moon]`.\n\n[^moon]: The moon.\n\n[^b]: Unused.";

    @Test
    void findsEachReferenceAndEachNotesOpening() {
        FootnoteMarks marks = Footnotes.of(TEXT);

        assertThat(marks.references()).containsExactly(new FootnoteMark("moon", new TextRange(4, 11)),
                new FootnoteMark("Moon", new TextRange(19, 26)));
        assertThat(marks.notes()).containsExactly(
                new FootnoteMark("moon", new TextRange(45, 53), new TextRange(45, 63)),
                new FootnoteMark("b", new TextRange(65, 70), new TextRange(65, 78)));
        assertThat(marks.missing()).isEmpty();
        assertThat(marks.ranges()).containsExactly(new TextRange(4, 11), new TextRange(19, 26), new TextRange(45, 53),
                new TextRange(65, 70));
    }

    @Test
    void keepsEachReferenceAndEachWholeNoteAsABlockIndentedParagraphsIncluded() {
        String text = "Moon[^1] and[^none].\n\n[^1]: First\n    line.\n\n    Second.\n\nAfter.";

        assertThat(Footnotes.of(text).blocks()).containsExactly(new TextRange(4, 8), new TextRange(12, 19),
                new TextRange(22, 56));
    }

    @Test
    void leadsAPreviewLinkToItsNoteOrBackToItsReference() {
        assertThat(Footnotes.place(TEXT, "#fn-moon")).isEqualTo(OptionalInt.of(45));
        assertThat(Footnotes.place(TEXT, "#fnref-moon")).isEqualTo(OptionalInt.of(4));
        assertThat(Footnotes.place(TEXT, "#fnref-moon-2")).isEqualTo(OptionalInt.of(19));
    }

    @Test
    void leadsALinkToNoFootnoteNowhere() {
        assertThat(Footnotes.place(TEXT, "#fn-sun")).isEmpty();
        assertThat(Footnotes.place(TEXT, "#fnref-moon-3")).isEmpty();
        assertThat(Footnotes.place(TEXT, "#the-moon")).isEmpty();
    }

    @Test
    void tellsTheFootnotesLinksFromOthers() {
        assertThat(Footnotes.isFootnoteLink("#fn-moon")).isTrue();
        assertThat(Footnotes.isFootnoteLink("#fnref-moon-2")).isTrue();
        assertThat(Footnotes.isFootnoteLink("02-due.md#fn-moon")).isFalse();
    }

    @Test
    void findsEachReferenceToNoNoteButNotInCode() {
        assertThat(Footnotes.of("Moon[^none] and `[^code]`.").missing())
                .containsExactly(new FootnoteMark("none", new TextRange(4, 11)));
    }

    @Test
    void findsTheWrongMarksAReferenceToNoNoteANoteNoneLeadsToAndASecondNoteOfALabel() {
        String text = "Moon[^moon] and[^none].\n\n[^moon]: First.\n\n[^unused]: Never read.\n\n[^MOON]: Second.";

        assertThat(Footnotes.wrong(Footnotes.of(text))).containsExactly(
                new FootnoteMark("none", new TextRange(15, 22)),
                new FootnoteMark("unused", new TextRange(42, 52), new TextRange(42, 64)),
                new FootnoteMark("MOON", new TextRange(66, 74), new TextRange(66, 82)));
    }

    @Test
    void putsTheMarkersAroundTheLabelOfAReferenceAndOfANote() {
        assertThat(Footnotes.markers(new FootnoteMark("moon", new TextRange(4, 11))))
                .containsExactly(new TextRange(4, 6), new TextRange(10, 11));
        assertThat(Footnotes.markers(new FootnoteMark("moon", new TextRange(45, 53))))
                .containsExactly(new TextRange(45, 47), new TextRange(51, 53));
    }

    @Test
    void leadsAMarksLinkToItsNoteOrBackToItsFirstReference() {
        assertThat(Footnotes.place(TEXT, Footnotes.noteLink("Moon"))).isEqualTo(OptionalInt.of(45));
        assertThat(Footnotes.place(TEXT, Footnotes.referenceLink("moon"))).isEqualTo(OptionalInt.of(4));
        assertThat(Footnotes.place(TEXT, Footnotes.referenceLink("b"))).isEmpty();
    }

    @Test
    void insertsAReferenceAtThePositionAndItsNoteAfterTheParagraphWithTheCaretThere() {
        String text = "The moon rose.\nIt was full.\n\nNext.";

        TextEdit edit = Footnotes.insert(text, 8);
        String changed = text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());

        assertThat(changed).isEqualTo("The moon[^1] rose.\nIt was full.\n\n[^1]: \n\nNext.");
        assertThat(edit.selectionStart()).isEqualTo(edit.selectionEnd()).isEqualTo(changed.indexOf("[^1]: ") + 6);
    }

    @Test
    void insertsANoteAtTheEndOfTheTextAfterItsLastParagraph() {
        TextEdit edit = Footnotes.insert("Last.", 5);

        assertThat(edit.replacement()).isEqualTo("[^1]\n\n[^1]: ");
    }

    @Test
    void numbersANewNoteOneAfterTheHighestNumberUsed() {
        String text = "A[^2] b[^moon] c[^7].\n\n[^2]: Two.";

        assertThat(Footnotes.insert(text, 1).replacement()).startsWith("[^8]");
    }

    @Test
    void removingTheOnlyReferenceRemovesItsNoteWithABlankLine() {
        String text = "Moon[^1] rose.\n\n[^1]: A note.\n\nAfter.";

        assertThat(applied(text, Footnotes.withNotes(text, 4, 8, "").orElseThrow())).isEqualTo("Moon rose.\n\nAfter.");
    }

    @Test
    void removingOneOfTwoReferencesKeepsTheNote() {
        String text = "Moon[^1] and sun[^1].\n\n[^1]: A note.";

        assertThat(Footnotes.withNotes(text, 4, 8, "")).isEmpty();
    }

    @Test
    void removingANoteRemovesEveryReferenceToItAndPutsTheCaretWhereTheNoteWas() {
        String text = "Moon[^1] and sun[^1].\n\n[^1]: A note.\n    More.\n\nAfter.";
        int note = text.indexOf("[^1]:");

        TextEdit edit = Footnotes.withNotes(text, note, note + "[^1]: A note.\n    More.".length(), "").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Moon and sun.\n\n\n\nAfter.");
        assertThat(edit.selectionStart()).isEqualTo("Moon and sun.\n\n".length());
    }

    @Test
    void removingASecondNoteOfALabelKeepsItsReferences() {
        String text = "Moon[^1].\n\n[^1]: First.\n\n[^1]: Second.";
        int second = text.indexOf("[^1]: Second.");

        assertThat(Footnotes.withNotes(text, second, text.length(), "")).isEmpty();
    }

    @Test
    void removingAReferenceAndItsNoteTogetherLeavesTheRestAsItIs() {
        String text = "Moon[^1].\n\n[^1]: A note.\n\nAfter.";

        assertThat(Footnotes.withNotes(text, 0, text.indexOf("After"), "")).isEmpty();
    }

    @Test
    void typingOverAReferenceKeepsWhatIsTypedAndRemovesTheNote() {
        String text = "Moon[^1] rose.\n\n[^1]: A note.";

        TextEdit edit = Footnotes.withNotes(text, 0, 8, "Sun").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun rose.");
        assertThat(edit.selectionStart()).isEqualTo(3);
    }

    @Test
    void findsEachReferenceToNoNoteInAPieceOfProse() {
        assertThat(Footnotes.missingNotes("Moon[^none], sun [^also] and [^ spaced].")).containsExactly(
                new TextRange(4, 11), new TextRange(17, 24));
    }

    private static String applied(String text, TextEdit edit) {
        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }
}
