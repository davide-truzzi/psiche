package it.infog.psiche.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @TempDir
    Path chaptersDir;

    private final MarkdownRenderer renderer = new MarkdownRenderer("p { text-indent: 1.6em; }");

    @Test
    void rendersMarkdownAsHtmlInsideTheArticle() {
        String page = renderer.render("# The storm\n\nIt was *very* dark.", chaptersDir);

        assertThat(page).contains(
                "<article>\n<h1 data-line=\"0\">The storm</h1>\n<p data-line=\"2\">It was <em>very</em> dark.</p>\n</article>");
    }

    @Test
    void marksEachBlockWithTheLineItStartsOn() {
        String page = renderer.render("# The storm\n\nIt was dark.\nAnd cold.\n\n> Run!\n\n- north\n- south", chaptersDir);

        assertThat(page)
                .contains("<h1 data-line=\"0\">")
                .contains("<p data-line=\"2\">It was dark.<br>")
                .contains("<blockquote data-line=\"5\">\n<p data-line=\"5\">Run!</p>")
                .contains("<li data-line=\"7\">north</li>")
                .contains("<li data-line=\"8\">south</li>");
    }

    @Test
    void resolvesRelativeMediaLinksAgainstTheGivenDirectory() {
        String page = renderer.render("![Map](../media/map.png)", chaptersDir);

        assertThat(page)
                .contains("<base href=\"" + chaptersDir.toUri() + "\">")
                .contains("<img src=\"../media/map.png\" alt=\"Map\" />");
    }

    @Test
    void embedsTheStylesheet() {
        assertThat(renderer.render("", chaptersDir)).contains("<style>p { text-indent: 1.6em; }</style>");
    }
}
