package it.infog.psiche.markdown;

import org.commonmark.node.Image;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Paragraphs as commonmark writes them, but an image on a paragraph of its own, which is a figure: the image, centred
 * by the stylesheet, with its caption under it in a {@code <figcaption>}; an empty caption writes none. The preview,
 * the PDF and the EPUB show an image so, as a printed book does. The figure takes the attributes a paragraph would,
 * such as where it is in the text.
 */
public final class FigureRenderer implements NodeRenderer {

    private final HtmlNodeRendererContext context;
    private final Predicate<Image> shown;

    /** @param shown true for an image the page shows; a figure whose image it does not show is left out whole */
    public FigureRenderer(HtmlNodeRendererContext context, Predicate<Image> shown) {

        this.context = context;
        this.shown = shown;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {

        return Set.of(Paragraph.class);
    }

    @Override
    public void render(Node node) {

        Paragraph paragraph = (Paragraph) node;
        Optional<Image> figure = ImageLines.figure(paragraph);
        if (figure.isPresent())
            figure(paragraph, figure.get());
        else
            paragraph(paragraph);
    }

    private void figure(Paragraph paragraph, Image image) {

        if (!shown.test(image))
            return;

        HtmlWriter html = context.getWriter();
        html.line();
        html.tag("figure", context.extendAttributes(paragraph, "figure", Map.of()));
        context.render(image);
        String caption = PlainText.of(image);
        if (!caption.isBlank()) {
            // Given the image's attributes rather than the paragraph's, so only the figure says where it is.
            html.tag("figcaption", context.extendAttributes(image, "figcaption", Map.of()));
            html.text(caption);
            html.tag("/figcaption");
        }
        html.tag("/figure");
        html.line();
    }

    /** A paragraph as commonmark writes it: without {@code <p>} in a tight list, as its items' lines are. */
    private void paragraph(Paragraph paragraph) {

        HtmlWriter html = context.getWriter();
        boolean tight = paragraph.getParent() != null && paragraph.getParent().getParent() instanceof ListBlock list
                && list.isTight();
        if (!tight) {
            html.line();
            html.tag("p", context.extendAttributes(paragraph, "p", Map.of()));
        }
        HtmlChildren.render(context, paragraph);
        if (!tight) {
            html.tag("/p");
            html.line();
        }
    }
}
