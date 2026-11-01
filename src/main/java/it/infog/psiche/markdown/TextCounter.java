package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextCount;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Counts a chapter's characters and words as a reader sees them: Markdown markers, link addresses, images and HTML
 * do not count, and a line break inside a paragraph counts as a space, as it would in a typeset paragraph.
 */
public final class TextCounter {

    private static final Parser PARSER = Parser.builder().build();
    // A word has at least one letter or digit, so a dash or an ellipsis between spaces is not one.
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

    private TextCounter() {
    }

    public static TextCount count(String markdown) {

        String text = readersText(markdown);
        int characters = (int) text.codePoints().filter(c -> c != '\n').count();
        int words = (int) Arrays.stream(text.split("\\s+")).filter(word -> WORD.matcher(word).find()).count();

        return new TextCount(characters, words);
    }

    /** The text a reader sees, one block per line; the line ends between blocks are not part of it. */
    static String readersText(String markdown) {

        StringBuilder text = new StringBuilder();
        PARSER.parse(markdown).accept(new AbstractVisitor() {

            @Override
            public void visit(Text node) {
                text.append(node.getLiteral());
            }

            @Override
            public void visit(Code node) {
                text.append(node.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak node) {
                text.append(' ');
            }

            @Override
            public void visit(HardLineBreak node) {
                text.append(' ');
            }

            @Override
            public void visit(Paragraph node) {
                block(node);
            }

            @Override
            public void visit(Heading node) {
                block(node);
            }

            @Override
            public void visit(FencedCodeBlock node) {
                text.append(node.getLiteral()).append('\n');
            }

            @Override
            public void visit(IndentedCodeBlock node) {
                text.append(node.getLiteral()).append('\n');
            }

            // An image's description, raw HTML and comments are not read as part of the text.
            @Override
            public void visit(Image node) {
            }

            @Override
            public void visit(HtmlInline node) {
            }

            @Override
            public void visit(HtmlBlock node) {
            }

            private void block(Block node) {
                visitChildren(node);
                text.append('\n');
            }
        });

        return text.toString();
    }
}
