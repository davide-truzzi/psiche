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
    void findsEachReferenceToNoNoteInAPieceOfProse() {
        assertThat(Footnotes.missingNotes("Moon[^none], sun [^also] and [^ spaced].")).containsExactly(
                new TextRange(4, 11), new TextRange(17, 24));
    }
}
