package it.infog.psiche.export;

import it.infog.psiche.export.model.Contents;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.LinkPlace;
import it.infog.psiche.export.model.TitlePage;
import it.infog.psiche.markdown.FigureRenderer;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.HtmlChildren;
import it.infog.psiche.markdown.HtmlComments;
import it.infog.psiche.markdown.MarkdownSyntax;
import it.infog.psiche.markdown.PlainText;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.ThematicBreak;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Renders parsed Markdown as XHTML for the exports that lay out web pages, the PDF and the EPUB. Both render a
 * chapter as the author wrote it: raw HTML is printed as text but HTML comments are left out, a line break inside a
 * paragraph is kept, and an image on a paragraph of its own is a figure with its caption under it
 * ({@link FigureRenderer}). Each heading has its id from {@link LinkTargets#headingId}; web and mail links stay links,
 * a link to a chapter or a heading of the export leads there, and any other link is its text alone. Both close each
 * chapter title, a level-1 heading, with the Psiche mark ({@link TitleMark}), hidden from screen readers, inside the
 * heading so that the stylesheets' rules for the paragraph after a heading still apply. They differ in two things:
 * <ul>
 *     <li>the manuscript, for the PDF, prints a scene break as a centred {@code * * *}, shows images where the
 *     chapter links them, links a place of the book within its one document, and prints the footnotes at the foot
 *     of the page ({@link PageFootnotes});</li>
 *     <li>the ebook keeps a scene break a real {@code <hr />}, which the book's stylesheet draws and screen readers
 *     announce, links each image to its copy in the ebook, through {@link ImageLinks}, a place of the book on the
 *     page of its chapter, puts the footnotes at the chapter's end, as e-readers' pop-ups ({@link EbookFootnotes}),
 *     and names a table column's alignment as a class, for the chapter's stylesheet.</li>
 * </ul>
 */
public final class XhtmlRenderer {

    private static final String SCENE_BREAK = "* * *";
    // The manuscript's stylesheet sizes the mark.
    private static final XhtmlRenderer MANUSCRIPT = new XhtmlRenderer(null, TitleMark.manuscript(),
            place -> "#" + place.elementId());
    // An image and its caption, centred with room around them, styled here rather than in the book's stylesheet,
    // which the author may have written without them.
    private static final String ALIGN = "align";
    private static final Map<String, String> EBOOK_FIGURE_STYLES = Map.of(
            "figure", "margin: 1.5em 0; text-align: center; text-indent: 0;",
            "figcaption", "margin-top: 0.5em; font-size: 0.9em; font-style: italic; text-align: center; text-indent: 0;");

    private final ImageLinks images;
    private final String titleMark;
    private final Function<LinkPlace, String> hrefs;

    /**
     * @param images    where the ebook's images are; null for the manuscript
     * @param titleMark the XHTML that ends each chapter title
     * @param hrefs     the address of a place of the book, from a page of a chapter
     */
    private XhtmlRenderer(ImageLinks images, String titleMark, Function<LinkPlace, String> hrefs) {

        this.images = images;
        this.titleMark = titleMark;
        this.hrefs = hrefs;
    }

    /** {@code chapter} as an XHTML fragment for a manuscript page, the PDF's body, its links led by {@code links}. */
    public static String render(ExportedChapter chapter, LinkTargets links) {

        return MANUSCRIPT.toXhtml(chapter, links);
    }

    /**
     * A renderer for ebook pages, which finds the chapters' images through {@code images}, and writes the address of a
     * place of the book, from a chapter's page, with {@code hrefs}.
     */
    public static XhtmlRenderer ebook(ImageLinks images, Function<LinkPlace, String> hrefs) {

        return new XhtmlRenderer(images, TitleMark.ebook(), hrefs);
    }

    /** {@code chapter} as an XHTML fragment, for the body of a page, its links led by {@code links}. */
    public String toXhtml(ExportedChapter chapter, LinkTargets links) {

        // Raw HTML is escaped, since the PDF renderer and e-readers only read well-formed XHTML.
        Function<Heading, String> anchors = HeadingAnchors.namer();
        HtmlRenderer.Builder builder = MarkdownSyntax.htmlRenderer()
                .escapeHtml(true)
                .softbreak("<br />")
                .nodeRendererFactory(context -> new LinkRenderer(context, chapter, links, hrefs))
                .nodeRendererFactory(context -> new RawHtmlRenderer(context.getWriter()))
                .nodeRendererFactory(context -> new TitleMarkRenderer(context, titleMark))
                .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                    if (node instanceof Heading heading)
                        attributes.put("id", LinkTargets.headingId(chapter.id(), anchors.apply(heading)));
                });

        if (images == null)
            builder.nodeRendererFactory(PageFootnotes::new)
                    .nodeRendererFactory(context -> new SceneBreakRenderer(context.getWriter()))
                    .nodeRendererFactory(context -> new FigureRenderer(context, image -> true));
        else
            builder.nodeRendererFactory(context -> new EbookFootnotes(context, chapter.id()))
                    .nodeRendererFactory(context -> new ImageRenderer(context.getWriter(), images))
                    .nodeRendererFactory(context -> new FigureRenderer(context,
                            image -> images.href(image.getDestination()).isPresent()))
                    .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                        if (EBOOK_FIGURE_STYLES.containsKey(tagName))
                            attributes.put("style", EBOOK_FIGURE_STYLES.get(tagName));
                    })
                    .attributeProviderFactory(context -> (node, tagName, attributes) -> alignAsClass(attributes));

        return builder.build().render(chapter.document());
    }

    /**
     * The book's title page as an XHTML section: the cover, if there is one, then a paragraph per line, its class
     * naming what the line is, for the stylesheet to style.
     */
    public static String titlePage(TitlePage page) {

        return titlePage(page, "");
    }

    /**
     * The book's title page, as {@link #titlePage(TitlePage)} draws it.
     *
     * @param attributes more attributes for the {@code <section>}, starting with a space, such as
     *                   {@code  epub:type="titlepage"}
     */
    public static String titlePage(TitlePage page, String attributes) {

        StringBuilder xhtml = new StringBuilder("<section class=\"title-page\"%s>\n".formatted(attributes));

        page.cover().ifPresent(cover -> xhtml.append("<div class=\"cover\"><img src=\"%s\" alt=\"%s\" /></div>\n"
                .formatted(XmlText.escape(cover.toUri().toString()), XmlText.escape(page.title()))));

        for (TitlePage.Line line : page.lines()) {
            String style = switch (line.role()) {
                case TITLE -> "book-title";
                case SUBTITLE -> "book-subtitle";
                case AUTHORS -> "book-authors";
            };
            xhtml.append("<p class=\"%s\">%s</p>\n".formatted(style, XmlText.escape(line.text())));
        }

        return xhtml.append("</section>\n").toString();
    }

    /**
     * The book's table of contents as an XHTML {@code <nav>}: its heading, then a list with a link per chapter. The
     * page numbers a printed book shows come from the stylesheet, not from here.
     *
     * @param linkTo     the address of a chapter from its id, such as {@code #chapter-3} or {@code chapter-3.xhtml}
     * @param attributes more attributes for the {@code <nav>}, starting with a space, such as {@code  id="toc"}
     */
    public static String contents(Contents contents, UnaryOperator<String> linkTo, String attributes) {

        StringBuilder xhtml = new StringBuilder("<nav class=\"toc\"%s>\n<h1 id=\"toc-heading\">%s</h1>\n<ol>\n"
                .formatted(attributes, XmlText.escape(contents.heading())));
        for (Contents.Entry entry : contents.entries())
            xhtml.append("<li><a href=\"%s\">%s</a></li>\n"
                    .formatted(XmlText.escape(linkTo.apply(entry.id())), XmlText.escape(entry.title())));

        return xhtml.append("</ol>\n</nav>\n").toString();
    }

    /**
     * A table column's alignment, which commonmark writes as an {@code align} attribute, obsolete in EPUB 3's XHTML,
     * as a class the chapter's stylesheet aligns it by instead: {@code align-left}, {@code align-center} or
     * {@code align-right}.
     */
    private static void alignAsClass(Map<String, String> attributes) {

        String align = attributes.remove(ALIGN);
        if (align != null)
            attributes.put("class", ALIGN + "-" + align);
    }

    /**
     * An ebook's image, linked to its copy in the ebook with its description as alt text, for readers who cannot
     * see it. An image the ebook could not take is left out.
     */
    private record ImageRenderer(HtmlWriter html, ImageLinks images) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {

            Image image = (Image) node;
            images.href(image.getDestination()).ifPresent(href -> {
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("src", href);
                attributes.put("alt", PlainText.of(image));
                html.tag("img", attributes, true);
            });
        }
    }

    /** Headings as commonmark writes them, a chapter title, a level-1 heading, ending with {@code mark}. */
    private record TitleMarkRenderer(HtmlNodeRendererContext context, String mark) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Heading.class);
        }

        @Override
        public void render(Node node) {

            Heading heading = (Heading) node;
            String tag = "h" + heading.getLevel();
            HtmlWriter html = context.getWriter();

            html.line();
            html.tag(tag, context.extendAttributes(heading, tag, Map.of()));
            HtmlChildren.render(context, heading);
            if (heading.getLevel() == 1)
                html.raw(mark);
            html.tag("/" + tag);
            html.line();
        }
    }

    /** A scene break as a centred row of asterisks, as manuscripts mark one, instead of a rule across the page. */
    private record SceneBreakRenderer(HtmlWriter html) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(ThematicBreak.class);
        }

        @Override
        public void render(Node node) {

            html.line();
            html.tag("p", Map.of("class", "scene-break"));
            html.text(SCENE_BREAK);
            html.tag("/p");
            html.line();
        }
    }

    /**
     * Raw HTML printed as the text the author typed, a block of it in a paragraph of its own. HTML comments are the
     * author's notes, hidden in the preview, so they are left out, and a block holding only comments with them.
     */
    private record RawHtmlRenderer(HtmlWriter html) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(HtmlBlock.class, HtmlInline.class);
        }

        @Override
        public void render(Node node) {

            if (node instanceof HtmlInline inline) {
                html.text(HtmlComments.strip(inline.getLiteral()));
                return;
            }

            String text = HtmlComments.strip(((HtmlBlock) node).getLiteral()).strip();
            if (text.isEmpty())
                return;

            html.line();
            html.tag("p");
            html.text(text);
            html.tag("/p");
            html.line();
        }
    }

    /**
     * Keeps web and mail links, and a link to a place of the export, led there by {@code links}; any other link, as
     * one to a chapter left out, would lead nowhere, so only its text is rendered.
     */
    private record LinkRenderer(HtmlNodeRendererContext context, ExportedChapter chapter, LinkTargets links,
                                Function<LinkPlace, String> hrefs) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Link.class);
        }

        @Override
        public void render(Node node) {

            Link link = (Link) node;
            HtmlWriter html = context.getWriter();
            String destination = link.getDestination();
            Optional<String> href = LinkTargets.isWeb(destination)
                    ? Optional.of(context.encodeUrl(destination))
                    : links.place(chapter, destination).map(hrefs);

            href.ifPresent(address -> html.tag("a", Map.of("href", address)));
            HtmlChildren.render(context, link);
            if (href.isPresent())
                html.tag("/a");
        }
    }
}
