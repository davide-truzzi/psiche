package it.infog.psiche.export;

import it.infog.psiche.markdown.Footnotes;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A chapter's footnotes as every export prints them: each note numbered in the order of its first reference, from 1,
 * as the preview numbers them; of two notes of a label, the first one, which readers see. It tells each reference
 * whether it is the first to its note, where the note is printed, or a later one, which prints its number alone.
 */
public final class ChapterNotes {

    // The chapter's notes by their label's key, the first one of each label, and their numbers.
    private final Map<String, FootnoteDefinition> notes = new HashMap<>();
    private final Map<String, Integer> numbers = new HashMap<>();
    private final Set<String> printed = new HashSet<>();

    /** The notes of {@code chapter}, its parsed document. */
    public ChapterNotes(Node chapter) {

        chapter.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomBlock block) {

                if (block instanceof FootnoteDefinition note)
                    notes.putIfAbsent(Footnotes.key(note.getLabel()), note);
                visitChildren(block);
            }
        });
        chapter.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomNode node) {

                if (node instanceof FootnoteReference reference && notes.containsKey(key(reference)))
                    numbers.putIfAbsent(key(reference), numbers.size() + 1);
                visitChildren(node);
            }
        });
    }

    /** The note {@code reference} leads to; empty if the chapter has none of its label. */
    public Optional<FootnoteDefinition> note(FootnoteReference reference) {

        return Optional.ofNullable(notes.get(key(reference)));
    }

    /** The number readers see {@code reference}'s note by; 0 if it leads to none. */
    public int number(FootnoteReference reference) {

        return numbers.getOrDefault(key(reference), 0);
    }

    /** True the first time it is asked for a reference to its note, which prints the note there. */
    public boolean printsNote(FootnoteReference reference) {

        return notes.containsKey(key(reference)) && printed.add(key(reference));
    }

    /** The paragraphs of {@code note}, and any other block it holds, in order. */
    public static List<Node> parts(FootnoteDefinition note) {

        List<Node> parts = new ArrayList<>();
        for (Node part = note.getFirstChild(); part != null; part = part.getNext())
            parts.add(part);

        return parts;
    }

    private static String key(FootnoteReference reference) {

        return Footnotes.key(reference.getLabel());
    }
}
