package it.infog.psiche.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @TempDir
    Path chaptersDir;

    private final MarkdownRenderer renderer = new MarkdownRenderer("p { text-indent: 1.6em; }");

    @Test
    void rendersMarkdownAsHtmlInsideTheArticle() {
        String page = renderer.render("# The storm\n\nIt was *very* dark.", chaptersDir, 16);

        assertThat(page).contains(
                "<article>\n<h1 data-start=\"0\" data-end=\"11\">The storm</h1>\n"
                        + "<p data-start=\"13\" data-end=\"32\">It was <em>very</em> dark.</p>\n</article>");
    }

    @Test
    void marksEachBlockWithWhereItStartsAndEndsInTheText() {
        String page = renderer.render("# The storm\n\nIt was dark.\nAnd cold.\n\n> Run!\n\n- north\n- south",
                chaptersDir, 16);

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
        String page = renderer.render("One.\nTwo.\nThree.\n", chaptersDir, 16);

        assertThat(page).contains("<p data-start=\"0\" data-end=\"16\">One.<br>");
    }

    @Test
    void resolvesRelativeMediaLinksAgainstTheGivenDirectory() {
        String page = renderer.render("![Map](../media/map.png)", chaptersDir, 16);

        assertThat(page)
                .contains("<base href=\"" + chaptersDir.toUri() + "\">")
                .contains("<img src=\"../media/map.png?v=0\" alt=\"Map\" />");
    }

    @Test
    void asksForAnImageWithWhenItsFileLastChanged() throws IOException {
        Path chapters = Files.createDirectories(chaptersDir.resolve("chapters"));
        Path map = Files.createDirectories(chaptersDir.resolve("media")).resolve("map.png");
        Files.write(map, new byte[]{1});
        Files.setLastModifiedTime(map, FileTime.fromMillis(1_700_000_000_000L));

        assertThat(renderer.renderArticle("![Map](../media/map.png)", chapters))
                .contains("<img src=\"../media/map.png?v=1700000000000\"");
    }

    @Test
    void asksForAnImageOnTheWebAsWritten() {
        assertThat(renderer.renderArticle("![Map](https://example.org/map.png)", chaptersDir))
                .contains("<img src=\"https://example.org/map.png\"");
    }

    @Test
    void embedsTheStylesheet() {
        assertThat(renderer.render("", chaptersDir, 16)).contains("<style>p { text-indent: 1.6em; }</style>");
    }

    @Test
    void setsTheTextSizeAfterTheStylesheetSoItWins() {
        String page = renderer.render("", chaptersDir, 22);

        assertThat(page.indexOf("<style>body { font-size: 22px; }</style>"))
                .isGreaterThan(page.indexOf("<style>p { text-indent: 1.6em; }</style>"));
    }
}
