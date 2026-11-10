package it.infog.psiche.export;

import it.infog.psiche.markdown.Footnotes;
import it.infog.psiche.markdown.HtmlChildren;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A chapter's footnotes at the foot of the page, as the manuscript's PDF prints them: the first reference to a note
 * holds the note, in a {@code <span class="footnote">} the stylesheet floats to the foot of the page and numbers, its
 * paragraphs each in a {@code <span class="note-paragraph">}; a later reference to the same note is its number alone,
 * in a {@code <sup class="footnote-again">}. The notes are numbered in the order of their first reference, as the
 * stylesheet's counter numbers them, from 1 in every chapter; where they are written prints nothing.
 */
final class PageFootnotes implements NodeRenderer {

    private final HtmlNodeRendererContext context;
    private final HtmlWriter html;
    // The chapter's notes by their label's key, the first one of each label, which readers see.
    private final Map<String, FootnoteDefinition> notes = new HashMap<>();
    private final Map<String, Integer> numbers = new HashMap<>();
    private final Set<String> printed = new HashSet<>();
    // True while a note is printed, whose own references are numbers only: a note never floats inside another.
    private boolean inNote;

    PageFootnotes(HtmlNodeRendererContext context) {

        this.context = context;
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {

        return Set.of(FootnoteReference.class, FootnoteDefinition.class);
    }

    @Override
    public void beforeRoot(Node root) {

        root.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomBlock block) {

                if (block instanceof FootnoteDefinition note)
                    notes.putIfAbsent(Footnotes.key(note.getLabel()), note);
                visitChildren(block);
            }
        });
        root.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomNode node) {

                if (node instanceof FootnoteReference reference)
                    numbers.putIfAbsent(Footnotes.key(reference.getLabel()), numbers.size() + 1);
                visitChildren(node);
            }
        });
    }

    @Override
    public void render(Node node) {

        if (!(node instanceof FootnoteReference reference))
            return;

        String key = Footnotes.key(reference.getLabel());
        FootnoteDefinition note = notes.get(key);
        if (note == null)
            return;

        if (inNote || !printed.add(key)) {
            html.tag("sup", Map.of("class", "footnote-again"));
            html.text(String.valueOf(numbers.get(key)));
            html.tag("/sup");
            return;
        }

        inNote = true;
        html.tag("span", Map.of("class", "footnote"));
        for (Node part = note.getFirstChild(); part != null; part = part.getNext())
            paragraph(part);
        html.tag("/span");
        inNote = false;
    }

    /** One of a note's paragraphs, inline, as the floated note must hold no block. */
    private void paragraph(Node part) {

        html.tag("span", Map.of("class", "note-paragraph"));
        if (part instanceof Paragraph)
            HtmlChildren.render(context, part);
        else
            context.render(part);
        html.tag("/span");
    }
}
