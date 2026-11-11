package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static it.infog.psiche.markdown.FootnoteEdits.NOTE_BREAK;
import static it.infog.psiche.markdown.FootnoteEdits.noteText;
import static it.infog.psiche.markdown.FootnoteEdits.removing;
import static it.infog.psiche.markdown.FootnoteEdits.withBlankLine;
import static it.infog.psiche.markdown.Footnotes.LABEL_OPENING;
import static it.infog.psiche.markdown.Footnotes.key;
import static it.infog.psiche.markdown.Footnotes.keys;

/**
 * Footnotes going where their text goes, in Default mode: a copy takes the notes of its references along, and a paste
 * puts them after the paragraph it lands in, numbered anew where their labels are taken.
 */
public final class FootnoteClipboard {

    private FootnoteClipboard() {
    }

    /**
     * The text from {@code start} to {@code end} as a copy takes it, with the notes of the references in it that it
     * does not hold, each after a blank line, as they are written: the notes go where the text goes.
     */
    public static String copied(String text, int start, int end) {

        FootnoteMarks marks = Footnotes.of(text);
        TextRange selection = new TextRange(start, end);
        Predicate<FootnoteMark> inside = mark -> selection.contains(mark.block());
        Set<String> held = keys(marks.notes().stream().filter(inside));

        StringBuilder copied = new StringBuilder(text.substring(start, end));
        marks.references().stream().filter(inside)
                .flatMap(reference -> Footnotes.noteOf(marks, reference.label()).stream())
                .distinct()
                .filter(note -> !held.contains(key(note.label())))
                .forEach(note -> copied.append(NOTE_BREAK)
                        .append(text, note.block().start(), note.block().end()));

        return copied.toString();
    }

    /**
     * The edit that pastes {@code pasted} in place of {@code start} to {@code end} with its notes after the paragraph
     * it lands in, each after a blank line. A note the rest of the text has already, the same label and text, as one
     * whose other references stay when one is cut, is that note: it stays where it is, and the pasted references lead
     * to it. Any other label the rest of the text has gets the next free number, in the pasted references and notes
     * alike, so the two never mix. The caret goes after the pasted text. Empty if {@code pasted} has no notes, to paste
     * it as it is.
     */
    public static Optional<TextEdit> pasted(String text, int start, int end, String pasted) {

        if (Footnotes.of(pasted).notes().isEmpty())
            return Optional.empty();

        //notes the rest of the text has as they are, which stay where they are; other labels it has, numbered anew
        String rest = text.substring(0, start) + text.substring(end);
        FootnoteMarks restMarks = Footnotes.of(rest);
        Set<String> kept = keys(Footnotes.of(pasted).notes().stream().filter(note -> Footnotes.noteOf(restMarks,
                note.label()).filter(found -> noteText(rest, found).equals(noteText(pasted, note))).isPresent()));
        Set<String> taken = new HashSet<>(keys(referencesAndNotes(restMarks)));
        taken.removeAll(kept);
        String renumbered = renumbered(pasted, taken, FootnoteEdits.nextNumber(rest));

        //the notes, taken out of the pasted text
        FootnoteMarks marks = Footnotes.of(renumbered);
        List<TextRange> noteParts = marks.notes().stream().map(note -> withBlankLine(renumbered, note.block()))
                .toList();
        String body = removing(renumbered, new TextRange(0, 0), "", noteParts).replacement();
        List<String> notes = marks.notes().stream()
                .filter(note -> !kept.contains(key(note.label())))
                .map(note -> renumbered.substring(note.block().start(), note.block().end()))
                .toList();

        //the text with the pasted one in, and the new notes after its paragraph
        String changed = text.substring(0, start) + body + text.substring(end);
        int paragraphEnd = FootnoteEdits.paragraphEnd(changed, start + body.length());
        if (!notes.isEmpty())
            changed = changed.substring(0, paragraphEnd) + NOTE_BREAK + String.join(NOTE_BREAK, notes)
                    + changed.substring(paragraphEnd);
        TextEdit edit = TextEdit.between(text, changed);

        return Optional.of(TextEdit.caretAt(edit.start(), edit.end(), edit.replacement(), start + body.length()));
    }

    /**
     * {@code pasted} with every label in {@code taken}, in its references and notes, numbered anew from {@code next}
     * on, one number to each.
     */
    private static String renumbered(String pasted, Set<String> taken, int next) {

        FootnoteMarks marks = Footnotes.of(pasted);
        Map<String, String> numbers = new HashMap<>();
        int number = next;
        for (FootnoteMark mark : referencesAndNotes(marks).toList())
            if (taken.contains(key(mark.label())) && !numbers.containsKey(key(mark.label())))
                numbers.put(key(mark.label()), String.valueOf(number++));
        if (numbers.isEmpty())
            return pasted;

        //each label, from the last back, so the earlier ones stay where they are
        StringBuilder result = new StringBuilder(pasted);
        referencesAndNotes(marks)
                .filter(mark -> numbers.containsKey(key(mark.label())))
                .sorted(Comparator.comparingInt((FootnoteMark mark) -> mark.range().start()).reversed())
                .forEach(mark -> {
                    int labelStart = mark.range().start() + LABEL_OPENING.length();
                    result.replace(labelStart, labelStart + mark.label().length(), numbers.get(key(mark.label())));
                });

        return result.toString();
    }

    /** The references to notes and the notes' openings of {@code marks}, without the references to no note. */
    private static Stream<FootnoteMark> referencesAndNotes(FootnoteMarks marks) {

        return Stream.of(marks.references(), marks.notes()).flatMap(List::stream);
    }
}
