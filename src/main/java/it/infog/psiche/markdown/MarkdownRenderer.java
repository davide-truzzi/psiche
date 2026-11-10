package it.infog.psiche.markdown;

import it.infog.psiche.common.Template;

import org.commonmark.node.Block;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Renders chapter Markdown to a standalone HTML page for the preview pane. Each block element carries
 * {@code data-start} and {@code data-end} attributes with where it starts and ends in the Markdown, counted in
 * characters from the start of the text, so the preview can follow the editor's caret. An image on a paragraph of its
 * own is a figure, with its caption under it, as the exports print it; see {@link FigureRenderer}. Each heading has
 * its anchor as its id, so a link to a heading of the same chapter leads to it, and a link that leads nowhere is its
 * text alone, in orange.
 * <p>
 * Each image of the book is asked for with its file's version, {@code ?v=} and the time it last changed, 0 while it is
 * not there: WebKit keeps the pictures it showed by their address, and would go on showing one renamed, deleted or
 * replaced on disk.
 */
public final class MarkdownRenderer {

    private static final String SOURCE = "src";
    private static final String MISSING = "0";

    private final Parser parser = MarkdownSyntax.parser(IncludeSourceSpans.BLOCKS);
    private final Template page = Template.load(MarkdownRenderer.class, "preview.html");
    private final String stylesheet;

    public MarkdownRenderer(String stylesheet) {

        this.stylesheet = stylesheet;
    }

    /**
     * @param baseDirectory directory that relative links and images resolve against, so {@code ../media/map.png}
     *                      written in a chapter finds the book's media folder
     * @param fontSize      the size of the page's text in pixels, which the stylesheet's sizes in {@code em} follow
     * @param broken        true for the destination of a link that leads nowhere
     */
    public String render(String markdown, Path baseDirectory, int fontSize, Predicate<String> broken) {

        return page.fill(Map.of(
                "base", baseDirectory.toUri().toString(),
                "style", stylesheet,
                "font-size", String.valueOf(fontSize),
                "article", renderArticle(markdown, baseDirectory, broken)));
    }

    /**
     * The page's article alone, without the page around it, to put in place of the article of a page already shown
     * for {@code baseDirectory}, which keeps its stylesheet, fonts and scroll position.
     *
     * @param broken true for the destination of a link that leads nowhere
     */
    public String renderArticle(String markdown, Path baseDirectory, Predicate<String> broken) {

        Function<Heading, String> anchors = HeadingAnchors.namer();
        HtmlRenderer renderer = MarkdownSyntax.htmlRenderer()
                .softbreak("<br>") // Forces <br> for all soft breaks
                .nodeRendererFactory(context -> new FigureRenderer(context, image -> true))
                .nodeRendererFactory(context -> new LinkRenderer(context, broken))
                .attributeProviderFactory(context -> MarkdownRenderer::markSourceRange)
                .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                    if (node instanceof Heading heading)
                        attributes.put("id", anchors.apply(heading));
                })
                .attributeProviderFactory(context -> (node, tagName, attributes) ->
                        markVersion(baseDirectory, node, attributes))
                .build();

        return renderer.render(parser.parse(markdown));
    }

    private static void markSourceRange(Node node, String tagName, Map<String, String> attributes) {

        if (!(node instanceof Block) || node instanceof Document || node.getSourceSpans().isEmpty())
            return;

        SourceSpan last = node.getSourceSpans().getLast();
        attributes.put("data-start", String.valueOf(node.getSourceSpans().getFirst().getInputIndex()));
        attributes.put("data-end", String.valueOf(last.getInputIndex() + last.getLength()));
    }

    /** Asks for an image of the book with its file's version, so WebKit shows the file as it is now. */
    private static void markVersion(Path baseDirectory, Node node, Map<String, String> attributes) {

        if (!(node instanceof Image image) || ImageLines.isWebAddress(image.getDestination())
                || !attributes.containsKey(SOURCE))
            return;

        attributes.put(SOURCE, attributes.get(SOURCE) + "?v=" + version(baseDirectory, image.getDestination()));
    }

    /** When the file at {@code destination} last changed, in milliseconds; {@value #MISSING} if it is not there. */
    private static String version(Path baseDirectory, String destination) {

        Optional<Path> file = ImageLines.file(baseDirectory, destination);
        try {
            return file.isPresent() ? String.valueOf(Files.getLastModifiedTime(file.get()).toMillis()) : MISSING;
        } catch (IOException e) {
            return MISSING;
        }
    }

    /** Renders a link as a link, with its title, or one that leads nowhere as its text alone, marked as broken. */
    private record LinkRenderer(HtmlNodeRendererContext context, Predicate<String> broken) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Link.class);
        }

        @Override
        public void render(Node node) {

            Link link = (Link) node;
            HtmlWriter html = context.getWriter();
            boolean leadsNowhere = broken.test(link.getDestination());

            Map<String, String> attributes = new LinkedHashMap<>();
            if (leadsNowhere)
                attributes.put("class", "broken-link");
            else
                attributes.put("href", context.encodeUrl(link.getDestination()));
            if (link.getTitle() != null)
                attributes.put("title", link.getTitle());
            html.tag(leadsNowhere ? "span" : "a", attributes);
            HtmlChildren.render(context, link);
            html.tag(leadsNowhere ? "/span" : "/a");
        }
    }
}
