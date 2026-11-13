package it.infog.psiche.ui;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.markdown.MarkdownRenderer;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.enums.ChapterTab;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.ui.book.BookContext;
import it.infog.psiche.ui.book.BookDrop;
import it.infog.psiche.ui.book.ProjectTransfer;
import it.infog.psiche.ui.book.RecentBooksMenu;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.chapters.UnsavedChanges;
import it.infog.psiche.ui.common.Animations;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.Logo;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.git.HistoryPane;
import it.infog.psiche.ui.media.ImageInsertion;
import it.infog.psiche.ui.media.MediaPane;
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.notes.NotesTransfer;
import it.infog.psiche.ui.preferences.PreferencesDialog;
import it.infog.psiche.ui.preferences.SupportDialog;
import it.infog.psiche.ui.preview.PreviewPane;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static it.infog.psiche.ui.common.Controls.button;
import static it.infog.psiche.ui.common.Controls.hSpacer;
import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * The main window's content: toolbar, chapter list, editor beside the preview and history tabs, and status bar. It
 * lays the parts out and wires them together; the {@link BookContext} handles the open book and the
 * {@link ChapterContext} its chapters.
 */
public final class BookWorkspace extends BorderPane implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BookWorkspace.class);

    private final StatusBar statusBar = new StatusBar();
    private final Settings settings;
    private final Consumer<String> browser;
    private final PreviewPane preview;
    private final HistoryPane history;
    private final ChapterEditor chapterEditor;
    private final UnsavedChanges unsavedChanges;
    private final GitControls gitControls;
    private final NotesPane notes;
    private final NotesTransfer notesTransfer;
    private final ImageInsertion images;
    private final MediaPane media;
    private final ProjectTransfer projectTransfer;
    private final Exports exports;
    private final ChapterContext chapters;
    private final BookContext books;
    private final TabPane chapterTabs;
    private final OpeningTab openingTab;

    /**
     * @param renderers render the preview's pages in a theme
     * @param welcome   Markdown shown in the editor and rendered in the preview until a book is opened
     * @param browser   opens a web or mail link from the preview in the system's default application
     * @param settings  where the app remembers what outlasts a session, such as the books opened lately
     * @param keychain  where the tokens of the books' remotes are kept
     */
    BookWorkspace(Function<PreviewTheme, MarkdownRenderer> renderers, String welcome, Consumer<String> browser,
                  Settings settings, Keychain keychain) {

        this.settings = settings;
        this.browser = browser;
        Preferences preferences = settings.preferences();
        preview = new PreviewPane(renderers, preferences.previewTheme(), preferences.previewFontSize(),
                settings.webViewFolder());
        history = new HistoryPane(this::reportError, this::showRevision);
        chapterEditor = new ChapterEditor(statusBar, this::reportError, welcome, settings::ownWords,
                settings::addOwnWord);
        chapterEditor.setFontSize(preferences.editorFontSize());
        chapterEditor.setSpelling(preferences.checkSpelling(), preferences.strictSpelling());
        chapterEditor.setHighlightRepetitions(preferences.highlightRepetitions());
        chapterEditor.setEditorMode(preferences.editorMode());
        unsavedChanges = new UnsavedChanges(chapterEditor, this::window);
        gitControls = new GitControls(() -> unsavedChanges.settle("Save them before committing?"),
                this::committed, statusBar, this::reportError, keychain);
        Runnable keepNotesOutOfGit = () -> gitControls.ignore("notes/", "Keep chapter notes out of Git",
                "Notes are kept out of Git");
        notes = new NotesPane(statusBar, this::reportError, keepNotesOutOfGit::run, browser);
        notesTransfer = new NotesTransfer(notes, statusBar, this::reportError, this::window, keepNotesOutOfGit);
        images = new ImageInsertion(chapterEditor, statusBar, this::reportError, this::window);
        chapterEditor.setOnInsertImage(images::insert);
        chapterEditor.setOnModifyImage(images::modify);
        chapterEditor.setBrowser(browser);
        media = new MediaPane(chapterEditor, images, statusBar, this::reportError, this::window,
                settings.mediaPreviewHeight(), settings::saveMediaPreviewHeight);
        projectTransfer = new ProjectTransfer(statusBar, this::reportError, this::window, notes::settle);
        exports = new Exports(chapter -> chapterEditor.unsavedChapters().contains(chapter), unsavedChanges::settle,
                statusBar, this::reportError, this::window);
        chapters = new ChapterContext(chapterEditor, preview, history, notes, media, gitControls, statusBar,
                exports, this::reportError, this::window, settings.bookCountsShown(), settings::saveBookCountsShown);
        chapterTabs = chapterTabs();
        openingTab = new OpeningTab(chapterTabs, settings, statusBar);
        books = new BookContext(chapters, gitControls, statusBar, unsavedChanges, exports, notesTransfer,
                projectTransfer, this::reportError, this::window, this::bookOpened, settings, keychain);

        // Back in the window, the author may have added chapters elsewhere; only matters while they are not watched.
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::focusedProperty)
                .addListener((obs, was, focused) -> {
                    if (Boolean.TRUE.equals(focused)) {
                        books.refreshUnwatched();
                        // The watcher does not see commits, so one made outside the app shows only now.
                        chapters.checkRevisions();
                    }
                });

        // The welcome links to nothing on disk, so any directory serves as its base.
        preview.render(welcome, Path.of("").toAbsolutePath());

        getStyleClass().add("workspace");
        setTop(toolbar());
        setLeft(chapters.sidebar());
        setCenter(pages());
        setBottom(statusBar);
        freezeWhileTransferring();
        BookDrop.install(this, books,
                () -> !projectTransfer.busyProperty().get() && !gitControls.busyProperty().get());
    }

    /** Opens the book in {@code directory}, discarding any unsaved changes; callers settle them first. */
    void openBook(Path directory) {

        books.openBook(directory);
    }

    /** Saves the open chapter. @return false if it could not be written; the author has already been told why */
    boolean save() {

        return chapterEditor.save();
    }

    /** Saves every chapter with unsaved changes. @return false if one could not be written; the author was told why */
    boolean saveAll() {

        return chapterEditor.saveAll();
    }

    /**
     * Lets the author save or discard unsaved chapters before the window closes, and keep a transfer that runs going;
     * then synchronizes the open book if it does so automatically, and runs {@code close}. Nothing runs if the author
     * cancels.
     */
    void requestClose(Runnable close) {

        if (projectTransfer.confirmStop() && unsavedChanges.settle("Save them before closing Psiche Writer?"))
            gitControls.leave(close);
    }

    /**
     * Keeps the book from changing while a Psiche project is exported or imported, or Synchronize runs: the chapter
     * list, the notes, the images are disabled, and the open chapter can be read but not edited; Commit and
     * Synchronize too during a transfer. The Book menu waits too; see {@link #bookMenuButton}.
     */
    private void freezeWhileTransferring() {

        ReadOnlyBooleanProperty transferring = projectTransfer.busyProperty();
        BooleanBinding frozenBook = transferring.or(gitControls.synchronizingProperty());
        chapters.sidebar().disableProperty().bind(frozenBook);
        gitControls.disableProperty().bind(transferring);
        frozenBook.addListener((obs, was, frozen) -> {
            chapterEditor.setFrozen(frozen);
            notes.setFrozen(frozen);
            media.setFrozen(frozen);
        });
    }

    // Layout

    /**
     * The app's mark, the open book's title, the Book menu, Preferences, the lamp asking for support, and on the right
     * the Git controls.
     */
    private Node toolbar() {

        Node mark = Logo.mark(24);
        mark.getStyleClass().add("toolbar-mark");

        Button preferences = button("Preferences", this::editPreferences);

        Button support = SupportDialog.lampButton(() -> SupportDialog.show(window(), browser));

        HBox bar = new HBox(mark, books.title(), bookMenuButton(), preferences, support, hSpacer(), gitControls);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    /**
     * Book, which opens a menu of what acts on the whole book: create or open one, save every chapter, edit its
     * properties, export it, export or import its notes. Each chapter's own actions, Save among them, are in its
     * right-click menu.
     */
    private Button bookMenuButton() {

        MenuItem create = menuItem("New book", books::createBook);
        create.disableProperty().bind(gitControls.busyProperty());

        MenuItem open = menuItem("Open book", books::chooseBook);
        open.disableProperty().bind(gitControls.busyProperty());

        RecentBooksMenu recent = new RecentBooksMenu(books);

        // The shortcut is shown here and bound by WriterApp; a menu opened from a button does not bind it.
        MenuItem saveAll = menuItem("Save chapters", this::saveAll);
        saveAll.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN));
        saveAll.disableProperty().bind(Bindings.isEmpty(chapterEditor.unsavedChapters()));

        // A Git command, or notes on their way in or out, still need the book.
        MenuItem close = menuItem("Close book", books::closeOpenBook);
        close.disableProperty().bind(books.bookOpenProperty().not().or(gitControls.busyProperty())
                .or(notesTransfer.busyProperty()));

        MenuItem properties = menuItem("Book properties", books::editProperties);
        properties.disableProperty().bind(books.bookOpenProperty().not().or(gitControls.busyProperty()));

        Menu export = new Menu("Export");
        for (ExportFormat format : Exports.BOOK_FORMATS)
            export.getItems().add(menuItem(format.label(), () -> books.exportBook(format)));
        export.disableProperty().bind(books.bookOpenProperty().not());

        // Notes are not in Git: these move them to another computer, or keep a copy.
        MenuItem exportNotes = menuItem("Export notes", books::exportNotes);
        exportNotes.disableProperty().bind(books.bookOpenProperty().not().or(notesTransfer.busyProperty()));

        MenuItem importNotes = menuItem("Import notes", books::importNotes);
        importNotes.disableProperty().bind(exportNotes.disableProperty());

        // The whole book folder, notes and history included, to one file.
        MenuItem exportProject = menuItem("Export Psiche project", books::exportProject);
        exportProject.disableProperty().bind(exportNotes.disableProperty().or(gitControls.busyProperty()));

        // It may replace the open book, which then closes: as Close book, it waits for Git and the notes.
        MenuItem importProject = menuItem("Import Psiche project", books::importProject);
        importProject.disableProperty().bind(gitControls.busyProperty().or(notesTransfer.busyProperty()));

        ContextMenu menu = new ContextMenu(create, open, recent.menu(), close, new SeparatorMenuItem(), saveAll,
                new SeparatorMenuItem(), properties, export, new SeparatorMenuItem(), exportNotes, importNotes,
                new SeparatorMenuItem(), exportProject, importProject);
        menu.setOnShowing(event -> recent.refresh(gitControls.busyProperty().get()));

        Button book = new Button("Book");
        book.setOnAction(event -> menu.show(book, Side.BOTTOM, 0, 4));
        // Nothing in it may change the book while a Psiche project is exported or imported.
        book.disableProperty().bind(projectTransfer.busyProperty());

        return book;
    }

    /**
     * The preview of the open chapter, its notes, its images, and its commit history, each knowing which
     * {@link ChapterTab} it is; which shows as a book opens is pinned from their right-click menus, see
     * {@link OpeningTab}.
     */
    private TabPane chapterTabs() {

        Tab previewTab = new Tab("Preview", preview);
        previewTab.setUserData(ChapterTab.PREVIEW);
        preview.showingProperty().bind(previewTab.selectedProperty());

        // "Notes (3)", in red-pencil while a note carries the Duplicate on Import tag; disabled with no book open.
        Tab notesTab = new Tab("Notes", notes);
        notesTab.setUserData(ChapterTab.NOTES);
        notes.showingProperty().bind(notesTab.selectedProperty());
        notesTab.textProperty().bind(notes.titleBinding());
        notesTab.disableProperty().bind(notes.availableProperty().not());
        notes.duplicatesProperty().addListener((obs, had, has) -> {
            if (has)
                notesTab.getStyleClass().add("notes-duplicates");
            else
                notesTab.getStyleClass().remove("notes-duplicates");
        });

        // "Media (5)", in red-pencil while the chapter's folder holds an image its text does not show.
        Tab mediaTab = new Tab("Media", media);
        mediaTab.setUserData(ChapterTab.MEDIA);
        media.showingProperty().bind(mediaTab.selectedProperty());
        mediaTab.textProperty().bind(media.titleProperty());
        mediaTab.disableProperty().bind(media.availableProperty().not());
        media.notInChapterProperty().addListener((obs, had, has) -> {
            if (has)
                mediaTab.getStyleClass().add("media-not-in-chapter");
            else
                mediaTab.getStyleClass().remove("media-not-in-chapter");
        });

        Tab historyTab = new Tab("History", history);
        historyTab.setUserData(ChapterTab.HISTORY);
        history.showingProperty().bind(historyTab.selectedProperty());

        TabPane tabs = new TabPane(previewTab, notesTab, mediaTab, historyTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("chapter-tabs");
        // A click on a pin in the editor's margin shows its note.
        notes.boardPins().setOnReveal(() -> tabs.getSelectionModel().select(notesTab));

        return tabs;
    }

    private Node pages() {

        SplitPane pages = new SplitPane(chapterEditor.node(), chapterTabs);
        pages.getStyleClass().add("pages");
        pages.setDividerPositions(0.5);

        return pages;
    }

    /** Edits the preferences; each time the author applies or saves them, they are remembered and take effect. */
    private void editPreferences() {

        PreferencesDialog.show(window(), settings.preferences(), browser, this::applyPreferences);
    }

    /**
     * Remembers {@code preferences}, and shows the editor and the preview as they ask. If they cannot be saved, they
     * still apply until the app closes, and the status bar says so.
     */
    private void applyPreferences(Preferences preferences) {

        boolean saved = settings.savePreferences(preferences);
        preview.setTheme(preferences.previewTheme());
        preview.setFontSize(preferences.previewFontSize());
        chapterEditor.setFontSize(preferences.editorFontSize());
        chapterEditor.setSpelling(preferences.checkSpelling(), preferences.strictSpelling());
        chapterEditor.setHighlightRepetitions(preferences.highlightRepetitions());
        chapterEditor.setEditorMode(preferences.editorMode());
        if (saved)
            statusBar.info("Preferences saved");
        else
            statusBar.error("Cannot save the preferences: they last until Psiche Writer closes");
    }

    // Shared by the parts

    /** Shows the tab pinned for an opening book, and reveals the book's parts. */
    private void bookOpened() {

        openingTab.show();
        Animations.reveal(chapters.sidebar(), chapterEditor.node(), chapterTabs);
    }

    /** After a commit: the history has a revision more, and the chapters it recorded are as it left them. */
    private void committed() {

        history.refresh();
        chapters.checkRevisions();
    }

    /** Hands a past version the History tab has read to the chapters, which compare it with the saved chapter. */
    private void showRevision(Chapter chapter, FileRevision revision, Optional<String> text) {

        chapters.showRevision(chapter, revision, text);
    }

    /** Logs the error, flags it in the status bar and shows its message in an alert. */
    private void reportError(String header, Throwable error) {

        log.warn(header, error);
        statusBar.error(header);

        Dialogs.showError(window(), header, error);
    }

    private Window window() {

        return getScene().getWindow();
    }

    @Override
    public void close() {

        notes.settle();
        books.close();
        notes.shutdown();
        notesTransfer.shutdown();
        images.shutdown();
        media.shutdown();
        projectTransfer.shutdown();
        gitControls.shutdown();
        history.shutdown();
        exports.shutdown();
    }
}
