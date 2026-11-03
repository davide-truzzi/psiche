package it.infog.psiche.markdown;

import org.commonmark.node.Block;
import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.nio.file.Path;
import java.util.Map;

/**
 * Renders chapter Markdown to a standalone HTML page for the preview pane. Each block element carries
 * {@code data-start} and {@code data-end} attributes with where it starts and ends in the Markdown, counted in
 * characters from the start of the text, so the preview can follow the editor's caret.
 */
public final class MarkdownRenderer {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <base href="%s">
            <style>%s</style>
            <style>body { font-size: %dpx; }</style>
            </head>
            <body><article>
            %s</article></body>
            </html>
            """;

    private final Parser parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .softbreak("<br>") // Forces <br> for all soft breaks
            .attributeProviderFactory(context -> MarkdownRenderer::markSourceRange)
            .build();
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

        return PAGE.formatted(baseDirectory.toUri(), stylesheet, fontSize, renderArticle(markdown));
    }

    /**
     * The page's article alone, without the page around it, to put in place of the article of a page already shown,
     * which keeps its stylesheet, fonts and scroll position.
     */
    public String renderArticle(String markdown) {

        return renderer.render(parser.parse(markdown));
    }

    private static void markSourceRange(Node node, String tagName, Map<String, String> attributes) {

        if (!(node instanceof Block) || node instanceof Document || node.getSourceSpans().isEmpty())
            return;

        SourceSpan last = node.getSourceSpans().getLast();
        attributes.put("data-start", String.valueOf(node.getSourceSpans().getFirst().getInputIndex()));
        attributes.put("data-end", String.valueOf(last.getInputIndex() + last.getLength()));
    }
}
