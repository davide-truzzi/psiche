package it.infog.psiche.ui.chapters;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NotePin;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.NotePinning;
import it.infog.psiche.ui.notes.NotesPane;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The open chapter's pinned notes, between their notes and the editor: read as the chapter opens and shown on their
 * paragraphs, saved as they follow the text, and the author told when one goes to another paragraph than its own. The
 * Notes tab pins and unpins the notes through it, and is shown the note whose pin the author clicks.
 */
final class ChapterPins implements NotePinning {

    private static final String MOVED = "Pinned note \"%s\" moved to paragraph %d";
    // How much of a note's first line names it in the status bar.
    private static final int SHORT_LABEL = 40;
    private static final String ELLIPSIS = "…";

    private final ChapterEditor editor;
    private final PinMarks marks;
    private final NotesPane notesPane;
    // The text of each of the chapter's notes as read, by id, which names a note no longer on the board.
    private Map<String, String> texts = Map.of();

    ChapterPins(ChapterEditor editor, NotesPane notesPane, StatusBar statusBar) {

        this.editor = editor;
        this.marks = editor.pins();
        this.notesPane = notesPane;
        marks.setLabel(this::label);
        marks.setOnClicked(id -> notesPane.boardPins().show(id));
        marks.setOnChanged(pins -> {
            notesPane.pins().save(pins);
            notesPane.boardPins().refresh();
        });
        marks.setOnMoved((id, paragraph) -> statusBar.info(MOVED.formatted(shortLabel(id), paragraph + 1)));
        notesPane.boardPins().setPinning(this);
    }

    /** Reads the pins of {@code chapter}, just opened in the editor, and shows them there. */
    void open(String chapter) {

        notesPane.pins().read(chapter, notes -> {
            Map<String, String> read = new HashMap<>();
            Map<String, NotePin> pins = new HashMap<>();
            for (Note note : notes) {
                read.put(note.id(), note.text());
                note.pin().ifPresent(pin -> pins.put(note.id(), pin));
            }
            texts = read;
            marks.show(pins);
            notesPane.boardPins().refresh();
        });
    }

    /** Saves the pins of the chapter just closed, and forgets its notes. */
    void close() {

        notesPane.pins().close();
        texts = Map.of();
    }

    // The Notes tab's pinning

    @Override
    public boolean isPinned(String id) {

        return marks.pins().containsKey(id);
    }

    @Override
    public boolean canPinHere() {

        return editor.chapter().isPresent() && marks.canHold(caretParagraph());
    }

    @Override
    public Optional<String> pinnedHere() {

        return marks.pinnedTo(caretParagraph());
    }

    @Override
    public OptionalInt pinHere(String id) {

        if (!canPinHere())
            return OptionalInt.empty();

        int paragraph = caretParagraph();
        marks.pin(id, paragraph);
        return OptionalInt.of(paragraph + 1);
    }

    @Override
    public void unpin(String id) {

        marks.unpin(id);
    }

    @Override
    public void goTo(String id) {

        marks.start(id).ifPresent(editor::reveal);
    }

    // Names

    private int caretParagraph() {

        return marks.paragraphAt(editor.caret());
    }

    /** The note's first line, as typed on the board, or as read with the chapter's notes. */
    private String label(String id) {

        return NotePinning.label(notesPane.boardPins().text(id).orElse(texts.getOrDefault(id, "")));
    }

    /** The note's first line, cut short with an ellipsis if it is long. */
    private String shortLabel(String id) {

        String label = label(id);
        return label.length() <= SHORT_LABEL ? label : label.substring(0, SHORT_LABEL).strip() + ELLIPSIS;
    }
}
