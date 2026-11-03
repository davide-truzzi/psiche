package it.infog.psiche.export.epub;

import it.infog.psiche.common.Template;
import it.infog.psiche.export.XhtmlRenderer;
import it.infog.psiche.export.epub.model.EpubPage;
import it.infog.psiche.export.model.Contents;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.infog.psiche.export.XmlText.escape;

/**
 * How readers find their way through the EPUB:
 * <ul>
 *     <li>the navigation document, {@code nav.xhtml}, which EPUB 3 requires. It is also the book's contents page, in
 *     the reading order after the title page, as stores want one near the beginning: the book's {@link Contents}, a
 *     link per chapter, without page numbers. Its landmarks, hidden, tell e-readers where the cover, the contents and
 *     the text start;</li>
 *     <li>{@code toc.ncx}, the same contents for e-readers that only read EPUB 2, which Amazon still asks for.</li>
 * </ul>
 */
final class Navigation {

    static final String NCX_PATH = "toc.ncx";

    // The contents page is the navigation document, as EPUB 3 names it.
    private static final String NAV_ATTRIBUTES =
            " epub:type=\"toc\" role=\"doc-toc\" id=\"toc\" aria-labelledby=\"toc-heading\"";

    private final Template ncx = Template.load(Navigation.class, "toc.ncx");

    /**
     * The contents page, with the landmarks.
     *
     * @param contents  the book's contents; there must be a chapter in it
     * @param cover     the cover's page, if the book has one in the EPUB
     * @param titlePage the title page
     */
    EpubPage contents(Pages pages, Contents contents, Optional<EpubPage> cover, EpubPage titlePage) {

        StringBuilder content = new StringBuilder(XhtmlRenderer.contents(contents, Pages::fileName, NAV_ATTRIBUTES));

        // Landmarks: where the cover, the title page, the contents and the text start.
        content.append("<nav class=\"landmarks\" epub:type=\"landmarks\" hidden=\"hidden\">\n<ol>\n");
        cover.ifPresent(page -> content.append(landmark("cover", Pages.fileName(page.id()), "Cover")));
        content.append(landmark("titlepage", Pages.fileName(titlePage.id()), "Title page"));
        content.append("<li><a epub:type=\"toc\" href=\"nav.xhtml#toc\">%s</a></li>\n"
                .formatted(escape(contents.heading())));
        content.append(landmark("bodymatter", Pages.fileName(contents.entries().getFirst().id()), "Start"));
        content.append("</ol>\n</nav>\n");

        return pages.frontMatter("nav", contents.heading(), content.toString(), "nav");
    }

    /** {@code toc.ncx}: the same contents, for EPUB 2 e-readers. */
    String ncx(Contents contents, String identifier, String title, String language) {

        StringBuilder navPoints = new StringBuilder();
        List<Contents.Entry> entries = contents.entries();
        for (int i = 0; i < entries.size(); i++) {
            Contents.Entry entry = entries.get(i);
            navPoints.append("""
                            <navPoint id="nav-%d" playOrder="%d">
                                <navLabel><text>%s</text></navLabel>
                                <content src="xhtml/%s"/>
                            </navPoint>
                    """.formatted(i + 1, i + 1, escape(entry.title()), Pages.fileName(entry.id())));
        }

        return ncx.fill(Map.of(
                "language", escape(language),
                "identifier", escape(identifier),
                "title", escape(title),
                "nav-points", navPoints.toString()));
    }

    private static String landmark(String type, String href, String label) {

        return "<li><a epub:type=\"%s\" href=\"%s\">%s</a></li>\n".formatted(type, href, label);
    }
}
