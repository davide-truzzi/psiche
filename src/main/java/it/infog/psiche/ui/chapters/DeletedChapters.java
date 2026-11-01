package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.GitCommandException;
import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.git.HistoryPane;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The chapters deleted from the book: lists them for the author to pick one, writes the one picked back with the text
 * it had when it was deleted, offers back the notes it had then, and commits it, so its history goes on from before
 * the deletion.
 */
public final class DeletedChapters {

    private final GitControls gitControls;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Predicate<String> readyForCommit;
    private final Consumer<String> restoreNotes;
    private final Consumer<Chapter> showRestored;

    /**
     * @param readyForCommit tells, given why, whether the restore can be committed alone; if not, it has told the
     *                       author what to do first
     * @param restoreNotes   offers back, by the restored chapter's name, the notes it had when it was deleted
     * @param showRestored   lists the book's chapters again and opens the restored one
     */
    DeletedChapters(GitControls gitControls, ErrorReporter errors, Supplier<Window> window,
                    Predicate<String> readyForCommit, Consumer<String> restoreNotes, Consumer<Chapter> showRestored) {

        this.gitControls = gitControls;
        this.errors = errors;
        this.window = window;
        this.readyForCommit = readyForCommit;
        this.restoreNotes = restoreNotes;
        this.showRestored = showRestored;
    }

    /** Lists the chapters deleted from {@code book}, and restores the one the author picks. */
    void show(BookFolder book, BookRepository repository) {

        List<DeletedChapter> deleted;
        try {
            deleted = repository.deletedChapters(book.chaptersDir());
        } catch (UncheckedIOException e) {
            errors.report("Cannot read the deleted chapters", e);
            return;
        }

        DeletedChaptersDialog.show(window.get(), deleted).ifPresent(chapter -> restore(book, repository, chapter));
    }

    /** Writes {@code deleted} back with its last text, and commits it. Its history goes on from before the deletion. */
    private void restore(BookFolder book, BookRepository repository, DeletedChapter deleted) {

        if (!readyForCommit.test("Restoring a chapter is recorded as a commit of its own"))
            return;

        Chapter restored;
        try {
            restored = book.restoreChapter(HistoryPane.chapterTitle(deleted.path()), repository.contentOf(deleted));
        } catch (IllegalArgumentException | UncheckedIOException | GitCommandException e) {
            errors.report("Cannot restore chapter", e);
            return;
        }

        // The notes are offered back on the notes thread; if they come back, the chapter's board shows them at once.
        restoreNotes.accept(restored.title());
        showRestored.accept(restored);
        gitControls.commitChange("Restore chapter \"%s\"".formatted(restored.title()), List.of(restored.file()));
    }
}
