package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
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
        assertThat(marks.notes()).containsExactly(new FootnoteMark("moon", new TextRange(45, 53)),
                new FootnoteMark("b", new TextRange(65, 70)));
        assertThat(marks.missing()).isEmpty();
        assertThat(marks.ranges()).containsExactly(new TextRange(4, 11), new TextRange(19, 26), new TextRange(45, 53),
                new TextRange(65, 70));
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
                new FootnoteMark("unused", new TextRange(42, 52)),
                new FootnoteMark("MOON", new TextRange(66, 74)));
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
    void findsEachReferenceToNoNoteInAPieceOfProse() {
        assertThat(Footnotes.missingNotes("Moon[^none], sun [^also] and [^ spaced].")).containsExactly(
                new TextRange(4, 11), new TextRange(17, 24));
    }
}
