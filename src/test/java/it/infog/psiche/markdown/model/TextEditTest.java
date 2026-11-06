package it.infog.psiche.markdown.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextEditTest {

    @Test
    void theEditBetweenTwoTextsReplacesOnlyWhatDiffers() {

        TextEdit edit = TextEdit.between("The old harbour at dawn", "The new harbour at dawn");

        assertThat(edit.start()).isEqualTo(4);
        assertThat(edit.end()).isEqualTo(7);
        assertThat(edit.replacement()).isEqualTo("new");
    }

    @Test
    void textRemovedOrAddedIsAnEditOfItsOwnLength() {

        assertThat(TextEdit.between("First.\n\nImage\n\nSecond.", "First.\n\nSecond."))
                .satisfies(edit -> assertThat(edit.end() - edit.start()).isEqualTo("Image\n\n".length()))
                .extracting(TextEdit::replacement).isEqualTo("");
        assertThat(TextEdit.between("aa", "aaa").replacement()).isEqualTo("a");
    }

    @Test
    void theSameTextNeedsNoEdit() {

        assertThat(TextEdit.between("Same.", "Same.").empty()).isTrue();
        assertThat(TextEdit.between("", "").empty()).isTrue();
        assertThat(TextEdit.between("", "New").empty()).isFalse();
    }
}
