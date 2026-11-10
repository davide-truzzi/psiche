package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.TextRange;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A chapter's footnotes: a reference, {@code text[^moon]}, to a note written anywhere in the chapter,
 * {@code [^moon]: The note.}, which a reader finds numbered at the chapter's end, in the order of the references.
 * Labels are the same whatever their capitals, and the first note of a label is the one read. A reference to no note
 * stays as it is written; a note no reference leads to is left out.
 * <p>
 * In the preview, a reference links to {@code #fn-} and its note's label, and each of the note's way back to
 * {@code #fnref-} and the label, followed for the second reference on by a dash and its number.
 */
public final class Footnotes {

    private static final Parser PARSER = MarkdownSyntax.parser(IncludeSourceSpans.BLOCKS_AND_INLINES);
    private static final String NOTE_LINK = "#fn-";
    private static final String REFERENCE_LINK = "#fnref-";
    private static final String NOTE_OPENING_END = "]:";
    // A reference that leads to no note, as the parser leaves it in the text.
    private static final Pattern MISSING_NOTE = Pattern.compile("\\[\\^[^\\s\\]]+]");
    // The way back to the second reference of a note or a later one: its label, a dash and its number.
    private static final Pattern LATER_REFERENCE = Pattern.compile("(.+)-(\\d+)");

    private Footnotes() {
    }

    /** The references and notes' openings of {@code text}. */
    public static FootnoteMarks of(String text) {

        List<FootnoteMark> references = new ArrayList<>();
        List<FootnoteMark> notes = new ArrayList<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(CustomNode node) {

                if (node instanceof FootnoteReference reference && !node.getSourceSpans().isEmpty())
                    references.add(new FootnoteMark(reference.getLabel(), range(node)));
                visitChildren(node);
            }

            @Override
            public void visit(CustomBlock block) {

                if (block instanceof FootnoteDefinition note && !block.getSourceSpans().isEmpty()) {
                    int start = block.getSourceSpans().getFirst().getInputIndex();
                    int end = text.indexOf(NOTE_OPENING_END, start) + NOTE_OPENING_END.length();
                    notes.add(new FootnoteMark(note.getLabel(), new TextRange(start, end)));
                }
                visitChildren(block);
            }
        });

        return new FootnoteMarks(references, notes);
    }

    /** True if {@code destination} is a link of the preview's footnotes, to a note or back to its reference. */
    public static boolean isFootnoteLink(String destination) {

        return destination.startsWith(NOTE_LINK) || destination.startsWith(REFERENCE_LINK);
    }

    /**
     * Where a link of the preview's footnotes leads in {@code text}: a note's opening, or the reference a note's way
     * back is for; empty if it leads nowhere, or is no such link.
     */
    public static OptionalInt place(String text, String destination) {

        FootnoteMarks marks = of(text);

        if (destination.startsWith(NOTE_LINK))
            return nth(marks.notes(), destination.substring(NOTE_LINK.length()), 0);
        if (!destination.startsWith(REFERENCE_LINK))
            return OptionalInt.empty();

        String label = destination.substring(REFERENCE_LINK.length());
        OptionalInt reference = nth(marks.references(), label, 0);
        Matcher later = LATER_REFERENCE.matcher(label);
        if (reference.isPresent() || !later.matches())
            return reference;

        return nth(marks.references(), later.group(1), Integer.parseInt(later.group(2)) - 1);
    }

    /** Each reference written in {@code literal}, a piece of prose, that leads to no note, in order. */
    public static List<TextRange> missingNotes(String literal) {

        return MISSING_NOTE.matcher(literal).results()
                .map(found -> new TextRange(found.start(), found.end()))
                .toList();
    }

    /** Where the {@code index}th mark of {@code marks} with {@code label}, counted from 0, starts. */
    private static OptionalInt nth(List<FootnoteMark> marks, String label, int index) {

        Predicate<FootnoteMark> labelled = mark -> key(mark.label()).equals(key(label));
        return marks.stream().filter(labelled).skip(index).mapToInt(mark -> mark.range().start()).findFirst();
    }

    /** A label as the parser compares it: its spaces as one, its capitals ignored. */
    private static String key(String label) {

        return label.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    private static TextRange range(Node node) {

        List<SourceSpan> spans = node.getSourceSpans();
        SourceSpan last = spans.getLast();
        return new TextRange(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }
}
