package it.infog.psiche.export.odt;

import it.infog.psiche.export.HtmlComments;
import it.infog.psiche.export.odt.model.Picture;
import it.infog.psiche.export.odt.model.enums.Style;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.Iterator;
import java.util.Optional;

/**
 * Turns a chapter's Markdown into the body of the document's {@code content.xml}: headings, paragraphs, lists and
 * spans that only name their {@link Style}, so the look stays in {@code styles.xml}. It follows the manuscript PDF:
 * scene breaks are {@code * * *}, raw HTML is kept as text but HTML comments are left out, and a line break inside a
 * paragraph is kept.
 */
final class ContentRenderer extends AbstractVisitor {

    private static final String SCENE_BREAK = "* * *";
    private static final String LINE_BREAK = "<text:line-break/>";

    private final OdtBody body;
    private final Pictures pictures;

    // Paragraphs are indented, except the chapter's first and the one after a heading, scene break, quote or list.
    private boolean indentNext;
    private int quoteDepth;
    private int listDepth;

    /**
     * Renders one chapter; the book renders each of its chapters with a renderer of its own, so every chapter's first
     * paragraph is unindented.
     *
     * @param body     where the chapter is written
     * @param pictures where the chapter's images are found, and collected to be packed into the document
     */
    ContentRenderer(OdtBody body, Pictures pictures) {

        this.body = body;
        this.pictures = pictures;
    }

    // Blocks

    /** A heading, with its outline level so the word processor's navigator lists it. */
    @Override
    public void visit(Heading heading) {

        body.openHeading(Style.heading(heading.getLevel()), Math.min(heading.getLevel(), 4));
        visitChildren(heading);
        body.append("</text:h>\n");

        indentNext = false;
    }

    @Override
    public void visit(Paragraph paragraph) {

        openParagraph(paragraphStyle(paragraph));
        visitChildren(paragraph);
        body.append("</text:p>\n");

        indentNext = true;
    }

    /** A paragraph's style, from what it holds and where it is. */
    private Style paragraphStyle(Paragraph paragraph) {

        if (paragraph.getFirstChild() instanceof Image && paragraph.getFirstChild() == paragraph.getLastChild())
            return Style.FIGURE;
        if (quoteDepth > 0)
            return Style.QUOTATIONS;
        if (listDepth > 0)
            return Style.LIST;

        return indentNext ? Style.TEXT_BODY : Style.FIRST_PARAGRAPH;
    }

    /** A Markdown rule ({@code ---}) is a scene break, as manuscripts mark one. */
    @Override
    public void visit(ThematicBreak thematicBreak) {

        openParagraph(Style.SCENE_BREAK);
        body.append(SCENE_BREAK).append("</text:p>\n");

        indentNext = false;
    }

    /** A quote's paragraphs take the {@link Style#QUOTATIONS} style, nested quotes included. */
    @Override
    public void visit(BlockQuote blockQuote) {

        quoteDepth++;
        visitChildren(blockQuote);
        quoteDepth--;

        indentNext = false;
    }

    @Override
    public void visit(BulletList bulletList) {

        list(bulletList, Style.BULLETS, 1);
    }

    @Override
    public void visit(OrderedList orderedList) {

        Integer start = orderedList.getMarkerStartNumber();
        list(orderedList, Style.NUMBERING, start == null ? 1 : start);
    }

    /**
     * A list and its items. A list inside an item is nested in it, and the list style indents it one level further.
     *
     * @param start the number of the first item, for a numbered list that does not start at 1
     */
    private void list(Node list, Style style, int start) {

        body.append("<text:list text:style-name=\"%s\">\n".formatted(style.xmlName()));
        listDepth++;

        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (item == list.getFirstChild() && start != 1)
                body.append("<text:list-item text:start-value=\"%d\">".formatted(start));
            else
                body.append("<text:list-item>");

            visitChildren(item);
            body.append("</text:list-item>\n");
        }

        listDepth--;
        body.append("</text:list>\n");

        indentNext = false;
    }

    @Override
    public void visit(FencedCodeBlock codeBlock) {

        lines(Style.PREFORMATTED, codeBlock.getLiteral(), true);
    }

    @Override
    public void visit(IndentedCodeBlock codeBlock) {

        lines(Style.PREFORMATTED, codeBlock.getLiteral(), true);
    }

    /**
     * Raw HTML is kept as text in a paragraph of its own, as the PDF prints it. HTML comments are the author's notes,
     * hidden in the preview, so they are left out, and a block holding only comments leaves no paragraph behind.
     */
    @Override
    public void visit(HtmlBlock htmlBlock) {

        String text = HtmlComments.strip(htmlBlock.getLiteral());
        if (text.isBlank())
            return;

        lines(indentNext ? Style.TEXT_BODY : Style.FIRST_PARAGRAPH, text, false);
        indentNext = true;
    }

    /**
     * A paragraph of {@code literal}'s lines, kept apart by line breaks.
     *
     * @param code true to keep the spaces at the start of each line, as code needs
     */
    private void lines(Style style, String literal, boolean code) {

        openParagraph(style);

        Iterator<String> lines = literal.stripTrailing().lines().iterator();
        while (lines.hasNext()) {
            body.text(lines.next(), code);
            if (lines.hasNext())
                body.append(LINE_BREAK);
        }

        body.append("</text:p>\n");
    }

    private void openParagraph(Style style) {

        body.openParagraph(style);
    }

    // Inline text

    @Override
    public void visit(Text text) {

        body.text(text.getLiteral(), false);
    }

    /** Inline HTML is kept as text, without its comments. */
    @Override
    public void visit(HtmlInline htmlInline) {

        body.text(HtmlComments.strip(htmlInline.getLiteral()), false);
    }

    @Override
    public void visit(Code code) {

        body.append("<text:span text:style-name=\"%s\">".formatted(Style.SOURCE_TEXT.xmlName()));
        body.text(code.getLiteral(), false);
        body.append("</text:span>");
    }

    @Override
    public void visit(Emphasis emphasis) {

        span(Style.EMPHASIS, emphasis);
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {

        span(Style.STRONG_EMPHASIS, strongEmphasis);
    }

    /** {@code node}'s text in a span of {@code style}; spans nest, so bold inside italic is both. */
    private void span(Style style, Node node) {

        body.append("<text:span text:style-name=\"%s\">".formatted(style.xmlName()));
        visitChildren(node);
        body.append("</text:span>");
    }

    /** A line break inside a paragraph is kept, as in the PDF and the preview. */
    @Override
    public void visit(SoftLineBreak softLineBreak) {

        body.append(LINE_BREAK);
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {

        body.append(LINE_BREAK);
    }

    /**
     * Web and mail links stay links. A link to one of the book's own files would lead nowhere once the chapter is
     * out of the book, so only its text is kept.
     */
    @Override
    public void visit(Link link) {

        String destination = link.getDestination();
        if (!destination.matches("(?i)(https?|mailto):.*")) {
            visitChildren(link);
            return;
        }

        body.append("<text:a xlink:type=\"simple\" xlink:href=\"%s\">".formatted(OdtText.escape(destination)));
        visitChildren(link);
        body.append("</text:a>");
    }

    /**
     * An image, drawn in the line like a large character, with its alt text as the title a screen reader reads. An
     * image that cannot be found or read is left out.
     */
    @Override
    public void visit(Image image) {

        Optional<Picture> found = pictures.find(image.getDestination());
        if (found.isEmpty())
            return;

        body.image(found.get(), altText(image));
    }

    /** The plain text of an image's description, the part in square brackets. */
    private static String altText(Image image) {

        StringBuilder alt = new StringBuilder();
        image.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                alt.append(text.getLiteral());
            }
        });

        return alt.toString();
    }
}
