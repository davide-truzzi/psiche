package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.BookState;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.git.HistoryPane;
import it.infog.psiche.ui.git.RevisionDialog;
import it.infog.psiche.ui.media.DetachedImages;
import it.infog.psiche.ui.media.MediaPane;
import it.infog.psiche.ui.notes.DetachedNotes;
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.preview.PreviewPane;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.SetChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static it.infog.psiche.ui.common.Controls.button;
import static it.infog.psiche.ui.common.Controls.hSpacer;

/**
 * The open book's chapters: the chapter list with its menus, opening a chapter in the editor, and keeping the preview,
 * notes, history and status bar in step with it. A chapter's right-click menu is {@link ChapterMenu}'s, and what the
 * status bar and the book's counts tell is {@link ChapterStatus}'. Creating, renaming and deleting a chapter are left
 * to {@link ChapterChanges}, exporting one to {@link Exports}, restoring a deleted one to {@link DeletedChapters}, and
 * the notes left without a chapter to {@link DetachedNotes}.
 */
public final class ChapterContext {

    // The text area's own Undo shortcut, named in messages as the platform shows it (Ctrl+Z, or ⌘Z on macOS).
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);

    private final ListView<Chapter> chapterList = new ListView<>();
    private final ChapterEditor chapterEditor;
    private final PreviewPane preview;
    private final HistoryPane history;
    private final NotesPane notesPane;
    private final MediaPane mediaPane;
    private final GitControls gitControls;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final VBox sidebar;

    private final Exports exports;
    private final ChapterChanges changes;
    private final DeletedChapters deletedChapters;
    private final DetachedButtons detached;
    private final ChapterRevisions revisions;
    private final ChapterStatus status;

    private final BooleanProperty bookOpen = new SimpleBooleanProperty();
    private final BooleanProperty hasRepository = new SimpleBooleanProperty();

    private BookFolder book;
    private BookRepository repository;
    private NoteStore notes;

    /**
     * @param bookCountsShown    true to show the book's counts under the chapter list open, false collapsed
     * @param rememberBookCounts told when the author opens or collapses them, to keep it for the next start
     */
    public ChapterContext(ChapterEditor chapterEditor, PreviewPane preview, HistoryPane history, NotesPane notesPane,
                          MediaPane mediaPane, GitControls gitControls, StatusBar statusBar, Exports exports,
                          ErrorReporter errors, Supplier<Window> window, boolean bookCountsShown,
                          Consumer<Boolean> rememberBookCounts) {

        this.chapterEditor = chapterEditor;
        this.preview = preview;
        this.history = history;
        this.notesPane = notesPane;
        this.mediaPane = mediaPane;
        this.gitControls = gitControls;
        this.statusBar = statusBar;
        this.exports = exports;
        this.errors = errors;
        this.window = window;

        revisions = new ChapterRevisions(gitControls, chapterList::refresh);
        changes = new ChapterChanges(chapterEditor, notesPane, gitControls, statusBar, errors, window);
        detached = new DetachedButtons(
                new DetachedNotes(notesPane, statusBar, errors, window, () -> notes, this::chapterTitles),
                new DetachedImages(mediaPane, statusBar, errors, window, () -> book, this::chapterTitles));
        deletedChapters = new DeletedChapters(gitControls, errors, window,
                why -> changes.readyForCommit(repository, why, Optional.empty()),
                detached::offerRestore, this::showChapter);
        status = new ChapterStatus(chapterEditor, statusBar, bookCountsShown, rememberBookCounts);
        sidebar = buildSidebar();

        // listeners
        chapterEditor.textProperty().addListener((obs, old, text) -> followEditor(text));
        chapterEditor.currentPositionProperty().addListener(
                (obs, old, position) -> preview.followPosition(position.intValue(), chapterEditor.isReading()));
        chapterEditor.dirtyProperty().addListener((obs, old, isDirty) -> status.showChapter());
        // Cells read the unsaved set when they are drawn, so redraw them when a chapter gains or loses its draft; a
        // chapter loses it when saved, which can bring it back to its last revision and changes the book's counts.
        chapterEditor.unsavedChapters().addListener((SetChangeListener<Chapter>) change -> {
            chapterList.refresh();
            revisions.check();
            if (change.wasRemoved())
                countBook();
        });
        notesPane.setOnBinned(detached::check);
        mediaPane.setOnDetached(detached::check);
        notesPane.setChapters(this::chapterTitles);
    }

    /**
     * The chapter list with New chapter above it, and the book's counts, Deleted chapters, Detached notes and Detached
     * images below.
     */
    public VBox sidebar() {

        return sidebar;
    }

    /** Lists the chapters of {@code book} and opens the first; from now on an empty chapter shows as a blank page. */
    public void show(BookFolder book, BookRepository repository) {

        this.book = book;
        this.repository = repository;
        notes = new NoteStore(book.notesDir());
        // One state for the book, shared, as the editor and the notes keep theirs in the same file.
        BookState state = BookState.of(book);
        Optional<BookProperties> properties = properties(book);
        chapterEditor.setBookState(state);
        notesPane.setBook(properties.map(BookProperties::title).orElse(book.title()), state);
        notesPane.show(notes, null);
        chapterEditor.hideWelcome();

        bookOpen.set(true);
        hasRepository.set(repository != null);
        chapterEditor.setBookLanguage(properties.map(BookProperties::language).orElse(null));
        revisions.show(repository);

        refresh();
        chapterList.getSelectionModel().selectFirst();
    }

    /**
     * Empties the list and the editor, which shows the welcome again, discarding unsaved changes; callers save first
     * if they need to.
     */
    public void clear() {

        chapterEditor.close();
        chapterEditor.setBookState(null);
        chapterEditor.showWelcome();
        history.show(null, null);
        notesPane.setBook(null, null);
        notesPane.show(null, null);
        mediaPane.show(null, null);

        // Closing a saved chapter leaves the dirty flag unchanged, so its listener would not clear the status bar.
        status.clear();
        chapterList.getItems().clear();

        book = null;
        repository = null;
        notes = null;
        bookOpen.set(false);
        hasRepository.set(false);
        detached.check();
        revisions.show(null);
        chapterEditor.setBookLanguage(null);
    }

    /**
     * Takes the open book's {@code properties} after the author changed them: the spelling is checked in its language,
     * and the Notes tab names the book's board after its title.
     */
    public void setBookProperties(BookProperties properties) {

        chapterEditor.setBookLanguage(properties.language());
        notesPane.setBookTitle(properties.title());
    }

    /** Lists the book's chapters again, keeping the open one selected. */
    public void refresh() {

        if (book == null)
            return;

        List<Chapter> chapters;
        try {
            chapters = book.chapters();
        } catch (UncheckedIOException e) {
            errors.report("Cannot list chapters", e);
            return;
        }

        chapterList.getItems().setAll(chapters);
        chapterEditor.chapter().filter(chapters::contains).ifPresent(chapterList.getSelectionModel()::select);
        detached.check();
        revisions.check();
        countBook();
    }

    /** Reads again which chapters changed since the last revision, after a commit, in the app or outside it. */
    public void checkRevisions() {

        revisions.check();
    }

    /**
     * Shows how the saved chapter differs from a past version of it, as tracked changes. Restore puts that
     * version in the editor as an unsaved change, which Undo reverts like any edit.
     */
    public void showRevision(Chapter chapter, FileRevision revision, Optional<String> text) {

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

    /** The book's properties; none, and the author told, if they cannot be read: its spelling is then not checked. */
    private Optional<BookProperties> properties(BookFolder book) {

        try {
            return Optional.of(book.properties());
        } catch (UncheckedIOException e) {
            errors.report("Cannot read the book's properties, so its spelling is not checked", e);
            return Optional.empty();
        }
    }

    // Layout

    private VBox buildSidebar() {

        //chapter list
        ChapterMenu menu = new ChapterMenu(chapterEditor, exports, gitControls, hasRepository, () -> book,
                this::renameChapter, this::deleteChapter);
        chapterList.setCellFactory(list -> new ChapterCell(chapterEditor.unsavedChapters(), revisions, menu::of));

        //placeholder for empty list
        chapterList.setPlaceholder(new Label("No chapters yet."));

        // Re-selecting the open chapter, as refresh() does, must not reload it.
        chapterList.getSelectionModel().selectedItemProperty().addListener((obs, old, chapter) -> {
            if (chapter != null && !chapterEditor.isOpen(chapter)) {
                openChapter(chapter);
            }
        });

        //chapter list heading
        Label heading = new Label("CHAPTERS");
        heading.getStyleClass().add("section-heading");

        Button newChapter = button("New chapter", this::createChapter);
        newChapter.disableProperty().bind(bookOpen.not());

        HBox header = new HBox(heading, hSpacer(), newChapter);
        header.getStyleClass().add("sidebar-header");

        //chapter list footer
        Button deleted = button("Deleted chapters", () -> deletedChapters.show(book, repository));
        deleted.disableProperty().bind(hasRepository.not().or(gitControls.busyProperty()));

        // The buttons span the column, inside the footer's padding.
        deleted.setMaxWidth(Double.MAX_VALUE);

        VBox footer = new VBox(status.bookCounts(), deleted);
        footer.getChildren().addAll(detached.buttons());
        footer.getStyleClass().add("sidebar-footer");

        VBox box = new VBox(header, chapterList, footer);
        box.getStyleClass().add("sidebar");
        VBox.setVgrow(chapterList, Priority.ALWAYS);
        return box;
    }

    // Chapters

    private void createChapter() {

        changes.create(book, repository, chapterList.getItems().size()).ifPresent(this::showChapter);
    }

    private void openChapter(Chapter chapter) {

        if (chapterEditor.open(book, chapter)) {
            preview.render(chapterEditor.text(), book.chaptersDir());
            history.show(repository, chapter);
            notesPane.show(notes, chapter.title());
            mediaPane.show(book, chapter);
            status.showChapter();
            status.countChapter();
        }
    }

    /** Renames {@code chapter}; the open chapter is reopened under its new name, with no draft to carry over. */
    private void renameChapter(Chapter chapter) {

        boolean wasOpen = chapterEditor.isOpen(chapter);
        if (wasOpen)
            chapterEditor.rememberPlace();
        changes.rename(book, repository, notes, chapter).ifPresent(renamed -> {
            chapterEditor.placeRenamed(chapter, renamed);
            if (wasOpen)
                showChapter(renamed);
            else
                refresh();
        });
    }

    /** Deletes {@code chapter}; if it was open, it closes, and the one that took its place in the list opens. */
    private void deleteChapter(Chapter chapter) {

        int index = chapterList.getItems().indexOf(chapter);
        if (!changes.delete(book, repository, notes, chapter))
            return;

        if (!chapterEditor.isOpen(chapter)) {
            refresh();
            return;
        }

        chapterEditor.close();
        history.show(repository, null);
        notesPane.show(notes, null);
        mediaPane.show(null, null);
        status.showChapter();
        refresh();
        if (!chapterList.getItems().isEmpty())
            chapterList.getSelectionModel().select(Math.min(index, chapterList.getItems().size() - 1));
    }

    /** Lists the chapters again and opens {@code chapter}, one just created, renamed or restored. */
    private void showChapter(Chapter chapter) {

        refresh();
        chapterList.getSelectionModel().select(chapter);
    }

    // Notes

    /** The chapters' names, in their order: where notes can be moved or restored to. */
    private List<String> chapterTitles() {

        return chapterList.getItems().stream().map(Chapter::title).toList();
    }

    // Keeping the parts in step

    /**
     * Re-renders the preview and recounts the chapter as the author types; with no chapter open the editor is empty,
     * and so is the preview.
     */
    private void followEditor(String text) {

        if (chapterEditor.chapter().isPresent())
            preview.renderLater(text, book.chaptersDir());
        else
            preview.clearLater();
        status.countLater();
    }

    /** Counts the book's chapters again, as they are saved. */
    private void countBook() {

        if (book != null)
            status.countBook(book, chapterList.getItems());
    }
}
