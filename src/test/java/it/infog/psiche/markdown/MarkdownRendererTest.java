package it.infog.psiche.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @TempDir
    Path chaptersDir;

    // No link of these texts leads nowhere.
    private static final Predicate<String> NONE_BROKEN = destination -> false;

    private final MarkdownRenderer renderer = new MarkdownRenderer("p { text-indent: 1.6em; }");

    @Test
    void rendersMarkdownAsHtmlInsideTheArticle() {
        String page = renderer.render("# The storm\n\nIt was *very* dark.", chaptersDir, 16, NONE_BROKEN);

        assertThat(page).contains(
                "<article>\n<h1 data-start=\"0\" data-end=\"11\" id=\"the-storm\">The storm</h1>\n"
                        + "<p data-start=\"13\" data-end=\"32\">It was <em>very</em> dark.</p>\n</article>");
    }

    @Test
    void drawsStrikethroughSubscriptAndSuperscript() {
        String page = renderer.render("~~Water~~ H~2~O, the 1^st^.", chaptersDir, 16, NONE_BROKEN);

        assertThat(page).contains("<del>Water</del> H<sub>2</sub>O, the 1<sup>st</sup>.");
    }

    @Test
    void marksEachBlockWithWhereItStartsAndEndsInTheText() {
        String page = renderer.render("# The storm\n\nIt was dark.\nAnd cold.\n\n> Run!\n\n- north\n- south",
                chaptersDir, 16, NONE_BROKEN);

        assertThat(page)
                .contains("<h1 data-start=\"0\" data-end=\"11\" id=\"the-storm\">")
                .contains("<p data-start=\"13\" data-end=\"35\">It was dark.<br>")
                .contains("<blockquote data-start=\"37\" data-end=\"43\">\n"
                        + "<p data-start=\"39\" data-end=\"43\">Run!</p>")
                .contains("<ul data-start=\"45\" data-end=\"60\">")
                .contains("<li data-start=\"45\" data-end=\"52\">north</li>")
                .contains("<li data-start=\"53\" data-end=\"60\">south</li>");
    }

    @Test
    void endsAParagraphOfSeveralLinesAtTheEndOfItsLastLine() {
        String page = renderer.render("One.\nTwo.\nThree.\n", chaptersDir, 16, NONE_BROKEN);

        assertThat(page).contains("<p data-start=\"0\" data-end=\"16\">One.<br>");
    }

    @Test
    void resolvesRelativeMediaLinksAgainstTheGivenDirectory() {
        String page = renderer.render("![Map](../media/map.png)", chaptersDir, 16, NONE_BROKEN);

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

        assertThat(renderer.renderArticle("![Map](../media/map.png)", chapters, NONE_BROKEN))
                .contains("<img src=\"../media/map.png?v=1700000000000\"");
    }

    @Test
    void asksForAnImageOnTheWebAsWritten() {
        assertThat(renderer.renderArticle("![Map](https://example.org/map.png)", chaptersDir, NONE_BROKEN))
                .contains("<img src=\"https://example.org/map.png\"");
    }

    @Test
    void embedsTheStylesheet() {
        assertThat(renderer.render("", chaptersDir, 16, NONE_BROKEN))
                .contains("<style>p { text-indent: 1.6em; }</style>");
    }

    @Test
    void setsTheTextSizeAfterTheStylesheetSoItWins() {
        String page = renderer.render("", chaptersDir, 22, NONE_BROKEN);

        assertThat(page.indexOf("<style>body { font-size: 22px; }</style>"))
                .isGreaterThan(page.indexOf("<style>p { text-indent: 1.6em; }</style>"));
    }

    @Test
    void showsAnImageAloneAsAFigureThatKeepsWhereItIsInTheText() {
        assertThat(renderer.renderArticle("Text.\n\n![The salt road](https://example.org/map.png)\n", chaptersDir,
                NONE_BROKEN))
                .contains("<figure data-start=\"7\" data-end=\"52\"><img src=\"https://example.org/map.png\" "
                        + "alt=\"The salt road\" /><figcaption>The salt road</figcaption></figure>");
    }

    @Test
    void eachHeadingHasItsAnchorAsItsIdRepeatsNumbered() {

        assertThat(renderer.renderArticle("## Night\n\n## Night\n", chaptersDir, NONE_BROKEN))
                .contains("id=\"night\">Night</h2>")
                .contains("id=\"night-1\">Night</h2>");
    }

    @Test
    void aLinkKeepsItsTitleAndOneThatLeadsNowhereIsItsTextAloneMarkedBroken() {

        String article = renderer.renderArticle("[the web](https://example.org \"Site\") and [the *calm*](03-gone.md)",
                chaptersDir, "03-gone.md"::equals);

        assertThat(article)
                .contains("<a href=\"https://example.org\" title=\"Site\">the web</a>")
                .contains("<span class=\"broken-link\">the <em>calm</em></span>");
    }
}
