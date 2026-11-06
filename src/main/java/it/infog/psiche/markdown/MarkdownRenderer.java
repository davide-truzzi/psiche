package it.infog.psiche.markdown;

import it.infog.psiche.common.Template;

import org.commonmark.node.Block;
import org.commonmark.node.Document;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Renders chapter Markdown to a standalone HTML page for the preview pane. Each block element carries
 * {@code data-start} and {@code data-end} attributes with where it starts and ends in the Markdown, counted in
 * characters from the start of the text, so the preview can follow the editor's caret.
 * <p>
 * Each image of the book is asked for with its file's version, {@code ?v=} and the time it last changed, 0 while it is
 * not there: WebKit keeps the pictures it showed by their address, and would go on showing one renamed, deleted or
 * replaced on disk.
 */
public final class MarkdownRenderer {

    private static final String SOURCE = "src";
    private static final String MISSING = "0";

    private final Parser parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final Template page = Template.load(MarkdownRenderer.class, "preview.html");
    private final String stylesheet;

    public MarkdownRenderer(String stylesheet) {

        this.stylesheet = stylesheet;
    }

    /**
     * @param baseDirectory directory that relative links and images resolve against, so {@code ../media/map.png}
     *                      written in a chapter finds the book's media folder
     * @param fontSize      the size of the page's text in pixels, which the stylesheet's sizes in {@code em} follow
     */
    public String render(String markdown, Path baseDirectory, int fontSize) {

        return page.fill(Map.of(
                "base", baseDirectory.toUri().toString(),
                "style", stylesheet,
                "font-size", String.valueOf(fontSize),
                "article", renderArticle(markdown, baseDirectory)));
    }

    /**
     * The page's article alone, without the page around it, to put in place of the article of a page already shown
     * for {@code baseDirectory}, which keeps its stylesheet, fonts and scroll position.
     */
    public String renderArticle(String markdown, Path baseDirectory) {

        HtmlRenderer renderer = HtmlRenderer.builder()
                .softbreak("<br>") // Forces <br> for all soft breaks
                .attributeProviderFactory(context -> MarkdownRenderer::markSourceRange)
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

        try {
            return String.valueOf(Files.getLastModifiedTime(baseDirectory.resolve(destination)).toMillis());
        } catch (IOException | InvalidPathException e) {
            return MISSING;
        }
    }
}
