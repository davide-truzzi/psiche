package it.infog.psiche.ui;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.markdown.MarkdownRenderer;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.ui.book.BookContext;
import it.infog.psiche.ui.book.BookMenu;
import it.infog.psiche.ui.book.BookDrop;
import it.infog.psiche.ui.book.ProjectTransfer;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.chapters.UnsavedChanges;
import it.infog.psiche.ui.common.Animations;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.KeptSizeSplit;
import it.infog.psiche.ui.common.Logo;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.common.WindowThemes;
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
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
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
    private final Keychain keychain;
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
        this.keychain = keychain;
        Preferences preferences = settings.preferences();
        preview = new PreviewPane(renderers, preferences.previewTheme(), preferences.previewFontSize(),
                settings.webViewFolder());
        history = new HistoryPane(this::reportError, this::showRevision);
        chapterEditor = new ChapterEditor(statusBar, this::reportError, welcome, settings::ownWords,
                settings::addOwnWord);
        showPreferences(preferences);
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
        chapterTabs = ChapterTabs.of(preview, notes, media, history);
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
        setCenter(sidebarAndPages());
        setBottom(statusBar);
        freezeWhileTransferring();
        BookDrop.install(this, books,
                () -> !projectTransfer.busyProperty().get() && !gitControls.busyProperty().get());
    }

    /** Opens the book in {@code directory}, discarding any unsaved changes; callers settle them first. */
    void openBook(Path directory) {

        books.openBook(directory);
    }

    /** The window's title, which names the open book. */
    StringExpression windowTitle() {

        return books.windowTitle();
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
     * Synchronize too during a transfer. The Book menu waits too; see {@link BookMenu}.
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

        Button book = BookMenu.button(books, chapterEditor, gitControls, notesTransfer, projectTransfer);

        HBox bar = new HBox(mark, books.title(), book, preferences, support, hSpacer(), gitControls);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    /** The chapter list beside the pages, the divider between them dragged by the author, as wide as it was left. */
    private Node sidebarAndPages() {

        SplitPane split = KeptSizeSplit.of(Orientation.HORIZONTAL, chapters.sidebar(), pages(),
                settings.sidebarWidth(), settings::saveSidebarWidth);
        split.getStyleClass().add("sidebar-split");

        return split;
    }

    private Node pages() {

        SplitPane pages = new SplitPane(chapterEditor.node(), chapterTabs);
        pages.getStyleClass().add("pages");
        pages.setDividerPositions(0.5);

        return pages;
    }

    /** Edits the preferences; each time the author applies or saves them, they are remembered and take effect. */
    private void editPreferences() {

        PreferencesDialog.show(window(), settings.preferences(), browser, keychain, this::applyPreferences);
    }

    /**
     * Remembers {@code preferences}, and shows the window, the editor and the preview as they ask. If they cannot be
     * saved, they still apply until the app closes, and the status bar says so.
     */
    private void applyPreferences(Preferences preferences) {

        boolean saved = settings.savePreferences(preferences);
        if (WindowThemes.apply(getScene(), preferences.windowTheme()))
            chapterEditor.layOutAfterRestyle();
        showPreferences(preferences);
        if (saved)
            statusBar.info("Preferences saved");
        else
            statusBar.error("Cannot save the preferences: they last until Psiche Writer closes");
    }

    /**
     * Shows the preview and the editor as {@code preferences} ask, as the workspace is built and each time they are
     * applied; the window's theme waits for the window, see {@link WriterApp}.
     */
    private void showPreferences(Preferences preferences) {

        preview.setTheme(preferences.previewTheme());
        preview.setFontSize(preferences.previewFontSize());
        chapterEditor.setFontSize(preferences.editorFontSize());
        chapterEditor.setLineNumbersAlways(preferences.alwaysShowLineNumbers());
        chapterEditor.setSpelling(preferences.checkSpelling(), preferences.strictSpelling());
        chapterEditor.setHighlightRepetitions(preferences.highlightRepetitions());
        chapterEditor.setEditorMode(preferences.editorMode());
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
