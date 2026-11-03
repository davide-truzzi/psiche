package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.markdown.HtmlComments;

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
 * Writes a chapter's Markdown into a {@link DocumentBody}, in any format: headings, paragraphs, lists and spans that
 * only name their {@link Style}, so the look stays in the format's stylesheet. It follows the manuscript PDF: scene
 * breaks are {@code * * *}, raw HTML is kept as text but HTML comments are left out, and a line break inside a
 * paragraph is kept.
 */
final class ChapterRenderer extends AbstractVisitor {

    private static final String SCENE_BREAK = "* * *";

    private final DocumentBody body;
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
    ChapterRenderer(DocumentBody body, Pictures pictures) {

        this.body = body;
        this.pictures = pictures;
    }

    // Blocks

    /** A heading, with its outline level so the word processor's navigator lists it. */
    @Override
    public void visit(Heading heading) {

        body.openHeading(Style.heading(heading.getLevel()), Math.min(heading.getLevel(), 4));
        visitChildren(heading);
        body.closeHeading();

        indentNext = false;
    }

    @Override
    public void visit(Paragraph paragraph) {

        body.openParagraph(paragraphStyle(paragraph));
        visitChildren(paragraph);
        body.closeParagraph();

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

        body.openParagraph(Style.SCENE_BREAK);
        body.text(SCENE_BREAK, false);
        body.closeParagraph();

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

        body.openList(style);
        listDepth++;

        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            body.openListItem(item == list.getFirstChild() && start != 1 ? start : null);
            visitChildren(item);
            body.closeListItem();
        }

        listDepth--;
        body.closeList();

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

        body.openParagraph(style);

        Iterator<String> lines = literal.stripTrailing().lines().iterator();
        while (lines.hasNext()) {
            body.text(lines.next(), code);
            if (lines.hasNext())
                body.lineBreak();
        }

        body.closeParagraph();
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

        body.openSpan(Style.SOURCE_TEXT);
        body.text(code.getLiteral(), false);
        body.closeSpan();
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

        body.openSpan(style);
        visitChildren(node);
        body.closeSpan();
    }

    /** A line break inside a paragraph is kept, as in the PDF and the preview. */
    @Override
    public void visit(SoftLineBreak softLineBreak) {

        body.lineBreak();
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {

        body.lineBreak();
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

        body.openLink(destination);
        visitChildren(link);
        body.closeLink();
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
