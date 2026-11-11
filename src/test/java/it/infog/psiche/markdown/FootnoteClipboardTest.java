package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextEdit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FootnoteClipboardTest {


    @Test
    void copiesTheNotesOfTheReferencesInTheSelectionAfterIt() {
        String text = "Moon[^1] and sun[^2].\n\n[^1]: First\n    line.\n\n[^2]: Second.";

        assertThat(FootnoteClipboard.copied(text, 0, 8)).isEqualTo("Moon[^1]\n\n[^1]: First\n    line.");
    }

    @Test
    void copiesANoteOnlyOnceAndNotWhenTheSelectionHoldsIt() {
        String text = "Moon[^1] and[^1].\n\n[^1]: A note.";

        assertThat(FootnoteClipboard.copied(text, 0, 17)).isEqualTo("Moon[^1] and[^1].\n\n[^1]: A note.");
        assertThat(FootnoteClipboard.copied(text, 0, text.length())).isEqualTo(text);
    }

    @Test
    void pastesTheNotesAfterTheParagraphTheTextLandsIn() {
        String text = "Before here.\n\nAfter.";

        TextEdit edit = FootnoteClipboard.pasted(text, 6, 6, " Moon[^1]\n\n[^1]: A note.").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Before Moon[^1] here.\n\n[^1]: A note.\n\nAfter.");
        assertThat(edit.selectionStart()).isEqualTo(15);
    }

    @Test
    void numbersAPastedNoteAnewWhenItsLabelIsTaken() {
        String text = "Sun[^1].\n\n[^1]: Sun note.";

        TextEdit edit = FootnoteClipboard.pasted(text, 8, 8, " Moon[^1]\n\n[^1]: Moon note.").orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun[^1]. Moon[^2]\n\n[^2]: Moon note.\n\n[^1]: Sun note.");
    }

    @Test
    void leadsAPastedReferenceToTheNoteTheChapterHasWithTheSameLabelAndText() {
        String text = "Sun[^1] and stars.\n\n[^1]: A note.";
        String cut = FootnoteClipboard.copied("Moon[^1]. " + text, 0, 10);

        TextEdit edit = FootnoteClipboard.pasted(text, 18, 18, cut).orElseThrow();

        assertThat(applied(text, edit)).isEqualTo("Sun[^1] and stars.Moon[^1]. \n\n[^1]: A note.");
    }

    @Test
    void pastesTextWithoutNotesAsItIs() {
        assertThat(FootnoteClipboard.pasted("Text.", 0, 0, "Moon[^1] ")).isEmpty();
    }

    private static String applied(String text, TextEdit edit) {
        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }
}
