package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.FootnotePlace;
import it.infog.psiche.markdown.model.NumberedNote;
import it.infog.psiche.markdown.model.enums.FootnotePart;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    static final String LABEL_OPENING = "[^";
    static final String LABEL_CLOSING = "]";
    static final String NOTE_OPENING_END = "]:";
    // A reference that leads to no note, as the parser leaves it in the text.
    private static final Pattern MISSING_NOTE = Pattern.compile("\\[\\^([^\\s\\]]+)]");
    // How much of a note's text tells it from the others, in characters.
    private static final int SUMMARY_LENGTH = 40;
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
     * The footnote's part from {@code start} to {@code end} lies in, as the right-click menu is on: a reference, a
     * note, or a reference to no note, and whether it is wrong; empty if it lies in none.
     */
    public static Optional<FootnotePlace> at(String text, int start, int end) {

        FootnoteMarks marks = of(text);
        List<FootnoteMark> wrong = wrong(marks);
        TextRange spot = new TextRange(start, end);

        return Stream.of(places(marks.references(), FootnotePart.REFERENCE, wrong),
                        places(marks.notes(), FootnotePart.NOTE, wrong),
                        places(marks.missing(), FootnotePart.MISSING_REFERENCE, wrong))
                .flatMap(Function.identity())
                .filter(place -> place.mark().block().overlaps(spot))
                .findFirst();
    }

    /**
     * The notes of {@code text} a new reference can lead to, numbered as readers find them: those with references in
     * the order of their first one, then those nothing refers to yet. A second note of a label, never read, is not one.
     */
    public static List<NumberedNote> numberedNotes(String text) {

        FootnoteMarks marks = of(text);
        Set<String> referred = marks.references().stream().map(mark -> key(mark.label())).collect(Collectors.toSet());
        List<FootnoteMark> read = new ArrayList<>(marks.references().stream()
                .flatMap(reference -> noteOf(marks, reference.label()).stream())
                .distinct()
                .toList());
        Set<String> listed = new HashSet<>();
        for (FootnoteMark note : marks.notes())
            if (!referred.contains(key(note.label())) && listed.add(key(note.label())))
                read.add(note);

        return IntStream.range(0, read.size())
                .mapToObj(index -> numbered(text, read.get(index), index + 1))
                .toList();
    }

    /** The note of {@code label} readers see, the first one written; empty if it has none. */
    public static Optional<FootnoteMark> noteOf(FootnoteMarks marks, String label) {

        return marks.notes().stream().filter(note -> key(note.label()).equals(key(label))).findFirst();
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

    private static NumberedNote numbered(String text, FootnoteMark note, int number) {

        String written = FootnoteEdits.noteText(text, note);
        String read = TextCounter.readersText(written).replace('\n', ' ').strip();
        String summary = read.length() <= SUMMARY_LENGTH ? read : read.substring(0, SUMMARY_LENGTH).strip() + "…";
        return new NumberedNote(note.label(), number, written, summary);
    }

    private static Stream<FootnotePlace> places(List<FootnoteMark> marks, FootnotePart part,
                                                List<FootnoteMark> wrong) {

        return marks.stream().map(mark -> new FootnotePlace(mark, part, wrong.contains(mark)));
    }

    /** Where the {@code index}th mark of {@code marks} with {@code label}, counted from 0, starts. */
    private static OptionalInt nth(List<FootnoteMark> marks, String label, int index) {

        Predicate<FootnoteMark> labelled = mark -> key(mark.label()).equals(key(label));
        return marks.stream().filter(labelled).skip(index).mapToInt(mark -> mark.range().start()).findFirst();
    }

    /** A label as the parser compares it: its spaces as one, its capitals ignored. */
    public static String key(String label) {

        return label.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    private static TextRange range(Node node) {

        List<SourceSpan> spans = node.getSourceSpans();
        SourceSpan last = spans.getLast();
        return new TextRange(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }
}
