package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Image;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The images of a chapter, each a paragraph of its own, {@code ![caption](../media/<chapter>/<file>)}: finding them
 * in the text, and writing a new one in with a blank line before and after it.
 */
public final class ImageLines {

    // Characters a caption escapes, so they stay text: the brackets that would end it, and the backslash itself.
    private static final Pattern CAPTION_SPECIAL = Pattern.compile("[\\\\\\[\\]]");
    // Characters that make a destination need angle brackets around it.
    private static final Pattern DESTINATION_SPECIAL = Pattern.compile("[\\s()]");
    private static final String PARAGRAPH_BREAK = "\n\n";

    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

    private ImageLines() {
    }

    /** The paragraphs of {@code text} that are an image alone, in order. */
    public static List<ImageLine> of(String text) {

        List<ImageLine> lines = new ArrayList<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Paragraph paragraph) {

                if (paragraph.getFirstChild() instanceof Image image && image.getNext() == null
                        && !paragraph.getSourceSpans().isEmpty())
                    lines.add(new ImageLine(range(paragraph), caption(image), image.getDestination()));
            }
        });

        return lines;
    }

    /** Where the image {@code fileName} of {@code chapter} is, written from a chapter's file in {@code chapters/}. */
    public static String destination(String chapter, String fileName) {

        return "../media/" + chapter + "/" + fileName;
    }

    /** The Markdown of an image with {@code caption}, found at {@code destination}. */
    public static String markdown(String caption, String destination) {

        String escaped = CAPTION_SPECIAL.matcher(caption.strip())
                .replaceAll(match -> Matcher.quoteReplacement("\\" + match.group()));
        String where = DESTINATION_SPECIAL.matcher(destination).find() ? "<" + destination + ">" : destination;

        return "![" + escaped + "](" + where + ")";
    }

    /**
     * Writes {@code image}, an image's Markdown, into {@code text} at {@code caret} as a paragraph of its own: a
     * blank line before and after it, unless the text has them there already, none before at the very start, and a
     * line end after it at the very end. A caret inside a line puts the image after that line, so no sentence is cut.
     * The caret ends after the image.
     */
    public static TextEdit insert(String text, int caret, String image) {

        int at = lineBoundary(text, caret);
        String before = text.substring(0, at);
        String after = text.substring(at);

        String opening = before.isEmpty() ? "" : PARAGRAPH_BREAK.substring(trailingLineEnds(before));
        String closing = after.isEmpty() ? "\n" : PARAGRAPH_BREAK.substring(leadingLineEnds(after));
        String replacement = opening + image + closing;

        int end = at + opening.length() + image.length();
        return new TextEdit(at, at, replacement, end, end);
    }

    /** {@code caret} if it is at the start of a line, otherwise the end of its line. */
    private static int lineBoundary(String text, int caret) {

        if (caret == 0 || text.charAt(caret - 1) == '\n')
            return caret;

        int lineEnd = text.indexOf('\n', caret);
        return lineEnd < 0 ? text.length() : lineEnd;
    }

    /** The line ends, up to two, that {@code text} ends with. */
    private static int trailingLineEnds(String text) {

        int count = 0;
        while (count < PARAGRAPH_BREAK.length() && text.length() > count
                && text.charAt(text.length() - 1 - count) == '\n')
            count++;

        return count;
    }

    /** The line ends, up to two, that {@code text} starts with. */
    private static int leadingLineEnds(String text) {

        int count = 0;
        while (count < PARAGRAPH_BREAK.length() && text.length() > count && text.charAt(count) == '\n')
            count++;

        return count;
    }

    private static TextRange range(Paragraph paragraph) {

        List<SourceSpan> spans = paragraph.getSourceSpans();
        SourceSpan last = spans.getLast();

        return new TextRange(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }

    /** The text of the image's description, as the reader sees it: its escapes undone, its formatting left out. */
    private static String caption(Image image) {

        StringBuilder caption = new StringBuilder();
        image.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {

                caption.append(text.getLiteral());
            }

            @Override
            public void visit(Code code) {

                caption.append(code.getLiteral());
            }
        });

        return caption.toString();
    }
}
