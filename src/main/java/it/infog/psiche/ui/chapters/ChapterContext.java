package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.markdown.TextCounter;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.git.HistoryPane;
import it.infog.psiche.ui.git.RevisionDialog;
import it.infog.psiche.ui.notes.DetachedNotes;
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.preview.PreviewPane;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.SetChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static it.infog.psiche.ui.common.Controls.button;
import static it.infog.psiche.ui.common.Controls.hSpacer;
import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * The open book's chapters: the chapter list with its menus, opening a chapter in the editor, and keeping the preview,
 * notes, history and status bar in step with it. Creating, renaming and deleting a chapter are left to
 * {@link ChapterChanges}, exporting one to {@link Exports}, restoring a deleted one to {@link DeletedChapters}, and
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
    private final GitControls gitControls;
    private final StatusBar statusBar;
    private final UnsavedChanges unsavedChanges;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final VBox sidebar;

    private final Exports exports;
    private final ChapterChanges changes;
    private final DeletedChapters deletedChapters;
    private final DetachedNotes detachedNotes;
    private final ChapterRevisions revisions;

    // Counting parses the whole chapter, so wait for a pause in typing as the preview does, but less long.
    private final PauseTransition countDelay = new PauseTransition(Duration.millis(300));

    private final BooleanProperty bookOpen = new SimpleBooleanProperty();
    private final BooleanProperty hasRepository = new SimpleBooleanProperty();
    // True while notes are kept for no chapter of the book, which shows Detached notes.
    private final BooleanProperty hasOrphans = new SimpleBooleanProperty();

    private BookFolder book;
    private BookRepository repository;
    private NoteStore notes;

    public ChapterContext(ChapterEditor chapterEditor, PreviewPane preview, HistoryPane history, NotesPane notesPane,
                          GitControls gitControls, StatusBar statusBar, UnsavedChanges unsavedChanges, Exports exports,
                          ErrorReporter errors, Supplier<Window> window) {

        this.chapterEditor = chapterEditor;
        this.preview = preview;
        this.history = history;
        this.notesPane = notesPane;
        this.gitControls = gitControls;
        this.statusBar = statusBar;
        this.unsavedChanges = unsavedChanges;
        this.exports = exports;
        this.errors = errors;
        this.window = window;

        revisions = new ChapterRevisions(gitControls, chapterList::refresh);
        changes = new ChapterChanges(chapterEditor, notesPane, gitControls, statusBar, errors, window);
        detachedNotes = new DetachedNotes(notesPane, statusBar, errors, window, this::checkOrphans);
        deletedChapters = new DeletedChapters(gitControls, errors, window,
                why -> changes.readyForCommit(repository, why, Optional.empty()),
                title -> detachedNotes.offerRestore(notes, title), this::showChapter);
        sidebar = buildSidebar();

        countDelay.setOnFinished(event -> countChapter());

        statusBar.alignCountsTo(chapterEditor.textMarginProperty());

        // listeners
        chapterEditor.textProperty().addListener((obs, old, text) -> followEditor(text));
        chapterEditor.currentPositionProperty().addListener(
                (obs, old, position) -> preview.followPosition(position.intValue(), chapterEditor.isReading()));
        chapterEditor.dirtyProperty().addListener((obs, old, isDirty) -> updateChapterState());
        // Cells read the unsaved set when they are drawn, so redraw them when a chapter gains or loses its draft; a
        // chapter loses it when saved, which can bring it back to its last revision.
        chapterEditor.unsavedChapters().addListener((SetChangeListener<Chapter>) change -> {
            chapterList.refresh();
            revisions.check();
        });
        notesPane.setOnBinned(this::checkOrphans);
    }

    /** The chapter list with New chapter above it, and Deleted chapters and Detached notes below. */
    public VBox sidebar() {

        return sidebar;
    }

    /** Lists the chapters of {@code book} and opens the first; from now on an empty chapter shows as a blank page. */
    public void show(BookFolder book, BookRepository repository) {

        this.book = book;
        this.repository = repository;
        notes = new NoteStore(book.notesDir());
        notesPane.show(notes, null);
        chapterEditor.hideWelcome();

        bookOpen.set(true);
        hasRepository.set(repository != null);
        chapterEditor.setBookLanguage(language(book));
        revisions.show(repository);

        refresh();
        chapterList.getSelectionModel().selectFirst();
    }

    /** Empties the list and the editor, discarding unsaved changes; callers save first if they need to. */
    public void clear() {

        chapterEditor.close();
        history.show(null, null);
        notesPane.show(null, null);

        // Closing a saved chapter leaves the dirty flag unchanged, so its listener would not clear the status bar.
        updateChapterState();
        chapterList.getItems().clear();

        book = null;
        repository = null;
        notes = null;
        bookOpen.set(false);
        hasRepository.set(false);
        hasOrphans.set(false);
        revisions.show(null);
        chapterEditor.setBookLanguage(null);
    }

    /** Checks the spelling in {@code tag}, the open book's language, after the author changed it. */
    public void setBookLanguage(String tag) {

        chapterEditor.setBookLanguage(tag);
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
        checkOrphans();
        revisions.check();
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

    /** The language {@code book} is written in, as its properties say; none if they cannot be read. */
    private String language(BookFolder book) {

        try {
            return book.properties().language();
        } catch (UncheckedIOException e) {
            errors.report("Cannot read the book's language, so its spelling is not checked", e);
            return null;
        }
    }

    // Layout

    private VBox buildSidebar() {

        //chapter list
        chapterList.setCellFactory(list -> new ChapterCell(chapterEditor.unsavedChapters(), revisions,
                this::chapterMenu));

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

        // Notes are not in Git, so their bin works without it; it is there only while it holds something.
        Button detachedNotesButton = button("Detached notes", this::showDetachedNotes);
        detachedNotesButton.visibleProperty().bind(hasOrphans);
        detachedNotesButton.managedProperty().bind(hasOrphans);

        // The buttons span the column, inside the footer's padding.
        deleted.setMaxWidth(Double.MAX_VALUE);
        detachedNotesButton.setMaxWidth(Double.MAX_VALUE);

        VBox footer = new VBox(deleted, detachedNotesButton);
        footer.getStyleClass().add("sidebar-footer");

        VBox box = new VBox(header, chapterList, footer);
        box.getStyleClass().add("sidebar");
        VBox.setVgrow(chapterList, Priority.ALWAYS);
        return box;
    }

    /**
     * Save, Rename, Delete and Export for the chapter in {@code cell}. Save writes the chapter's unsaved changes,
     * whether it is open or not. Rename and Delete make a commit of their own, so they need the book's repository and
     * wait while another Git command runs; Save and Export need neither.
     */
    private ContextMenu chapterMenu(ListCell<Chapter> cell) {

        MenuItem save = menuItem("Save", () -> chapterEditor.save(cell.getItem()));
        MenuItem rename = menuItem("Rename", () -> renameChapter(cell.getItem()));
        MenuItem delete = menuItem("Delete", () -> deleteChapter(cell.getItem()));
        MenuItem needsGit = new MenuItem("Needs a Git repository");
        needsGit.setDisable(true);

        Menu export = new Menu("Export");
        for (ExportFormat format : Exports.CHAPTER_FORMATS)
            export.getItems().add(menuItem(format.label(),
                    () -> exports.exportChapter(book, cell.getItem(), format)));

        ContextMenu menu = new ContextMenu(save, new SeparatorMenuItem(), rename, delete, needsGit,
                new SeparatorMenuItem(), export);
        menu.setOnShowing(event -> {
            save.setDisable(!chapterEditor.unsavedChapters().contains(cell.getItem()));
            rename.setDisable(!hasRepository.get() || gitControls.busyProperty().get());
            delete.setDisable(rename.isDisable());
            needsGit.setVisible(!hasRepository.get());
        });

        return menu;
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
            updateChapterState();
            countChapter();
        }
    }

    /** Renames {@code chapter}; the open chapter is reopened under its new name, with no draft to carry over. */
    private void renameChapter(Chapter chapter) {

        boolean wasOpen = chapterEditor.isOpen(chapter);
        changes.rename(book, repository, notes, chapter).ifPresent(renamed -> {
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
        updateChapterState();
        refresh();
        if (!chapterList.getItems().isEmpty())
            chapterList.getSelectionModel().select(Math.min(index, chapterList.getItems().size() - 1));
    }

    /** Lists the chapters again and opens {@code chapter}, one just created, renamed or restored. */
    private void showChapter(Chapter chapter) {

        refresh();
        chapterList.getSelectionModel().select(chapter);
    }

    // Detached notes

    private void showDetachedNotes() {

        detachedNotes.show(notes, chapterTitles());
    }

    /**
     * Shows Detached notes while notes are kept for no chapter of the book; the answer, read on the notes thread, is
     * dropped if another book has opened meanwhile.
     */
    private void checkOrphans() {

        if (notes == null) {
            hasOrphans.set(false);
            return;
        }

        NoteStore checked = notes;
        detachedNotes.hasAny(notes, chapterTitles(), has -> {
            if (notes == checked)
                hasOrphans.set(has);
        });
    }

    private List<String> chapterTitles() {

        return chapterList.getItems().stream().map(Chapter::title).toList();
    }

    // Keeping the parts in step

    /**
     * Re-renders the preview and recounts the chapter as the author types; with no chapter open the editor is empty,
     * and so is the preview.
     */
    private void followEditor(String text) {

        if (chapterEditor.chapter().isPresent()) {
            preview.renderLater(text, book.chaptersDir());
            countDelay.playFromStart();
        } else {
            preview.clearLater();
            countDelay.stop();
        }
    }

    /** Shows the open chapter's characters, words and pages in the status bar. */
    private void countChapter() {

        countDelay.stop();
        if (chapterEditor.chapter().isPresent())
            statusBar.showCount(TextCounter.count(chapterEditor.text()));
    }

    private void updateChapterState() {

        chapterEditor.chapter().ifPresentOrElse(
                chapter -> statusBar.showChapter(chapter.title(), chapterEditor.isDirty()),
                statusBar::clearChapter);
    }
}
