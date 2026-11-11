package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NotePin;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.notes.card.NoteCard;

import javafx.animation.PauseTransition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The open chapter's pins on disk, for the editor, which shows them on their paragraphs: read on the notes thread with
 * the chapter's notes, whichever board the Notes tab shows, and written there a moment after they last change, as a
 * note's text is, or at once on {@link #flush()}. Only the pins that changed are written.
 */
public final class PinnedNotes {

    private final NotesThread notes;
    private final ErrorReporter errors;
    private final PauseTransition saveDelay = new PauseTransition(NoteCard.SAVE_DELAY);
    // The chapter whose pins are kept, null with none; its pins as last read or written, by note id.
    private String chapter;
    private Map<String, NotePin> saved = Map.of();
    // Its pins waiting to be written, or null.
    private Map<String, NotePin> waiting;

    PinnedNotes(NotesThread notes, ErrorReporter errors) {

        this.notes = notes;
        this.errors = errors;
        saveDelay.setOnFinished(event -> flush());
    }

    /**
     * Reads the notes of {@code chapter}, pinned or not, then hands them to {@code done} on the FX thread, unless
     * another chapter was asked for meanwhile. The pins of the chapter before are written first.
     */
    public void read(String chapter, Consumer<List<Note>> done) {

        close();
        NoteStore store = notes.store();
        if (store == null)
            return;

        this.chapter = chapter;
        notes.run(() -> store.notes(chapter), read -> {
            if (!chapter.equals(this.chapter))
                return;

            Map<String, NotePin> pins = new HashMap<>();
            read.forEach(note -> note.pin().ifPresent(pin -> pins.put(note.id(), pin)));
            saved = pins;
            done.accept(read);
        }, failure -> errors.report("Cannot read the notes of " + chapter, failure));
    }

    /** Keeps {@code pins} as the chapter's, all of them by note id, to be written a moment after the last change. */
    public void save(Map<String, NotePin> pins) {

        if (chapter == null)
            return;

        waiting = pins;
        saveDelay.playFromStart();
    }

    /** Writes now the pins waiting to be, those that changed since they were last read or written. */
    public void flush() {

        saveDelay.stop();
        if (waiting == null || chapter == null)
            return;

        Map<String, Optional<NotePin>> changes = new HashMap<>();
        saved.keySet().stream().filter(id -> !waiting.containsKey(id)).forEach(id -> changes.put(id, Optional.empty()));
        waiting.forEach((id, pin) -> {
            if (!pin.equals(saved.get(id)))
                changes.put(id, Optional.of(pin));
        });
        saved = waiting;
        waiting = null;
        if (changes.isEmpty())
            return;

        NoteStore store = notes.store();
        String title = chapter;
        notes.change(() -> changes.forEach((id, pin) -> savePin(store, title, id, pin)), () -> {},
                failure -> errors.report("Cannot save the pins of " + title, failure));
    }

    /** Writes the pins waiting, and keeps no chapter's from now on, as the chapter closes. */
    public void close() {

        flush();
        chapter = null;
        saved = Map.of();
    }

    private static void savePin(NoteStore store, String chapter, String id, Optional<NotePin> pin) {

        try {
            store.savePin(chapter, id, pin);
        } catch (IllegalArgumentException e) {
            // The note left the chapter meanwhile, moved or detached, and its pin with it.
        }
    }
}
