package it.infog.psiche.export;

import it.infog.psiche.markdown.HtmlChildren;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A chapter's footnotes at the foot of the page, as the manuscript's PDF prints them: the first reference to a note
 * holds the note, in a {@code <span class="footnote">} the stylesheet floats to the foot of the page and numbers, its
 * paragraphs each in a {@code <span class="note-paragraph">}; a later reference to the same note is its number alone,
 * in a {@code <sup class="footnote-again">}. The notes are numbered as {@link ChapterNotes} says, which the
 * stylesheet's counter, from 1 in every chapter, follows; where they are written prints nothing.
 */
final class PageFootnotes implements NodeRenderer {

    private final HtmlNodeRendererContext context;
    private final HtmlWriter html;
    private ChapterNotes notes;
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

        notes = new ChapterNotes(root);
    }

    @Override
    public void render(Node node) {

        if (!(node instanceof FootnoteReference reference))
            return;

        Optional<FootnoteDefinition> note = notes.note(reference);
        if (note.isEmpty())
            return;

        if (inNote || !notes.printsNote(reference)) {
            html.tag("sup", Map.of("class", "footnote-again"));
            html.text(String.valueOf(notes.number(reference)));
            html.tag("/sup");
            return;
        }

        inNote = true;
        html.tag("span", Map.of("class", "footnote"));
        ChapterNotes.parts(note.get()).forEach(this::paragraph);
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
