package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Supplier;

/**
 * The notes left without a chapter: lists them for the author to put back on a chapter's board or delete for good, and
 * offers a restored chapter the notes it had when it was deleted. It works without Git, as notes are not in it. Notes
 * are read and moved on the {@link NotesThread}, after what the board had to write, and a board they are put back on
 * shows them at once.
 */
public final class DetachedNotes {

    private static final String READ_FAILURE = "Cannot read the detached notes";

    private final NotesPane notesPane;
    private final NotesThread thread;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Supplier<NoteStore> store;
    private final Supplier<List<String>> chapters;
    private final ReadOnlyBooleanWrapper any = new ReadOnlyBooleanWrapper();

    // The notes last asked about, so an answer about another book's, read before it closed, is dropped.
    private NoteStore asked;
    // True from a click on Detached notes until its dialog closes, so a second click opens no second one.
    private boolean opening;

    /**
     * @param store    the open book's notes; {@code null} with no book open
     * @param chapters the open book's chapters, by name, in their order
     */
    public DetachedNotes(NotesPane notesPane, StatusBar statusBar, ErrorReporter errors, Supplier<Window> window,
                         Supplier<NoteStore> store, Supplier<List<String>> chapters) {

        this.notesPane = notesPane;
        this.thread = notesPane.thread();
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.store = store;
        this.chapters = chapters;
    }

    /** Lists the notes the open book keeps for none of its chapters, to restore or delete, until closed. */
    public void show() {

        NoteStore store = this.store.get();
        List<String> chapters = this.chapters.get();
        if (opening || store == null)
            return;

        opening = true;
        thread.run(() -> store.detached().orphans(chapters), groups -> {
            DetachedNotesDialog dialog = new DetachedNotesDialog(window.get(), chapters);
            dialog.setOnRestore((group, chapter) -> {
                restore(store, group, chapter);
                refill(dialog, store, chapters);
            });
            dialog.setOnDelete(group -> {
                delete(store, group);
                refill(dialog, store, chapters);
            });
            dialog.showAndWait(groups);
            opening = false;
        }, failure -> {
            errors.report(READ_FAILURE, failure);
            opening = false;
        });
    }

    /** True while the book keeps notes for no chapter, as {@link #check} last found: Detached notes shows then. */
    public ReadOnlyBooleanProperty anyProperty() {

        return any.getReadOnlyProperty();
    }

    /**
     * Looks, on the notes thread, whether the open book keeps notes for none of its chapters, and keeps the answer in
     * {@link #anyProperty}; with no book open there are none.
     */
    public void check() {

        NoteStore store = this.store.get();
        List<String> chapters = this.chapters.get();
        asked = store;
        if (store == null) {
            any.set(false);
            return;
        }

        thread.run(() -> store.detached().hasOrphans(chapters), has -> {
            if (asked == store)
                any.set(has);
        }, failure -> errors.report(READ_FAILURE, failure));
    }

    /** Asks, after {@code chapter} is restored, whether the notes it had when it was deleted come back too. */
    public void offerRestore(String chapter) {

        NoteStore store = this.store.get();
        thread.run(() -> store.detached().latestDeleted(chapter), group -> {
            if (group.isPresent() && DetachedNotesDialog.askRestore(window.get(), group.get()))
                restore(store, group.get(), chapter);
        }, failure -> errors.report("Cannot read the notes of " + chapter, failure));
    }

    /** Shows in {@code dialog} the groups left after the change asked for, or closes it if they can't be read. */
    private void refill(DetachedNotesDialog dialog, NoteStore store, List<String> chapters) {

        thread.run(() -> store.detached().orphans(chapters), dialog::showGroups, failure -> {
            errors.report(READ_FAILURE, failure);
            dialog.close();
        });
    }

    private void restore(NoteStore store, OrphanGroup group, String chapter) {

        thread.change(() -> store.detached().restoreTo(group, chapter), () -> {
            notesPane.reload(chapter);
            statusBar.info("Restored %s to %s".formatted(DetachedNotesDialog.count(group), chapter));
            check();
        }, failure -> errors.report("Cannot restore the notes of " + group.chapter(), failure));
    }

    private void delete(NoteStore store, OrphanGroup group) {

        thread.change(() -> store.detached().deletePermanently(group), () -> {
            statusBar.info("Deleted the notes of %s for good".formatted(group.chapter()));
            check();
        }, failure -> errors.report("Cannot delete the notes of " + group.chapter(), failure));
    }
}
