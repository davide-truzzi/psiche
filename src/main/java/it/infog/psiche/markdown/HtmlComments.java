package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextRange;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML comments, such as {@code <!-- a note -->}, which authors use for notes to themselves in a chapter. The preview
 * hides them as a browser does, so the PDF and ODT exports leave them out too, and the editor shows them in grey; the
 * Markdown export keeps them. A comment is one only in the HTML a chapter holds: inside a code span or a code block it
 * is text, as the preview shows it.
 */
public final class HtmlComments {

    // A comment left open runs to the end, as it does in a browser.
    private static final Pattern COMMENT = Pattern.compile("<!--.*?(?:-->|\\z)", Pattern.DOTALL);
    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private HtmlComments() {
    }

    /** {@code html} without its comments; blank if it held nothing else. */
    public static String strip(String html) {

        return COMMENT.matcher(html).replaceAll("");
    }

    /** Where the comments are in {@code markdown}, in order: the ones the preview and the exports leave out. */
    public static List<TextRange> ranges(String markdown) {

        List<TextRange> ranges = new ArrayList<>();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(HtmlBlock htmlBlock) {
                addComments(markdown, htmlBlock, ranges);
            }

            @Override
            public void visit(HtmlInline htmlInline) {
                addComments(markdown, htmlInline, ranges);
            }
        });

        return ranges;
    }

    /** Adds the comments in the HTML {@code node} to {@code ranges}; one left open ends with the node. */
    private static void addComments(String markdown, Node node, List<TextRange> ranges) {

        List<SourceSpan> spans = node.getSourceSpans();
        if (spans.isEmpty())
            return;

        SourceSpan last = spans.getLast();
        Matcher comment = COMMENT.matcher(markdown)
                .region(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
        while (comment.find())
            ranges.add(new TextRange(comment.start(), comment.end()));
    }
}
