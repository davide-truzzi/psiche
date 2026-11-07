package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.enums.EmptyChapters;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class XhtmlRendererTest {

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
        ExportedChapter chapter = BookAssembler.alone("01-alba", markdown);
        return XhtmlRenderer.render(chapter, LinkTargets.alone(chapter));
    }

    private static String ebook(XhtmlRenderer ebook, String markdown) {
        ExportedChapter chapter = BookAssembler.alone("01-alba", markdown);
        return ebook.toXhtml(chapter, LinkTargets.alone(chapter));
    }

    @Test
    void anEbookKeepsSceneBreaksAsRulesAndLinksImagesToItsCopies() {
        XhtmlRenderer ebook = XhtmlRenderer.ebook(destination -> destination.equals("../media/map.png")
                ? Optional.of("../images/image-1.png")
                : Optional.empty(), place -> "");

        String xhtml = ebook(ebook, "Before.\n\n---\n\n![The *salt* road](../media/map.png)\n\n"
                + "![Lost](../media/lost.png)");

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
                .isEqualTo("<h1 id=\"chapter--alba\">Alba<span class=\"title-mark\" aria-hidden=\"true\">Ψ</span>"
                        + "</h1>\n"
                        + "<p>It began at dawn.</p>\n<h2 id=\"chapter--morning\">Morning</h2>\n");
    }

    @Test
    void anEbookEndsEachChapterTitleWithTheMarkInTheRedPencilsColor() {
        XhtmlRenderer ebook = XhtmlRenderer.ebook(destination -> Optional.empty(), place -> "");

        String xhtml = ebook(ebook, "# *Alba*\n\n## Morning\n");

        assertThat(xhtml).startsWith("<h1 id=\"chapter--alba\"><em>Alba</em><span class=\"title-mark\" "
                        + "aria-hidden=\"true\" style=\"")
                .contains("color: #b8361f;").endsWith("\">Ψ</span></h1>\n<h2 id=\"chapter--morning\">Morning</h2>\n");
    }

    @Test
    void linksToAChapterOrHeadingOfTheBookLeadThereAndOthersKeepOnlyTheirText() {
        BookExport book = BookAssembler.assemble(new BookProperties("Alba", "", List.of(), "it", "", ""),
                Optional.empty(), Optional.empty(), Path.of("chapters"), List.of(
                        new ChapterText("01-alba", "# Alba\n\n[due](02-due.md), [the storm](02-due.md#the-storm), "
                                + "[calm](02-due.md#the-calm), [gone](03-gone.md) and [here](#morning).\n\n"
                                + "## Morning\n"),
                        new ChapterText("02-due", "# Due\n\n## The storm\n")), EmptyChapters.SKIP);
        LinkTargets links = LinkTargets.of(book.chapters());

        String manuscript = XhtmlRenderer.render(book.chapters().getFirst(), links);
        assertThat(manuscript).contains("<a href=\"#chapter-2\">due</a>",
                "<a href=\"#chapter-2--the-storm\">the storm</a>", " calm, gone and ",
                "<a href=\"#chapter-1--morning\">here</a>");
        assertThat(XhtmlRenderer.render(book.chapters().get(1), links)).contains("<h2 id=\"chapter-2--the-storm\">");

        XhtmlRenderer ebook = XhtmlRenderer.ebook(destination -> Optional.empty(),
                place -> place.chapterId() + ".xhtml#" + place.elementId());
        assertThat(ebook.toXhtml(book.chapters().getFirst(), links))
                .contains("<a href=\"chapter-2.xhtml#chapter-2--the-storm\">the storm</a>");
    }
}
