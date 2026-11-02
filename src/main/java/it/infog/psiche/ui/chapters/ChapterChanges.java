package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.GitCommandException;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.notes.NotesPane;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Creating, renaming and deleting the book's chapters, after asking the author. A renamed or deleted chapter takes its
 * notes along, and the change is committed on its own, so the chapter's history follows it; for that, everything else
 * must be saved and committed first. Showing the change in the chapter list is left to the caller.
 */
final class ChapterChanges {

    private final ChapterEditor chapterEditor;
    private final NotesPane notesPane;
    private final GitControls gitControls;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;

    ChapterChanges(ChapterEditor chapterEditor, NotesPane notesPane, GitControls gitControls, StatusBar statusBar,
                   ErrorReporter errors, Supplier<Window> window) {

        this.chapterEditor = chapterEditor;
        this.notesPane = notesPane;
        this.gitControls = gitControls;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
    }

    /**
     * Creates a chapter under the title the author gives, after the book's {@code count} chapters.
     *
     * @return the chapter created, or empty if the author gave up or it could not be made
     */
    Optional<Chapter> create(BookFolder book, BookRepository repository, int count) {

        // Chapters are ordered by file name, so suggest the next number prefix; the author can change it.
        Optional<String> title = Dialogs.askTitle(window.get(), "New chapter", "%02d-".formatted(count + 1), "Create");
        if (title.isEmpty() || !confirmReusedName(book, repository, title.get()))
            return Optional.empty();

        Chapter created;
        try {
            created = book.createChapter(title.get());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            errors.report("Cannot create chapter", e);
            return Optional.empty();
        }

        statusBar.info("Created " + created.title());
        return Optional.of(created);
    }

    /**
     * Renames {@code chapter} under the title the author gives, moves its notes along, and commits the rename alone,
     * so its history follows it to the new name.
     *
     * @return the chapter under its new name, or empty if it kept its name
     */
    Optional<Chapter> rename(BookFolder book, BookRepository repository, NoteStore notes, Chapter chapter) {

        if (!readyForCommit(repository,
                "Renaming a chapter is recorded as a commit of its own, so its history follows it",
                Optional.of(chapter)))
            return Optional.empty();

        Optional<String> title = Dialogs.askTitle(window.get(), "Rename " + chapter.title(), chapter.title(), "Rename");
        if (title.isEmpty())
            return Optional.empty();

        Chapter renamed;
        try {
            renamed = book.renameChapter(chapter, title.get());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            errors.report("Cannot rename chapter", e);
            return Optional.empty();
        }
        if (renamed.equals(chapter))
            return Optional.empty();

        // The chapter's notes follow it, once the board has saved what it was holding; they are not in Git, so the
        // commit stays the rename alone.
        try {
            notesPane.settle();
            notes.rename(chapter.title(), renamed.title());
        } catch (UncheckedIOException e) {
            errors.report("Cannot move the notes of " + chapter.title(), e);
        }

        gitControls.commitChange("Rename chapter \"%s\" to \"%s\"".formatted(chapter.title(), renamed.title()),
                List.of(chapter.file(), renamed.file()));
        return Optional.of(renamed);
    }

    /**
     * Deletes {@code chapter} once the author confirms, keeps its notes apart, and commits the deletion so it can be
     * restored later.
     *
     * @return true if the chapter was deleted
     */
    boolean delete(BookFolder book, BookRepository repository, NoteStore notes, Chapter chapter) {

        if (!readyForCommit(repository,
                "Deleting a chapter is recorded as a commit of its own, so it can be restored later",
                Optional.of(chapter)))
            return false;

        ButtonType delete = new ButtonType("Delete", ButtonData.OK_DONE);
        Optional<ButtonType> choice = Dialogs.choose(window.get(), "Delete " + chapter.title() + "?",
                "You can restore it later from 'Deleted chapters'", delete, ButtonType.CANCEL);
        if (choice.filter(delete::equals).isEmpty())
            return false;

        try {
            book.deleteChapter(chapter);
        } catch (UncheckedIOException e) {
            errors.report("Cannot delete chapter", e);
            return false;
        }

        // Its notes are kept apart, under its name and the time, for Detached notes and for a restore.
        try {
            notesPane.settle();
            notes.binChapter(chapter.title());
        } catch (UncheckedIOException e) {
            errors.report("Cannot keep the notes of " + chapter.title(), e);
        }

        gitControls.commitChange("Delete chapter \"%s\"".formatted(chapter.title()), List.of(chapter.file()));
        return true;
    }

    /**
     * True if every chapter is saved, every change to the book committed, and {@code chapter}, the one about to
     * change, is in the last commit; so the change made now can be committed alone. Files Git does not track yet,
     * such as an editor's backup file, are left out of that commit and don't stand in the way. Otherwise tells the
     * author, after {@code why}, to save and commit first.
     */
    boolean readyForCommit(BookRepository repository, String why, Optional<Chapter> chapter) {

        boolean ready;
        try {
            ready = !chapterEditor.hasUnsavedChanges() && !repository.hasUncommittedChanges()
                    && chapter.map(Chapter::file).map(repository::isCommitted).orElse(true);
        } catch (GitCommandException e) {
            errors.report("Cannot read the state of the book", e);
            return false;
        }

        if (!ready)
            Dialogs.inform(window.get(), "Save and commit first",
                    why + ". Save your chapters and commit your changes, then try again.");

        return ready;
    }

    /**
     * Warns, before a new chapter takes a name an earlier chapter had, that the new chapter's history would show the
     * earlier one's versions, since Git keeps history by file name.
     *
     * @return false if the author chose not to create the chapter
     */
    private boolean confirmReusedName(BookFolder book, BookRepository repository, String title) {

        //reused test
        try {
            if (!(repository != null && repository.history().hasHistory(book.chapterFile(title))))
                return true;
        } catch (IllegalArgumentException | UncheckedIOException e) {
            // create reports a title it cannot use; a history that cannot be read is no reason to stop.
            return true;
        }

        String name = new Chapter(book.chapterFile(title)).title();
        ButtonType create = new ButtonType("Create anyway", ButtonData.OK_DONE);
        Optional<ButtonType> choice = Dialogs.choose(window.get(), "A chapter was called " + name + " before",
                ("It was deleted or renamed since. Git keeps a chapter's history by its file name, so the History "
                        + "of a new %s will also list the versions of the earlier one. If you meant to bring back a "
                        + "deleted chapter, use 'Deleted chapters' instead.").formatted(name), create,
                ButtonType.CANCEL);

        return choice.filter(create::equals).isPresent();
    }
}
