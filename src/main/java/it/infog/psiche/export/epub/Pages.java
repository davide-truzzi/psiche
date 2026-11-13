package it.infog.psiche.export.epub;

import it.infog.psiche.common.Template;
import it.infog.psiche.export.LinkTargets;
import it.infog.psiche.export.XhtmlRenderer;
import it.infog.psiche.export.epub.model.EpubImage;
import it.infog.psiche.export.epub.model.EpubPage;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.TitlePage;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static it.infog.psiche.export.XmlText.escape;

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

    // The line crediting the app on the copyright page, in the languages Psiche Writer knows; English for any other.
    private static final Map<String, String> CREDITS = Map.of(
            "en", "Made with Psiche Writer",
            "it", "Realizzato con Psiche Writer",
            "fr", "Réalisé avec Psiche Writer",
            "de", "Erstellt mit Psiche Writer",
            "es", "Hecho con Psiche Writer",
            "pt", "Feito com Psiche Writer",
            "nl", "Gemaakt met Psiche Writer");

    private final Template page = Template.load(Pages.class, "page.xhtml");
    // The cover's section, inside its page.
    private final Template coverSection = Template.load(Pages.class, "cover.xhtml");
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

        String content = coverSection.fill(Map.of(
                "width", String.valueOf(cover.width()),
                "height", String.valueOf(cover.height()),
                "title", escape(title),
                "path", cover.path()));

        return page("cover", title, " class=\"cover-page\" epub:type=\"frontmatter\"", ALL_STYLESHEETS, content,
                "svg");
    }

    /** The title page: the title, the subtitle and the authors, as every other format draws them, without the cover. */
    EpubPage titlePage(TitlePage titlePage) {

        String content = XhtmlRenderer.titlePage(new TitlePage(Optional.empty(), titlePage.lines()),
                " epub:type=\"titlepage\"");

        return page("title-page", titlePage.title(), " epub:type=\"frontmatter\"", ALL_STYLESHEETS, content, "");
    }

    /** A chapter, styled by the book's stylesheet alone, as the author set it, its links led by {@code links}. */
    EpubPage chapter(ExportedChapter chapter, XhtmlRenderer renderer, LinkTargets links) {

        String content = "<section id=\"%s\" epub:type=\"chapter\" role=\"doc-chapter\">\n%s</section>\n"
                .formatted(chapter.id(), renderer.toXhtml(chapter, links));

        // A chapter title ends with the mark, an SVG, which the manifest must declare.
        return page(chapter.id(), chapter.title(), " epub:type=\"bodymatter\"", BOOK_STYLESHEET, content,
                content.contains("<svg") ? "svg" : "");
    }

    /**
     * The copyright notice, if the book has one, then a small line saying the book was made with Psiche Writer, on a
     * page of its own at the back, so the store's sample opens on the book itself.
     *
     * @param rights the book's copyright notice; blank for none
     */
    EpubPage copyright(String rights, String bookTitle) {

        String notice = rights.isBlank() ? "" : "<p>%s</p>\n".formatted(escape(rights));
        String credit = CREDITS.getOrDefault(Locale.forLanguageTag(language).getLanguage(), CREDITS.get("en"));
        String content = "<section class=\"copyright-page\" epub:type=\"copyright-page\">\n%s%s</section>\n"
                .formatted(notice, "<p class=\"credit\">%s</p>\n".formatted(credit));

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
