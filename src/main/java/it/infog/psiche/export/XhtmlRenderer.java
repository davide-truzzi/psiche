package it.infog.psiche.export;

import it.infog.psiche.export.model.Contents;
import it.infog.psiche.export.model.TitlePage;
import it.infog.psiche.markdown.HtmlComments;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Renders parsed Markdown as XHTML for the exports that lay out web pages, the PDF and the EPUB. Both render a
 * chapter as the author wrote it: raw HTML is printed as text but HTML comments are left out, a line break inside a
 * paragraph is kept, and links lead only out of the book. Both close each chapter title, a level-1 heading, with the
 * Psiche mark, a Ψ hidden from screen readers, inside the heading so that the stylesheets' rules for the paragraph
 * after a heading still apply. They differ in two things:
 * <ul>
 *     <li>the manuscript, for the PDF, prints a scene break as a centred {@code * * *} and shows images where the
 *     chapter links them;</li>
 *     <li>the ebook keeps a scene break a real {@code <hr />}, which the book's stylesheet draws and screen readers
 *     announce, and links each image to its copy in the ebook, through {@link ImageLinks}.</li>
 * </ul>
 */
public final class XhtmlRenderer {

    private static final String SCENE_BREAK = "* * *";
    // The manuscript's stylesheet styles the mark.
    private static final XhtmlRenderer MANUSCRIPT =
            new XhtmlRenderer(null, "<span class=\"title-mark\" aria-hidden=\"true\">Ψ</span>");
    // Styled here rather than in the book's stylesheet, which the author may have written before the mark existed.
    private static final String EBOOK_TITLE_MARK = "<span class=\"title-mark\" aria-hidden=\"true\" "
            + "style=\"display: block; margin-top: 0.3em; font-size: 0.9em; line-height: 1; color: #b8361f;\">Ψ</span>";

    private final HtmlRenderer renderer;

    /**
     * @param images    where the ebook's images are; null for the manuscript
     * @param titleMark the XHTML that ends each chapter title
     */
    private XhtmlRenderer(ImageLinks images, String titleMark) {

        // Raw HTML is escaped, since the PDF renderer and e-readers only read well-formed XHTML.
        HtmlRenderer.Builder builder = HtmlRenderer.builder()
                .escapeHtml(true)
                .softbreak("<br />")
                .nodeRendererFactory(LocalLinkRenderer::new)
                .nodeRendererFactory(context -> new RawHtmlRenderer(context.getWriter()))
                .nodeRendererFactory(context -> new TitleMarkRenderer(context, titleMark));

        if (images == null)
            builder.nodeRendererFactory(context -> new SceneBreakRenderer(context.getWriter()));
        else
            builder.nodeRendererFactory(context -> new ImageRenderer(context.getWriter(), images));

        renderer = builder.build();
    }

    /** {@code document} as an XHTML fragment for a manuscript page, the body of the PDF. */
    public static String render(Node document) {

        return MANUSCRIPT.toXhtml(document);
    }

    /** A renderer for ebook pages, which finds the chapters' images through {@code images}. */
    public static XhtmlRenderer ebook(ImageLinks images) {

        return new XhtmlRenderer(images, EBOOK_TITLE_MARK);
    }

    /** {@code document} as an XHTML fragment, for the body of a page. */
    public String toXhtml(Node document) {

        return renderer.render(document);
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
                .formatted(escape(cover.toUri().toString()), escape(page.title()))));

        for (TitlePage.Line line : page.lines()) {
            String style = switch (line.role()) {
                case TITLE -> "book-title";
                case SUBTITLE -> "book-subtitle";
                case AUTHORS -> "book-authors";
            };
            xhtml.append("<p class=\"%s\">%s</p>\n".formatted(style, escape(line.text())));
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
                .formatted(attributes, escape(contents.heading())));
        for (Contents.Entry entry : contents.entries())
            xhtml.append("<li><a href=\"%s\">%s</a></li>\n"
                    .formatted(escape(linkTo.apply(entry.id())), escape(entry.title())));

        return xhtml.append("</ol>\n</nav>\n").toString();
    }

    /** {@code text} escaped for an XHTML element or a double-quoted attribute. */
    public static String escape(String text) {

        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The plain text inside {@code node}, emphasis and code included, as a reader sees it. */
    public static String plainText(Node node) {

        StringBuilder text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text literal) {
                text.append(literal.getLiteral());
            }

            @Override
            public void visit(Code code) {
                text.append(code.getLiteral());
            }
        });

        return text.toString();
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
                attributes.put("alt", plainText(image));
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
            for (Node child = heading.getFirstChild(); child != null; child = child.getNext())
                context.render(child);
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
     * Keeps web and mail links. A link to one of the book's own files, such as another chapter, would lead nowhere
     * once the text is out of the book, so only its text is rendered.
     */
    private record LocalLinkRenderer(HtmlNodeRendererContext context) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Link.class);
        }

        @Override
        public void render(Node node) {

            Link link = (Link) node;
            HtmlWriter html = context.getWriter();
            boolean external = link.getDestination().matches("(?i)(https?|mailto):.*");

            if (external)
                html.tag("a", Map.of("href", context.encodeUrl(link.getDestination())));
            Node child = link.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                context.render(child);
                child = next;
            }
            if (external)
                html.tag("/a");
        }
    }
}
