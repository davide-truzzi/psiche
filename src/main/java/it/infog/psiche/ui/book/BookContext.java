package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.ChapterWatcher;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.GitCommandException;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.chapters.UnsavedChanges;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.notes.NotesTransfer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Label;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The open book: opens, creates and closes books with their Git repository and chapter watcher, edits the book's
 * properties and how its pages are counted, and hands the book to the {@link ChapterContext} for its chapters.
 */
public final class BookContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BookContext.class);
    private static final Duration WATCH_RETRY = Duration.minutes(1);

    private final Label bookTitle = new Label("No book open");
    private final ReadOnlyBooleanWrapper bookOpen = new ReadOnlyBooleanWrapper();
    private final ChapterContext chapters;
    private final GitControls gitControls;
    private final StatusBar statusBar;
    private final UnsavedChanges unsavedChanges;
    private final Exports exports;
    private final NotesTransfer notesTransfer;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable onOpened;
    private final Settings settings;
    // Runs while the chapters folder cannot be watched; see watch().
    private final Timeline watchRetry = new Timeline(new KeyFrame(WATCH_RETRY, event -> retryWatch()));

    private BookFolder book;
    private BookRepository repository;
    private ChapterWatcher watcher;
    private String watchFailure;

    /** @param onOpened runs each time a book has opened, to reveal its parts */
    /** @param settings where the books opened lately are remembered */
    public BookContext(ChapterContext chapters, GitControls gitControls, StatusBar statusBar,
                       UnsavedChanges unsavedChanges, Exports exports, NotesTransfer notesTransfer,
                       ErrorReporter errors, Supplier<Window> window, Runnable onOpened, Settings settings) {

        this.chapters = chapters;
        this.gitControls = gitControls;
        this.statusBar = statusBar;
        this.unsavedChanges = unsavedChanges;
        this.exports = exports;
        this.notesTransfer = notesTransfer;
        this.errors = errors;
        this.window = window;
        this.onOpened = onOpened;
        this.settings = settings;

        bookTitle.getStyleClass().add("book-title");
        statusBar.setOnPageMeasureChosen(this::choosePageMeasure);
        watchRetry.setCycleCount(Animation.INDEFINITE);
    }

    /** The open book's title, for the toolbar. */
    public Label title() {

        return bookTitle;
    }

    /** True while a book is open. */
    public ReadOnlyBooleanProperty bookOpenProperty() {

        return bookOpen.getReadOnlyProperty();
    }

    /**
     * The books opened lately, the latest first, the open one left out. A book whose folder is gone stays listed,
     * as it may be on a drive that is not plugged in.
     */
    List<Path> recentBooks() {

        Optional<Path> open = Optional.ofNullable(book).map(opened -> realPath(opened.root()));
        return settings.recentBooks().books().stream()
                .filter(recent -> open.isEmpty() || !realPath(recent).equals(open.get()))
                .toList();
    }

    /** Opens a book from the recent list, once unsaved changes are settled. */
    void openRecent(Path directory) {

        if (!unsavedChanges.settle("Save them before opening another book?"))
            return;

        openBook(directory);
    }

    void clearRecentBooks() {

        settings.clearRecentBooks();
    }

    public void chooseBook() {

        if (!unsavedChanges.settle("Save them before opening another book?"))
            return;

        Dialogs.chooseDirectory(window.get(), "Open book folder", booksDirectory()).ifPresent(this::openBook);
    }

    public void createBook() {

        if (!unsavedChanges.settle("Save them before creating a new book?"))
            return;

        Optional<String> title = Dialogs.askTitle(window.get(), "New book", "", "Create");
        if (title.isEmpty())
            return;

        Optional<Path> parent = Dialogs.chooseDirectory(
                window.get(), "Choose where to create \"%s\"".formatted(title.get().strip()), booksDirectory());
        if (parent.isEmpty())
            return;

        BookFolder created;

        try {
            Preferences preferences = settings.preferences();
            created = BookFolder.create(parent.get(), title.get(), preferences.newBookLanguage(),
                    preferences.newBookAuthor());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            errors.report("Cannot create book", e);
            return;
        }

        try {
            BookRepository.init(created.root());
        } catch (GitCommandException e) {
            // The book itself is fine; it opens without Git and the author can run 'git init' later.
            errors.report("Created " + created.title() + " without a Git repository", e);
        }

        openBook(created.root());
    }

    /** Edits the open book's properties and cover; they are written as soon as the author saves the form. */
    public void editProperties() {

        BookProperties current;
        try {
            current = book.properties();
        } catch (UncheckedIOException e) {
            errors.report("Cannot read the book properties", e);
            return;
        }

        Optional<Path> cover = book.cover();
        BookPropertiesDialog.show(window.get(), current, cover).ifPresent(edit -> {
            try {
                book.saveProperties(edit.properties());
                if (edit.cover().isEmpty())
                    book.removeCover();
                else if (!edit.cover().equals(cover))
                    book.setCover(edit.cover().get());
            } catch (IllegalArgumentException | UncheckedIOException e) {
                errors.report("Cannot save the book properties", e);
                return;
            }

            bookTitle.setText(displayTitle());
            statusBar.setPageMeasure(pageMeasure());
            statusBar.info("Book properties saved");
        });
    }

    /** Exports the whole open book to {@code format}, one of {@link Exports#BOOK_FORMATS}. */
    public void exportBook(ExportFormat format) {

        exports.exportBook(book, format);
    }

    /** Writes the open book's notes to a zip, to move them to another computer or keep a copy. */
    public void exportNotes() {

        notesTransfer.exportNotes(book);
    }

    /** Merges the notes in a zip into the open book's, and lists the chapters again for Deleted notes to follow. */
    public void importNotes() {

        notesTransfer.importNotes(book, chapters::refresh);
    }

    /** Opens the book in {@code directory}, discarding any unsaved changes; callers settle them first. */
    public void openBook(Path directory) {

        BookFolder opened;
        try {
            opened = BookFolder.open(directory);
        } catch (IllegalArgumentException e) {
            errors.report("Cannot open book", e);
            return;
        }

        closeBook();

        book = opened;
        bookOpen.set(true);
        bookTitle.setText(displayTitle());
        statusBar.setPageMeasure(pageMeasure());
        try {
            repository = BookRepository.open(book.root());
            gitControls.setRepository(repository);
            statusBar.info("Opened " + book.title());
        } catch (GitCommandException e) {
            // Not fatal: the book can be edited without Git, so flag it in the status bar instead of an alert.
            statusBar.error(e.getMessage());
        }
        watch();
        chapters.show(book, repository);
        settings.bookOpened(book.root());
        onOpened.run();
    }

    /**
     * Refreshes the chapter list while the open book's chapters folder is not watched, as the author may be back
     * from adding chapters with another program. Called when the window gains focus.
     */
    public void refreshUnwatched() {

        if (book != null && watcher == null)
            chapters.refresh();
    }

    /**
     * Watches the open book's chapters folder, so chapters added or removed by other programs appear at once. The
     * system may have no watch to spare, as it gives each user only so many: then the author is told, and the watch
     * is tried again every minute until it starts or the book closes.
     */
    private void watch() {

        if (startWatcher())
            return;

        statusBar.error("Cannot watch the chapters folder for changes by other programs: " + watchFailure
                + ". Trying again every minute");
        watchRetry.playFromStart();
    }

    /** One more try at watching, while the first has failed; once it works, catches up on what was missed. */
    private void retryWatch() {

        if (book == null || !startWatcher())
            return;

        watchRetry.stop();
        chapters.refresh();
        statusBar.info("Watching the chapters folder again");
    }

    private boolean startWatcher() {

        try {
            watcher = new ChapterWatcher(book, () -> Platform.runLater(chapters::refresh));
            return true;
        } catch (UncheckedIOException e) {
            log.warn("Cannot watch the chapters of {}", book.root(), e);
            watchFailure = Optional.ofNullable(e.getCause().getMessage()).orElse("the system refused");
            return false;
        }
    }

    /** Releases the open book, if any, discarding unsaved changes; callers save first if they need to. */
    private void closeBook() {

        watchRetry.stop();
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }

        chapters.clear();

        if (repository != null) {
            repository.close();
            repository = null;
        }

        gitControls.setRepository(null);
        bookOpen.set(false);
    }

    /** The title from the book's properties, or the folder's name if they cannot be read. */
    private String displayTitle() {

        try {
            return book.properties().title();
        } catch (UncheckedIOException e) {
            log.warn("Cannot read the properties of {}", book.root(), e);
            return book.title();
        }
    }

    /**
     * Where to start choosing a book folder: the folder holding the open book, or else the latest book still there,
     * where the author most likely keeps other books; null to let the dialog choose.
     */
    private Path booksDirectory() {

        if (book != null)
            return book.root().getParent();

        // With no book open, start next to the latest book that is still there.
        return settings.recentBooks().books().stream()
                .filter(Files::isDirectory)
                .findFirst()
                .map(Path::getParent)
                .orElse(null);
    }

    /** {@code path} as the file system knows it, so a book reached through a link matches; as given if it is gone. */
    private static Path realPath(Path path) {

        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** The open book's page measure; the default one if the properties cannot be read. */
    private PageMeasure pageMeasure() {

        try {
            return book.pageMeasure();
        } catch (UncheckedIOException e) {
            log.warn("Cannot read the page measure of {}", book.root(), e);
            return PageMeasure.STANDARD;
        }
    }

    /** Counts pages by {@code measure} and keeps it in the book's properties, so the book remembers it. */
    private void choosePageMeasure(PageMeasure measure) {

        if (book == null)
            return;

        try {
            book.savePageMeasure(measure);
        } catch (UncheckedIOException e) {
            errors.report("Cannot save how pages are counted", e);
        }

        statusBar.setPageMeasure(measure);
        statusBar.info("Pages counted as " + measure.label());
    }

    @Override
    public void close() {

        closeBook();
    }
}
