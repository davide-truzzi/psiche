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
                "<article>\n<h1 data-start=\"0\" data-end=\"11\">The storm</h1>\n"
                        + "<p data-start=\"13\" data-end=\"32\">It was <em>very</em> dark.</p>\n</article>");
    }

    @Test
    void marksEachBlockWithWhereItStartsAndEndsInTheText() {
        String page = renderer.render("# The storm\n\nIt was dark.\nAnd cold.\n\n> Run!\n\n- north\n- south",
                chaptersDir);

        assertThat(page)
                .contains("<h1 data-start=\"0\" data-end=\"11\">")
                .contains("<p data-start=\"13\" data-end=\"35\">It was dark.<br>")
                .contains("<blockquote data-start=\"37\" data-end=\"43\">\n"
                        + "<p data-start=\"39\" data-end=\"43\">Run!</p>")
                .contains("<ul data-start=\"45\" data-end=\"60\">")
                .contains("<li data-start=\"45\" data-end=\"52\">north</li>")
                .contains("<li data-start=\"53\" data-end=\"60\">south</li>");
    }

    @Test
    void endsAParagraphOfSeveralLinesAtTheEndOfItsLastLine() {
        String page = renderer.render("One.\nTwo.\nThree.\n", chaptersDir);

        assertThat(page).contains("<p data-start=\"0\" data-end=\"16\">One.<br>");
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
