package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.Script;
import it.infog.psiche.markdown.model.Subscript;
import it.infog.psiche.markdown.model.Superscript;

import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.Nodes;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpans;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Subscript, {@code H~2~O}, and superscript, {@code X^2^}, as Pandoc and the Markdown Guide write them: one sign on
 * each side and no space or line break inside, so {@code ~ 30 people} or {@code a ^ b} stay as they are written.
 * CommonMark has neither; the parser finds them as it finds emphasis, and HTML draws them as {@code <sub>} and
 * {@code <sup>}. Two tildes are a strikethrough, which the strikethrough extension takes.
 */
final class ScriptExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    @Override
    public void extend(Parser.Builder parser) {

        parser.customDelimiterProcessor(new ScriptDelimiter(Subscript.SIGN, Subscript::new))
                .customDelimiterProcessor(new ScriptDelimiter(Superscript.SIGN, Superscript::new));
    }

    @Override
    public void extend(HtmlRenderer.Builder renderer) {

        renderer.nodeRendererFactory(ScriptRenderer::new);
    }

    /** True if {@code node}, or anything inside it, is or has a space or a line break. */
    private static boolean hasSpace(Node node) {

        return switch (node) {
            case SoftLineBreak _, HardLineBreak _ -> true;
            case Text text when text.getLiteral().chars().anyMatch(Character::isWhitespace) -> true;
            default -> childHasSpace(node);
        };
    }

    private static boolean childHasSpace(Node node) {


        for (Node child = node.getFirstChild(); child != null; child = child.getNext())
            if (hasSpace(child))
                return true;
        return false;
    }

    /** Takes one {@code sign} on each side of text with no space in it, and puts the text in a {@code script}. */
    private record ScriptDelimiter(char sign,
                                   Supplier<Script> script) implements DelimiterProcessor {

        @Override
        public char getOpeningCharacter() {

            return sign;
        }

        @Override
        public char getClosingCharacter() {

            return sign;
        }

        @Override
        public int getMinLength() {

            return 1;
        }

        @Override
        public int process(DelimiterRun opening, DelimiterRun closing) {

            if (opening.length() != 1 || closing.length() != 1)
                return 0;

            Text opener = opening.getOpener();
            List<Node> inside = new ArrayList<>();
            Nodes.between(opener, closing.getCloser()).forEach(inside::add);
            if (inside.isEmpty() || inside.stream().anyMatch(ScriptExtension::hasSpace))
                return 0;

            //the script, with its signs and text as its place in the source
            Script node = script.get();
            SourceSpans spans = new SourceSpans();
            spans.addAllFrom(opening.getOpeners(1));
            for (Node each : inside) {
                node.appendChild(each);
                spans.addAll(each.getSourceSpans());
            }
            spans.addAllFrom(closing.getClosers(1));
            node.setSourceSpans(spans.getSourceSpans());
            opener.insertAfter(node);

            return 1;
        }
    }

    /** Draws a subscript as {@code <sub>} and a superscript as {@code <sup>}. */
    private record ScriptRenderer(HtmlNodeRendererContext context) implements NodeRenderer {

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {

            return Set.of(Subscript.class, Superscript.class);
        }

        @Override
        public void render(Node node) {

            String tag = switch ((Script) node) {
                case Subscript _ -> "sub";
                case Superscript _ -> "sup";
            };

            HtmlWriter html = context.getWriter();
            html.tag(tag, context.extendAttributes(node, tag, Map.of()));
            HtmlChildren.render(context, node);
            html.tag("/" + tag);
        }
    }
}
