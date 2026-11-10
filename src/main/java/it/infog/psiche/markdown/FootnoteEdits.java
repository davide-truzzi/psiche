package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.infog.psiche.markdown.Footnotes.LABEL_CLOSING;
import static it.infog.psiche.markdown.Footnotes.LABEL_OPENING;
import static it.infog.psiche.markdown.Footnotes.NOTE_OPENING_END;
import static it.infog.psiche.markdown.Footnotes.key;

/**
 * The edits that write, change and remove a chapter's footnotes, each one change that a single Undo takes back. A
 * note is written as the note dialog gives it, paragraphs between blank lines, its opening before the first line and
 * every line after it set in by four spaces, so it never ends before its last paragraph. A new note is numbered one
 * after the highest number a label of the chapter is, and goes after the paragraph its reference is in.
 */
public final class FootnoteEdits {

    // Between a paragraph and a note after it.
    private static final String NOTE_BREAK = "\n\n";
    // What sets a note's lines after its first one in.
    private static final String NOTE_INDENT = "    ";
    // A label that is a number, as Psiche gives new notes.
    private static final Pattern NUMBER = Pattern.compile("\\d{1,9}");
    // Up to four spaces, or a tab, setting a line of a note in.
    private static final Pattern LINE_INDENT = Pattern.compile("^(?: {1,4}|\\t)");
    private static final Pattern BLANK_LINES = Pattern.compile("\n(?:[ \\t]*\n)+");

    private FootnoteEdits() {
    }

    /**
     * A new footnote, as Advanced mode writes it: a reference to the next free number at {@code position}, the caret
     * or the end of the selection, and its note's opening on a line of its own after the paragraph, with the caret
     * there, to type the note.
     */
    public static TextEdit insert(String text, int position) {

        String label = String.valueOf(nextNumber(text));
        int paragraphEnd = paragraphEnd(text, position);
        String replacement = reference(label) + text.substring(position, paragraphEnd) + NOTE_BREAK
                + LABEL_OPENING + label + NOTE_OPENING_END + " ";
        int caret = position + replacement.length();

        return new TextEdit(position, paragraphEnd, replacement, caret, caret);
    }

    /**
     * A new footnote, as Default mode writes it from the note dialog: a reference to the next free number at
     * {@code position}, and {@code note} after the paragraph; the caret goes after the reference, to write on.
     */
    public static TextEdit insert(String text, int position, String note) {

        String label = String.valueOf(nextNumber(text));
        int paragraphEnd = paragraphEnd(text, position);
        String replacement = reference(label) + text.substring(position, paragraphEnd) + NOTE_BREAK
                + written(label, note);
        int caret = position + reference(label).length();

        return new TextEdit(position, paragraphEnd, replacement, caret, caret);
    }

    /** A reference to the note of {@code label}, which is there already, at {@code position}; the caret after it. */
    public static TextEdit referenced(int position, String label) {

        String reference = reference(label);
        return new TextEdit(position, position, reference, position + reference.length(),
                position + reference.length());
    }

    /** {@code note} written for {@code reference}, a reference to no note, after the reference's paragraph. */
    public static TextEdit added(String text, FootnoteMark reference, String note) {

        int paragraphEnd = paragraphEnd(text, reference.range().end());
        String replacement = NOTE_BREAK + written(reference.label(), note);

        return new TextEdit(paragraphEnd, paragraphEnd, replacement, reference.range().end(),
                reference.range().end());
    }

    /** The text of {@code note} as the note dialog shows it: without its opening, nor the spaces setting it in. */
    public static String noteText(String text, FootnoteMark note) {

        String written = text.substring(note.range().end(), note.block().end()).strip();
        return written.lines()
                .map(line -> LINE_INDENT.matcher(line).replaceFirst(""))
                .map(line -> line.isBlank() ? "" : line)
                .collect(Collectors.joining("\n"));
    }

    /** {@code note} with {@code changed} as its text, its label as it is; the caret goes to its start. */
    public static TextEdit modified(FootnoteMark note, String changed) {

        String replacement = written(note.label(), changed);
        return new TextEdit(note.block().start(), note.block().end(), replacement, note.block().start(),
                note.block().start());
    }

    /** The edit that removes every note of {@code label} and every reference to it, the caret at the first one. */
    public static TextEdit removed(String text, String label) {

        FootnoteMarks marks = Footnotes.of(text);
        Predicate<FootnoteMark> labelled = mark -> key(mark.label()).equals(key(label));
        List<TextRange> parts = new ArrayList<>();
        marks.notes().stream().filter(labelled).forEach(note -> parts.add(withBlankLine(text, note.block())));
        marks.references().stream().filter(labelled).forEach(reference -> parts.add(reference.block()));

        int first = parts.stream().mapToInt(TextRange::start).min().orElse(0);
        return removing(text, new TextRange(first, first), "", parts);
    }

    /** The edit that removes {@code note} alone, with a blank line beside it, as a note nothing refers to. */
    public static TextEdit removedNote(String text, FootnoteMark note) {

        TextRange part = withBlankLine(text, note.block());
        return removing(text, new TextRange(part.start(), part.start()), "", List.of(part));
    }

    /** The edit that removes {@code reference} alone, as a reference to no note. */
    public static TextEdit removedReference(String text, FootnoteMark reference) {

        return removing(text, reference.block(), "", List.of());
    }

    /**
     * The edit that replaces {@code start} to {@code end} with {@code replacement} and leaves no footnote broken: a
     * note whose every reference it removes goes too, with a blank line beside it, and so do the references of a note
     * it removes, unless another note of their label stays. Empty if it leaves none broken as it is.
     */
    public static Optional<TextEdit> withNotes(String text, int start, int end, String replacement) {

        FootnoteMarks marks = Footnotes.of(text);
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

    /**
     * A note of {@code label} with {@code note} as its text: paragraphs between one blank line, every line after the
     * first set in, blank lines at either end left out.
     */
    private static String written(String label, String note) {

        String paragraphs = BLANK_LINES.matcher(note.replace("\r\n", "\n").replace('\r', '\n').strip())
                .replaceAll("\n\n");
        String[] lines = paragraphs.split("\n", -1);
        StringBuilder written = new StringBuilder(LABEL_OPENING + label + NOTE_OPENING_END + " " + lines[0]);
        for (int i = 1; i < lines.length; i++)
            written.append('\n').append(lines[i].isEmpty() ? "" : NOTE_INDENT + lines[i]);

        return written.toString();
    }

    private static String reference(String label) {

        return LABEL_OPENING + label + LABEL_CLOSING;
    }

    /** One more than the highest number a label of {@code text} is; 1 if none is a number. */
    private static int nextNumber(String text) {

        FootnoteMarks marks = Footnotes.of(text);
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
        int from = cut.isEmpty() ? removed.start() : Math.min(removed.start(), cut.nextSetBit(0));
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
}
