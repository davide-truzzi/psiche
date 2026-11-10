package it.infog.psiche.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * The Markdown a chapter is written in: CommonMark, with strikethrough between two tildes, {@code ~~gone~~}, and
 * subscript and superscript between one, {@code H~2~O} and {@code X^2^} (see {@link ScriptExtension}). Every part of
 * the app that reads a chapter, the preview, the exports, the counts, the spelling, the links, parses it here, so
 * each reads the same text. A single tilde is always a subscript, never a strikethrough.
 */
public final class MarkdownSyntax {

    private static final List<Extension> EXTENSIONS = List.of(
            StrikethroughExtension.builder().requireTwoTildes(true).build(),
            new ScriptExtension());

    private MarkdownSyntax() {
    }

    /** A parser that keeps no positions in the text. */
    public static Parser parser() {

        return parser(IncludeSourceSpans.NONE);
    }

    /** A parser that keeps where each block, or inline too, lies in the text, as {@code sourceSpans} asks. */
    public static Parser parser(IncludeSourceSpans sourceSpans) {

        return Parser.builder().extensions(EXTENSIONS).includeSourceSpans(sourceSpans).build();
    }

    /** An HTML renderer that knows every element the parser makes, to add the app's own renderers to. */
    public static HtmlRenderer.Builder htmlRenderer() {

        return HtmlRenderer.builder().extensions(EXTENSIONS);
    }
}
