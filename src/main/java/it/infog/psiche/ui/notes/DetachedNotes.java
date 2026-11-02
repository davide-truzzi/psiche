package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;
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
    private final Runnable changed;

    // True from a click on Detached notes until its dialog closes, so a second click opens no second one.
    private boolean opening;

    /** @param changed runs once notes have been restored or deleted for good */
    public DetachedNotes(NotesPane notesPane, StatusBar statusBar, ErrorReporter errors, Supplier<Window> window,
                        Runnable changed) {

        this.notesPane = notesPane;
        this.thread = notesPane.thread();
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.changed = changed;
    }

    /** Lists the notes of {@code store} kept for none of {@code chapters}, to restore or delete, until closed. */
    public void show(NoteStore store, List<String> chapters) {

        if (opening)
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

    /** Hands {@code answer} true if {@code store} keeps notes for none of {@code chapters}, for Detached notes. */
    public void hasAny(NoteStore store, List<String> chapters, Consumer<Boolean> answer) {

        thread.run(() -> store.detached().hasOrphans(chapters), answer,
                failure -> errors.report(READ_FAILURE, failure));
    }

    /** Asks, after {@code chapter} is restored, whether the notes it had when it was deleted come back too. */
    public void offerRestore(NoteStore store, String chapter) {

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
            changed.run();
        }, failure -> errors.report("Cannot restore the notes of " + group.chapter(), failure));
    }

    private void delete(NoteStore store, OrphanGroup group) {

        thread.change(() -> store.detached().deletePermanently(group), () -> {
            statusBar.info("Deleted the notes of %s for good".formatted(group.chapter()));
            changed.run();
        }, failure -> errors.report("Cannot delete the notes of " + group.chapter(), failure));
    }
}
