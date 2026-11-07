package it.infog.psiche.markdown;

import org.commonmark.node.Node;
import org.commonmark.renderer.html.HtmlNodeRendererContext;

/** Renders the children of a node, for the renderers that write a node's own tags around them. */
public final class HtmlChildren {

    private HtmlChildren() {
    }

    /** Renders each child of {@code parent} with {@code context}, taking the next one first: rendering may move it. */
    public static void render(HtmlNodeRendererContext context, Node parent) {

        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            context.render(child);
            child = next;
        }
    }
}
