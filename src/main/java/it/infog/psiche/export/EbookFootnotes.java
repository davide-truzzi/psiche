package it.infog.psiche.export;

import it.infog.psiche.markdown.HtmlChildren;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A chapter's footnotes as an ebook shows them, which e-readers open as pop-ups: each reference is a raised number
 * linking to its note, {@code epub:type="noteref"}, and the notes follow at the end of the chapter's page, each an
 * {@code <aside epub:type="footnote">}, numbered as {@link ChapterNotes} says, its last paragraph ending with a way
 * back to the note's first reference. Readers without pop-ups go to the note and back. Where a note is written prints
 * nothing; a reference to no note, nothing either.
 */
final class EbookFootnotes implements NodeRenderer {

    private static final String BACK = "↩";

    private final HtmlNodeRendererContext context;
    private final HtmlWriter html;
    private final String chapterId;
    private ChapterNotes notes;
    // The notes to print at the page's end, by number, in the order of their first reference.
    private final Map<Integer, FootnoteDefinition> printed = new LinkedHashMap<>();

    /** @param chapterId the chapter's id, which the notes' ids start with, unique in the book */
    EbookFootnotes(HtmlNodeRendererContext context, String chapterId) {

        this.context = context;
        this.html = context.getWriter();
        this.chapterId = chapterId;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {

        return Set.of(FootnoteReference.class, FootnoteDefinition.class);
    }

    @Override
    public void beforeRoot(Node root) {

        notes = new ChapterNotes(root);
    }

    /** A reference, as a raised number linking to its note; the first one to a note is where the note leads back. */
    @Override
    public void render(Node node) {

        if (!(node instanceof FootnoteReference reference))
            return;

        Optional<FootnoteDefinition> note = notes.note(reference);
        if (note.isEmpty())
            return;

        int number = notes.number(reference);
        Map<String, String> attributes = new LinkedHashMap<>();
        if (notes.printsNote(reference)) {
            attributes.put("id", referenceId(number));
            printed.put(number, note.get());
        }
        attributes.put("href", "#" + noteId(number));
        attributes.put("epub:type", "noteref");
        attributes.put("role", "doc-noteref");

        html.tag("sup");
        html.tag("a", attributes);
        html.text(String.valueOf(number));
        html.tag("/a");
        html.tag("/sup");
    }

    /** The chapter's notes, after its text, in the order of their numbers. */
    @Override
    public void afterRoot(Node root) {

        printed.forEach(this::note);
    }

    private void note(int number, FootnoteDefinition note) {

        html.line();
        html.tag("aside", attributes("id", noteId(number), "epub:type", "footnote", "role", "doc-footnote"));
        List<Node> parts = ChapterNotes.parts(note);

        //its paragraphs, the first with its number, the last with the way back
        for (int i = 0; i < parts.size(); i++) {
            Node part = parts.get(i);
            if (!(part instanceof Paragraph)) {
                context.render(part);
                continue;
            }

            html.tag("p");
            if (i == 0)
                html.text(number + ". ");
            HtmlChildren.render(context, part);
            if (i == parts.size() - 1)
                back(number);
            html.tag("/p");
        }

        html.tag("/aside");
        html.line();
    }

    private void back(int number) {

        html.text(" ");
        html.tag("a", attributes("href", "#" + referenceId(number), "role", "doc-backlink"));
        html.text(BACK);
        html.tag("/a");
    }

    /** Attributes from names and values in turn, written in that order, so every export of a book is the same. */
    private static Map<String, String> attributes(String... namesAndValues) {

        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2)
            attributes.put(namesAndValues[i], namesAndValues[i + 1]);

        return attributes;
    }

    private String noteId(int number) {

        return chapterId + "--note-" + number;
    }

    private String referenceId(int number) {

        return chapterId + "--noteref-" + number;
    }
}
