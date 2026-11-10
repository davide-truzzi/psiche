package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String LABEL_OPENING = "[^";
    private static final String LABEL_CLOSING = "]";
    private static final String NOTE_OPENING_END = "]:";
    // Between a paragraph and a note after it.
    private static final String NOTE_BREAK = "\n\n";
    // A label that is a number, as Psiche gives new notes.
    private static final Pattern NUMBER = Pattern.compile("\\d{1,9}");
    // A reference that leads to no note, as the parser leaves it in the text.
    private static final Pattern MISSING_NOTE = Pattern.compile("\\[\\^([^\\s\\]]+)]");
    // The way back to the second reference of a note or a later one: its label, a dash and its number.
    private static final Pattern LATER_REFERENCE = Pattern.compile("(.+)-(\\d+)");

    private Footnotes() {
    }

    /** The references and notes' openings of {@code text}. */
    public static FootnoteMarks of(String text) {

        List<FootnoteMark> references = new ArrayList<>();
        List<FootnoteMark> notes = new ArrayList<>();
        List<FootnoteMark> missing = new ArrayList<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Text prose) {

                for (SourceSpan span : prose.getSourceSpans()) {
                    Matcher found = MISSING_NOTE.matcher(text).region(span.getInputIndex(),
                            span.getInputIndex() + span.getLength());
                    while (found.find())
                        missing.add(new FootnoteMark(found.group(1), new TextRange(found.start(), found.end())));
                }
            }

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
                    notes.add(new FootnoteMark(note.getLabel(), new TextRange(start, end), range(block)));
                }
                visitChildren(block);
            }
        });

        return new FootnoteMarks(references, notes, missing);
    }

    /**
     * The marks of {@code marks} that are wrong, in the order of the text: each reference to no note, each note no
     * reference leads to, and each note of a label an earlier note has, which is never read.
     */
    public static List<FootnoteMark> wrong(FootnoteMarks marks) {

        Set<String> referred = marks.references().stream().map(mark -> key(mark.label())).collect(Collectors.toSet());
        Set<String> written = new HashSet<>();
        List<FootnoteMark> wrong = new ArrayList<>(marks.missing());
        for (FootnoteMark note : marks.notes())
            if (!written.add(key(note.label())) || !referred.contains(key(note.label())))
                wrong.add(note);

        wrong.sort(Comparator.comparingInt(mark -> mark.range().start()));
        return List.copyOf(wrong);
    }

    /** Where the Markdown around {@code mark}'s label is: {@code [^} and {@code ]}, or {@code ]:} for a note. */
    public static List<TextRange> markers(FootnoteMark mark) {

        int labelStart = mark.range().start() + LABEL_OPENING.length();
        return List.of(new TextRange(mark.range().start(), labelStart),
                new TextRange(labelStart + mark.label().length(), mark.range().end()));
    }

    /** The link to the note of {@code label}, as a reference leads to it. */
    public static String noteLink(String label) {

        return NOTE_LINK + label;
    }

    /** The link back to the first reference to the note of {@code label}. */
    public static String referenceLink(String label) {

        return REFERENCE_LINK + label;
    }

    /**
     * A new footnote, as Advanced mode writes it: a reference to the next free number at {@code position}, the caret
     * or the end of the selection, and its note's opening on a line of its own after the paragraph, with the caret
     * there, to type the note.
     */
    public static TextEdit insert(String text, int position) {

        String label = String.valueOf(nextNumber(text));
        int paragraphEnd = paragraphEnd(text, position);
        String replacement = LABEL_OPENING + label + LABEL_CLOSING + text.substring(position, paragraphEnd)
                + NOTE_BREAK + LABEL_OPENING + label + NOTE_OPENING_END + " ";
        int caret = position + replacement.length();

        return new TextEdit(position, paragraphEnd, replacement, caret, caret);
    }

    /**
     * The edit that replaces {@code start} to {@code end} with {@code replacement} and leaves no footnote broken: a
     * note whose every reference it removes goes too, with a blank line beside it, and so do the references of a note
     * it removes, unless another note of their label stays. Empty if it leaves none broken as it is.
     */
    public static Optional<TextEdit> withNotes(String text, int start, int end, String replacement) {

        FootnoteMarks marks = of(text);
        TextRange removed = new TextRange(start, end);
        Predicate<FootnoteMark> gone = mark -> removed.start() <= mark.block().start()
                && mark.block().end() <= removed.end();
        Set<String> staying = keys(marks.references().stream().filter(Predicate.not(gone)));
        Set<String> stayingNotes = keys(marks.notes().stream().filter(Predicate.not(gone)));
        Set<String> goneReferences = keys(marks.references().stream().filter(gone));
        Set<String> goneNotes = keys(marks.notes().stream().filter(gone));

        //the notes left without references, and the references left without a note
        List<TextRange> extra = new ArrayList<>();
        marks.notes().stream().filter(Predicate.not(gone))
                .filter(note -> goneReferences.contains(key(note.label())) && !staying.contains(key(note.label())))
                .forEach(note -> extra.add(withBlankLine(text, note.block())));
        marks.references().stream().filter(Predicate.not(gone))
                .filter(reference -> goneNotes.contains(key(reference.label()))
                        && !stayingNotes.contains(key(reference.label())))
                .forEach(reference -> extra.add(reference.block()));
        if (extra.isEmpty())
            return Optional.empty();

        return Optional.of(removing(text, removed, replacement, extra));
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

    /** One more than the highest number a label of {@code text} is; 1 if none is a number. */
    private static int nextNumber(String text) {

        FootnoteMarks marks = of(text);
        return Stream.of(marks.references(), marks.notes(), marks.missing())
                .flatMap(List::stream)
                .map(FootnoteMark::label)
                .filter(NUMBER.asMatchPredicate())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }

    /**
     * Where the paragraph holding {@code position} ends: at the line break before the next blank line, or at the end
     * of the text.
     */
    private static int paragraphEnd(String text, int position) {

        for (int lineBreak = text.indexOf('\n', position); lineBreak >= 0; ) {
            int next = text.indexOf('\n', lineBreak + 1);
            if (text.substring(lineBreak + 1, next < 0 ? text.length() : next).isBlank())
                return lineBreak;
            lineBreak = next;
        }

        return text.length();
    }

    /**
     * The edit that puts {@code replacement} in place of {@code removed} and takes {@code extra} out too, as one
     * change, with the caret after the replacement.
     */
    private static TextEdit removing(String text, TextRange removed, String replacement, List<TextRange> extra) {

        BitSet cut = new BitSet(text.length());
        cut.set(removed.start(), removed.end());
        extra.forEach(range -> cut.set(range.start(), range.end()));
        int from = Math.min(removed.start(), cut.nextSetBit(0));
        int to = Math.max(removed.end(), cut.length());

        StringBuilder result = new StringBuilder();
        int caret = from;
        for (int i = from; i <= to; i++) {
            if (i == removed.start()) {
                result.append(replacement);
                caret = from + result.length();
            }
            if (i < to && !cut.get(i))
                result.append(text.charAt(i));
        }

        return new TextEdit(from, to, result.toString(), caret, caret);
    }

    /** {@code note}, with the blank line before it, or after it if it starts the text, so no gap is left. */
    private static TextRange withBlankLine(String text, TextRange note) {

        if (text.startsWith(NOTE_BREAK, note.start() - NOTE_BREAK.length()))
            return new TextRange(note.start() - NOTE_BREAK.length(), note.end());
        if (text.startsWith(NOTE_BREAK, note.end()))
            return new TextRange(note.start(), note.end() + NOTE_BREAK.length());
        return note;
    }

    private static Set<String> keys(Stream<FootnoteMark> marks) {

        return marks.map(mark -> key(mark.label())).collect(Collectors.toSet());
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
