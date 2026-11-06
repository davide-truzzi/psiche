package it.infog.psiche.export;

import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class XhtmlRendererTest {

    private static final Parser PARSER = Parser.builder().build();

    @Test
    void sceneBreaksAreAsterisksAndRawHtmlIsText() {
        String xhtml = render("Before.\n\n---\n\nAfter <b>this</b> & that.");

        assertThat(xhtml).contains("<p class=\"scene-break\">* * *</p>",
                "<p>After &lt;b&gt;this&lt;/b&gt; &amp; that.</p>");
    }

    @Test
    void htmlCommentsAreLeftOutAndTheRestOfTheHtmlIsKept() {
        String xhtml = render("Before.\n\n<!-- questa è una nota -->\n\nAfter<!-- inline --> <b>bold</b>.\n\n"
                + "<!-- a\nlonger note --> <i>kept</i>");

        assertThat(xhtml).isEqualTo(
                "<p>Before.</p>\n<p>After &lt;b&gt;bold&lt;/b&gt;.</p>\n<p>&lt;i&gt;kept&lt;/i&gt;</p>\n");
    }

    @Test
    void aLineBreakInsideAParagraphIsKept() {
        assertThat(render("One line\nand the next.")).isEqualTo("<p>One line<br />and the next.</p>\n");
    }

    @Test
    void webAndMailLinksStayLinksAndLinksIntoTheBookKeepOnlyTheirText() {
        String xhtml = render("[site](https://example.org), [mail](mailto:a@b.it) and [the *next* one](02-due.md).");

        assertThat(xhtml).contains("<a href=\"https://example.org\">site</a>", "<a href=\"mailto:a@b.it\">mail</a>",
                "and the <em>next</em> one.");
        assertThat(xhtml).doesNotContain("02-due.md");
    }

    private static String render(String markdown) {
        return XhtmlRenderer.render(PARSER.parse(markdown));
    }

    @Test
    void anEbookKeepsSceneBreaksAsRulesAndLinksImagesToItsCopies() {
        XhtmlRenderer ebook = XhtmlRenderer.ebook(destination -> destination.equals("../media/map.png")
                ? Optional.of("../images/image-1.png")
                : Optional.empty());

        String xhtml = ebook.toXhtml(PARSER.parse("Before.\n\n---\n\n![The *salt* road](../media/map.png)\n\n"
                + "![Lost](../media/lost.png)"));

        assertThat(xhtml).isEqualTo("<p>Before.</p>\n<hr />\n"
                + "<figure style=\"margin: 1.5em 0; text-align: center; text-indent: 0;\">"
                + "<img src=\"../images/image-1.png\" alt=\"The salt road\" />"
                + "<figcaption style=\"margin-top: 0.5em; font-size: 0.9em; font-style: italic; text-align: center; "
                + "text-indent: 0;\">The salt road</figcaption></figure>\n");
    }

    @Test
    void theManuscriptPrintsAnImageAloneAsAFigureWithItsCaptionAndNoneIfEmpty() {
        assertThat(render("![The salt road](../media/map.png)\n\n![](../media/sea.png)\n\nSee ![x](../media/x.png)."))
                .isEqualTo("<figure><img src=\"../media/map.png\" alt=\"The salt road\" />"
                        + "<figcaption>The salt road</figcaption></figure>\n"
                        + "<figure><img src=\"../media/sea.png\" alt=\"\" /></figure>\n"
                        + "<p>See <img src=\"../media/x.png\" alt=\"x\" />.</p>\n");
    }

    @Test
    void aTightListsItemsStayWithoutParagraphs() {
        assertThat(render("- north\n- south\n")).isEqualTo("<ul>\n<li>north</li>\n<li>south</li>\n</ul>\n");
    }

    @Test
    void theManuscriptEndsEachChapterTitleWithTheMark() {
        assertThat(render("# Alba\n\nIt began at dawn.\n\n## Morning\n"))
                .isEqualTo("<h1>Alba<span class=\"title-mark\" aria-hidden=\"true\">Ψ</span></h1>\n"
                        + "<p>It began at dawn.</p>\n<h2>Morning</h2>\n");
    }

    @Test
    void anEbookEndsEachChapterTitleWithTheMarkInTheRedPencilsColor() {
        XhtmlRenderer ebook = XhtmlRenderer.ebook(destination -> Optional.empty());

        String xhtml = ebook.toXhtml(PARSER.parse("# *Alba*\n\n## Morning\n"));

        assertThat(xhtml).startsWith("<h1><em>Alba</em><span class=\"title-mark\" aria-hidden=\"true\" style=\"")
                .contains("color: #b8361f;").endsWith("\">Ψ</span></h1>\n<h2>Morning</h2>\n");
    }
}
