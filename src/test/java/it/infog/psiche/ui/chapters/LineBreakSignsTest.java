package it.infog.psiche.ui.chapters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineBreakSignsTest {

    @Test
    void everyLineEndingShowsAsTheEnterSign() {

        assertThat(LineBreakSigns.shown("one\ntwo\r\nthree\rfour")).isEqualTo("one↵two↵three↵four");
    }

    @Test
    void everyEnterSignIsALineBreakAgain() {

        assertThat(LineBreakSigns.text("end.↵↵New")).isEqualTo("end.\n\nNew");
        assertThat(LineBreakSigns.text(LineBreakSigns.shown("a\r\nb"))).isEqualTo("a\nb");
    }
}
