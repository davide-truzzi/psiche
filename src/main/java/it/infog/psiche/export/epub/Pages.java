package it.infog.psiche.export.epub;

import it.infog.psiche.export.Template;
import it.infog.psiche.export.XhtmlRenderer;
import it.infog.psiche.export.epub.model.EpubImage;
import it.infog.psiche.export.epub.model.EpubPage;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.TitlePage;

import java.util.Map;
import java.util.Optional;

import static it.infog.psiche.export.XhtmlRenderer.escape;

/**
 * The EPUB's XHTML pages, all from {@code page.xhtml}. Each carries its part of the book in {@code epub:type} and in
 * a DPUB-ARIA role, so e-readers and screen readers know a chapter from the cover or the title page.
 */
final class Pages {

    // From a page, in xhtml/, to the stylesheets in css/.
    static final String BOOK_STYLE = "css/style.css";
    static final String FRONT_MATTER_STYLE = "css/front-matter.css";

    private static final String BOOK_STYLESHEET = "<link rel=\"stylesheet\" type=\"text/css\" href=\"../%s\"/>\n"
            .formatted(BOOK_STYLE);
    private static final String ALL_STYLESHEETS = BOOK_STYLESHEET
            + "<link rel=\"stylesheet\" type=\"text/css\" href=\"../%s\"/>\n".formatted(FRONT_MATTER_STYLE);

    private final Template page = Template.load(Pages.class, "page.xhtml");
    private final String language;

    /** @param language the book's language, a BCP 47 tag such as {@code it-IT}, set on every page */
    Pages(String language) {

        this.language = language;
    }

    /**
     * The cover, filling the screen whatever its size, through an SVG that keeps the image's proportions, as
     * e-readers expect a cover page to be drawn.
     *
     * @param title the book's title, which describes the cover to readers who cannot see it
     */
    EpubPage cover(EpubImage cover, String title) {

        String content = """
                <section class="cover" epub:type="cover">
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" \
                width="100%%" height="100%%" viewBox="0 0 %d %d" preserveAspectRatio="xMidYMid meet" role="img" \
                aria-label="%s">
                <image width="%d" height="%d" xlink:href="../%s"/>
                </svg>
                </section>
                """.formatted(cover.width(), cover.height(), escape(title), cover.width(), cover.height(),
                cover.path());

        return page("cover", title, " class=\"cover-page\" epub:type=\"frontmatter\"", ALL_STYLESHEETS, content,
                "svg");
    }

    /** The title page: the title, the subtitle and the authors, as every other format draws them, without the cover. */
    EpubPage titlePage(TitlePage titlePage) {

        String content = XhtmlRenderer.titlePage(new TitlePage(Optional.empty(), titlePage.lines()),
                " epub:type=\"titlepage\"");

        return page("title-page", titlePage.title(), " epub:type=\"frontmatter\"", ALL_STYLESHEETS, content, "");
    }

    /** A chapter, styled by the book's stylesheet alone, as the author set it. */
    EpubPage chapter(ExportedChapter chapter, XhtmlRenderer renderer) {

        String content = "<section id=\"%s\" epub:type=\"chapter\" role=\"doc-chapter\">\n%s</section>\n"
                .formatted(chapter.id(), renderer.toXhtml(chapter.document()));

        return page(chapter.id(), chapter.title(), " epub:type=\"bodymatter\"", BOOK_STYLESHEET, content, "");
    }

    /** The copyright notice, on a page of its own at the back, so the store's sample opens on the book itself. */
    EpubPage copyright(String rights, String bookTitle) {

        String content = "<section class=\"copyright-page\" epub:type=\"copyright-page\">\n<p>%s</p>\n</section>\n"
                .formatted(escape(rights));

        return page("copyright", bookTitle, " epub:type=\"backmatter\"", ALL_STYLESHEETS, content, "");
    }

    /**
     * A page of the book's front matter, such as the contents, styled like the title page.
     *
     * @param properties the manifest properties it needs, such as {@code nav}
     */
    EpubPage frontMatter(String id, String title, String content, String properties) {

        return page(id, title, " epub:type=\"frontmatter\"", ALL_STYLESHEETS, content, properties);
    }

    private EpubPage page(String id, String title, String bodyAttributes, String stylesheets, String content,
                          String properties) {

        String xhtml = page.fill(Map.of(
                "language", escape(language),
                "title", escape(title),
                "stylesheets", stylesheets,
                "body-attributes", bodyAttributes,
                "content", content));

        return new EpubPage(id, "xhtml/" + fileName(id), title, xhtml, properties);
    }

    /** The file name of the page {@code id}, such as {@code chapter-3.xhtml}; every page sits in {@code xhtml/}. */
    static String fileName(String id) {

        return id + ".xhtml";
    }
}
