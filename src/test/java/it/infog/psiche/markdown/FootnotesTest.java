package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.FootnotePlace;
import it.infog.psiche.markdown.model.NumberedNote;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.FootnotePart;

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
    void tellsWhatPartOfAFootnoteASpotIsInAndWhetherItIsWrong() {
        String text = "Moon[^1] and[^none].\n\n[^1]: A note.\n\n[^b]: Unused.";

        assertThat(Footnotes.at(text, 5, 6)).hasValueSatisfying(place -> {
            assertThat(place.part()).isEqualTo(FootnotePart.REFERENCE);
            assertThat(place.wrong()).isFalse();
        });
        assertThat(Footnotes.at(text, 14, 15).map(FootnotePlace::part)).contains(FootnotePart.MISSING_REFERENCE);
        assertThat(Footnotes.at(text, 30, 31).map(FootnotePlace::part)).contains(FootnotePart.NOTE);
        assertThat(Footnotes.at(text, text.length() - 2, text.length() - 1).map(FootnotePlace::wrong)).contains(true);
        assertThat(Footnotes.at(text, 0, 1)).isEmpty();
    }

    @Test
    void numbersTheNotesAsReadersFindThemThenThoseNothingRefersTo() {
        String text = "Sun[^b] and moon[^a] and[^b].\n\n[^a]: *Moon* note.\n\n[^z]: Unused.\n\n[^b]: Sun.\n\n"
                + "[^b]: Never read.";

        assertThat(Footnotes.numberedNotes(text)).containsExactly(
                new NumberedNote("b", 1, "Sun.", "Sun."),
                new NumberedNote("a", 2, "*Moon* note.", "Moon note."),
                new NumberedNote("z", 3, "Unused.", "Unused."));
    }

    @Test
    void summarisesALongNoteByItsFirstWordsOnOneLine() {
        String text = "Moon[^1].\n\n[^1]: A note long enough to be cut somewhere\n    in its second line.";

        assertThat(Footnotes.numberedNotes(text).getFirst().summary())
                .isEqualTo("A note long enough to be cut somewhere i…");
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
    void findsEachReferenceToNoNoteInAPieceOfProse() {
        assertThat(Footnotes.missingNotes("Moon[^none], sun [^also] and [^ spaced].")).containsExactly(
                new TextRange(4, 11), new TextRange(17, 24));
    }
}
