package it.infog.psiche.markdown;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

/**
 * The text of a part of parsed Markdown as a reader sees it, such as a heading's or an image's caption: its escapes
 * undone, emphasis and code kept as their words, the marks around them left out.
 */
public final class PlainText {

    private PlainText() {
    }

    /** The plain text inside {@code node}. */
    public static String of(Node node) {

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
}
