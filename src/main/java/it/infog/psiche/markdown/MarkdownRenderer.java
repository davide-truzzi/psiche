package it.infog.psiche.markdown;

import org.commonmark.node.Block;
import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.nio.file.Path;
import java.util.Map;

/**
 * Renders chapter Markdown to a standalone HTML page for the preview pane. Each block element carries a
 * {@code data-line} attribute with the zero-based Markdown line it starts on, so the preview can follow the editor.
 */
public final class MarkdownRenderer {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <base href="%s">
            <style>%s</style>
            </head>
            <body><article>
            %s</article></body>
            </html>
            """;

    private final Parser parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .softbreak("<br>") // Forces <br> for all soft breaks
            .attributeProviderFactory(context -> MarkdownRenderer::markSourceLine)
            .build();
    private final String stylesheet;

    public MarkdownRenderer(String stylesheet) {

        this.stylesheet = stylesheet;
    }

    /**
     * @param baseDirectory directory that relative links and images resolve against, so {@code ../media/map.png}
     *                      written in a chapter finds the book's media folder
     */
    public String render(String markdown, Path baseDirectory) {

        String body = renderer.render(parser.parse(markdown));

        return PAGE.formatted(baseDirectory.toUri(), stylesheet, body);
    }

    private static void markSourceLine(Node node, String tagName, Map<String, String> attributes) {

        if (node instanceof Block && !(node instanceof Document) && !node.getSourceSpans().isEmpty())
            attributes.put("data-line", String.valueOf(node.getSourceSpans().getFirst().getLineIndex()));
    }
}
