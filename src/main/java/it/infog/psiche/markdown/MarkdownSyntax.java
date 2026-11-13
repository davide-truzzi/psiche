package it.infog.psiche.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * The Markdown a chapter is written in: CommonMark, with strikethrough between two tildes, {@code ~~gone~~}, and
 * subscript and superscript between one, {@code H~2~O} and {@code X^2^} (see {@link ScriptExtension}), footnotes
 * (see {@link Footnotes}) and GitHub's tables, a header row, a row of dashes whose colons align the columns, then the
 * body's rows, cells between pipes. Every part of the app that reads a chapter, the preview, the exports, the counts,
 * the spelling, the links, parses it here, so each reads the same text. A single tilde is always a subscript, never a
 * strikethrough.
 */
public final class MarkdownSyntax {

    private static final Extension FOOTNOTES = FootnotesExtension.create();
    // Drawn the same on every page; footnotes each page places its own way.
    private static final List<Extension> DRAWN_ALIKE = List.of(
            StrikethroughExtension.builder().requireTwoTildes(true).build(),
            new ScriptExtension(),
            TablesExtension.create());

    private MarkdownSyntax() {
    }

    /** A parser that keeps no positions in the text. */
    public static Parser parser() {

        return parser(IncludeSourceSpans.NONE);
    }

    /** A parser that keeps where each block, or inline too, lies in the text, as {@code sourceSpans} asks. */
    public static Parser parser(IncludeSourceSpans sourceSpans) {

        return Parser.builder().extensions(DRAWN_ALIKE).extensions(List.of(FOOTNOTES))
                .includeSourceSpans(sourceSpans).build();
    }

    /**
     * An HTML renderer that draws every element the parser makes but footnotes, to add the app's own renderers to:
     * a page without a renderer of its own for them leaves them out.
     */
    public static HtmlRenderer.Builder htmlRenderer() {

        return HtmlRenderer.builder().extensions(DRAWN_ALIKE);
    }

    /** An HTML renderer as {@link #htmlRenderer()}, with the footnotes numbered at the end of the page. */
    public static HtmlRenderer.Builder htmlRendererWithEndnotes() {

        return HtmlRenderer.builder().extensions(DRAWN_ALIKE).extensions(List.of(FOOTNOTES));
    }
}
