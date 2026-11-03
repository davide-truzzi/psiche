package it.infog.psiche.ui;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.markdown.MarkdownRenderer;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.ui.book.BookContext;
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
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.notes.NotesTransfer;
import it.infog.psiche.ui.preferences.PreferencesDialog;
import it.infog.psiche.ui.preview.PreviewPane;
import javafx.beans.binding.Bindings;
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
    private final Exports exports;
    private final ChapterContext chapters;
    private final BookContext books;
    private final TabPane chapterTabs;

    /**
     * @param renderers render the preview's pages in a theme
     * @param welcome   Markdown shown in the editor and rendered in the preview until a book is opened
     * @param browser   opens a web or mail link from the preview in the system's default application
     * @param settings  where the app remembers what outlasts a session, such as the books opened lately
     */
    BookWorkspace(Function<PreviewTheme, MarkdownRenderer> renderers, String welcome, Consumer<String> browser,
                  Settings settings) {

        this.settings = settings;
        this.browser = browser;
        Preferences preferences = settings.preferences();
        preview = new PreviewPane(renderers, preferences.previewTheme(), preferences.previewFontSize(),
                settings.webViewFolder(), browser);
        history = new HistoryPane(this::reportError, this::showRevision);
        chapterEditor = new ChapterEditor(statusBar, this::reportError, welcome);
        chapterEditor.setFontSize(preferences.editorFontSize());
        unsavedChanges = new UnsavedChanges(chapterEditor, this::window);
        gitControls = new GitControls(() -> unsavedChanges.settle("Save them before committing?"),
                this::committed, statusBar, this::reportError);
        Runnable keepNotesOutOfGit = () -> gitControls.ignore("notes/", "Keep chapter notes out of Git");
        notes = new NotesPane(statusBar, this::reportError, keepNotesOutOfGit::run, browser);
        notesTransfer = new NotesTransfer(notes, statusBar, this::reportError, this::window, keepNotesOutOfGit);
        exports = new Exports(chapter -> chapterEditor.unsavedChapters().contains(chapter), unsavedChanges::settle,
                statusBar, this::reportError, this::window);
        chapters = new ChapterContext(chapterEditor, preview, history, notes, gitControls, statusBar, unsavedChanges,
                exports, this::reportError, this::window);
        chapterTabs = chapterTabs();
        books = new BookContext(chapters, gitControls, statusBar, unsavedChanges, exports, notesTransfer,
                this::reportError, this::window,
                () -> Animations.reveal(chapters.sidebar(), chapterEditor.node(), chapterTabs), settings);

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

    /** Lets the author save or discard unsaved chapters before the window closes; false means stay open. */
    boolean confirmClose() {

        return unsavedChanges.settle("Save them before closing Psiche Writer?");
    }

    // Layout

    /** The app's mark, the open book's title, the Book menu, Preferences, and on the right the Git controls. */
    private Node toolbar() {

        Node mark = Logo.mark(24);
        mark.getStyleClass().add("toolbar-mark");

        Button preferences = button("Preferences", this::editPreferences);

        HBox bar = new HBox(mark, books.title(), bookMenuButton(), preferences, hSpacer(), gitControls);
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

        ContextMenu menu = new ContextMenu(create, open, recent.menu(), new SeparatorMenuItem(), saveAll,
                new SeparatorMenuItem(), properties, export, new SeparatorMenuItem(), exportNotes, importNotes);
        menu.setOnShowing(event -> recent.refresh(gitControls.busyProperty().get()));

        Button book = new Button("Book");
        book.setOnAction(event -> menu.show(book, Side.BOTTOM, 0, 4));

        return book;
    }

    /** The preview of the open chapter, its notes, and its commit history. */
    private TabPane chapterTabs() {

        Tab previewTab = new Tab("Preview", preview);
        preview.showingProperty().bind(previewTab.selectedProperty());

        // "Notes (3)", in red-pencil while a note carries the Duplicate on Import tag; disabled with no book open.
        Tab notesTab = new Tab("Notes", notes);
        notes.showingProperty().bind(notesTab.selectedProperty());
        notesTab.textProperty().bind(notes.titleBinding());
        notesTab.disableProperty().bind(notes.availableProperty().not());
        notes.duplicatesProperty().addListener((obs, had, has) -> {
            if (has)
                notesTab.getStyleClass().add("notes-duplicates");
            else
                notesTab.getStyleClass().remove("notes-duplicates");
        });

        Tab historyTab = new Tab("History", history);
        history.showingProperty().bind(historyTab.selectedProperty());

        TabPane tabs = new TabPane(previewTab, notesTab, historyTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("chapter-tabs");

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

    /** Remembers {@code preferences}, and shows the editor and the preview as they ask. */
    private void applyPreferences(Preferences preferences) {

        settings.savePreferences(preferences);
        preview.setTheme(preferences.previewTheme());
        preview.setFontSize(preferences.previewFontSize());
        chapterEditor.setFontSize(preferences.editorFontSize());
        statusBar.info("Preferences saved");
    }

    // Shared by the parts

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
        gitControls.shutdown();
        history.shutdown();
        exports.shutdown();
    }
}
