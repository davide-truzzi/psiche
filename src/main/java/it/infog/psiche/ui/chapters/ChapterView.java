package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.git.HistoryPane;
import it.infog.psiche.ui.git.RevisionDialog;
import it.infog.psiche.ui.media.MediaPane;
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.preview.PreviewPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The open chapter, in the editor and in what follows it: the preview, the history, the notes, the images and the
 * status bar. It opens and closes a chapter there, reads it again after Synchronize, and shows a past version of it.
 */
final class ChapterView {

    // The text area's own Undo shortcut, named in messages as the platform shows it (Ctrl+Z, or ⌘Z on macOS).
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);

    private final ChapterEditor chapterEditor;
    private final PreviewPane preview;
    private final HistoryPane history;
    private final NotesPane notesPane;
    private final MediaPane mediaPane;
    private final ChapterStatus status;
    private final ChapterRevisions revisions;
    private final StatusBar statusBar;
    private final Supplier<Window> window;

    private BookFolder book;
    private BookRepository repository;
    private NoteStore notes;

    ChapterView(ChapterEditor chapterEditor, PreviewPane preview, HistoryPane history, NotesPane notesPane,
                MediaPane mediaPane, ChapterStatus status, ChapterRevisions revisions, StatusBar statusBar,
                Supplier<Window> window) {

        this.chapterEditor = chapterEditor;
        this.preview = preview;
        this.history = history;
        this.notesPane = notesPane;
        this.mediaPane = mediaPane;
        this.status = status;
        this.revisions = revisions;
        this.statusBar = statusBar;
        this.window = window;
    }

    /** The open book, its repository and its notes, whose chapters are shown; {@code null}s once it closes. */
    void setBook(BookFolder book, BookRepository repository, NoteStore notes) {

        this.book = book;
        this.repository = repository;
        this.notes = notes;
    }

    /** Opens {@code chapter} in the editor, and shows its preview, history, notes and images. */
    void open(Chapter chapter) {

        if (chapterEditor.open(book, chapter)) {
            preview.render(chapterEditor.text(), book.chaptersDir());
            history.show(repository, chapter);
            notesPane.show(notes, chapter.title());
            mediaPane.show(book, chapter);
            status.showChapter();
            status.countChapter();
        }
    }

    /** Closes the open chapter, and empties what follows it: the history, the notes, the images. */
    void close() {

        chapterEditor.close();
        history.show(repository, null);
        notesPane.show(notes, null);
        mediaPane.show(null, null);
        status.showChapter();
    }

    /**
     * Shows the book as Synchronize left it on disk: the chapters listed again by {@code refresh}, and the open one
     * read again, or closed if it is gone. Synchronize runs only with every chapter saved, so no unsaved change is
     * lost.
     *
     * @param chapters the chapters listed, once {@code refresh} has run
     */
    void synchronizedBook(SyncReport report, Runnable refresh, Supplier<List<Chapter>> chapters) {

        history.refresh();
        revisions.check();
        if (report.changed().isEmpty())
            return;

        Optional<Chapter> open = chapterEditor.chapter();
        refresh.run();
        if (open.isPresent() && chapters.get().contains(open.get()))
            open(open.get());
        else if (open.isPresent())
            close();
    }

    /**
     * Shows how the saved chapter differs from a past version of it, as tracked changes. Restore puts that
     * version in the editor as an unsaved change, which Undo reverts like any edit.
     */
    void showRevision(Chapter chapter, FileRevision revision, Optional<String> text) {

        // The version was read in the background; the author may have opened another chapter meanwhile.
        if (!chapterEditor.isOpen(chapter))
            return;

        boolean restore = RevisionDialog.show(
                window.get(), revision, text, chapterEditor.savedText(), chapterEditor.isDirty());

        if (restore && text.isPresent()) {
            chapterEditor.replaceAll(text.get());
            statusBar.info("Restored %s from %s, not saved yet; %s brings back your text".formatted(
                    chapter.title(), revision.shortId(), UNDO.getDisplayText()));
        }
    }
}
