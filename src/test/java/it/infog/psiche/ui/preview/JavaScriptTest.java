package it.infog.psiche.ui.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaScriptTest {

    @Test
    void anArticleBecomesOneJavaScriptStringWhateverItHolds() {

        String html = "<p class=\"x\">Line one\nC:\\path\r   %s 'quoted'</p>";

        assertThat(JavaScript.string(html))
                .isEqualTo("\"<p class=\\\"x\\\">Line one\\nC:\\\\path\\r\\u2028\\u2029 %s 'quoted'</p>\"");
    }
}
