package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.exceptions.GitCommandException;
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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Label;
import javafx.stage.Window;
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
    private static final String NO_BOOK = "No book open";

    private final Label bookTitle = new Label(NO_BOOK);
    private final ReadOnlyBooleanWrapper bookOpen = new ReadOnlyBooleanWrapper();
    private final ChapterContext chapters;
    private final GitControls gitControls;
    private final StatusBar statusBar;
    private final UnsavedChanges unsavedChanges;
    private final Exports exports;
    private final NotesTransfer notesTransfer;
    private final ProjectTransfer projectTransfer;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable onOpened;
    private final Settings settings;
    private final ChapterFolderWatch chapterWatch;

    private BookFolder book;
    private BookRepository repository;

    /** @param onOpened runs each time a book has opened, to reveal its parts */
    /** @param settings where the books opened lately are remembered */
    public BookContext(ChapterContext chapters, GitControls gitControls, StatusBar statusBar,
                       UnsavedChanges unsavedChanges, Exports exports, NotesTransfer notesTransfer,
                       ProjectTransfer projectTransfer, ErrorReporter errors, Supplier<Window> window,
                       Runnable onOpened, Settings settings) {

        this.chapters = chapters;
        this.gitControls = gitControls;
        this.statusBar = statusBar;
        this.unsavedChanges = unsavedChanges;
        this.exports = exports;
        this.notesTransfer = notesTransfer;
        this.projectTransfer = projectTransfer;
        this.errors = errors;
        this.window = window;
        this.onOpened = onOpened;
        this.settings = settings;

        chapterWatch = new ChapterFolderWatch(chapters, statusBar);

        bookTitle.getStyleClass().add("book-title");
        statusBar.setOnPageMeasureChosen(this::choosePageMeasure);
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
            chapters.setBookLanguage(edit.properties().language());
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

    /** Merges the notes in a zip into the open book's, and lists the chapters again for Detached notes to follow. */
    public void importNotes() {

        notesTransfer.importNotes(book, chapters::refresh);
    }

    /**
     * Writes the open book, as it is saved, to a Psiche project file; refused while a chapter has unsaved changes, as
     * the file takes the book from disk.
     */
    public void exportProject() {

        if (book == null || unsavedChanges.refuse("export the book"))
            return;

        projectTransfer.export(book);
    }

    /**
     * Unpacks a Psiche project file into a folder the author picks, then opens the book; if it replaces the open book,
     * that closes first.
     */
    public void importProject() {

        projectTransfer.importProject(booksDirectory(), this::isOpen, this::makeWayFor, this::openBook);
    }

    /** Closes the open book, once unsaved changes are settled, and shows the welcome as when the app starts. */
    public void closeOpenBook() {

        if (book == null || !unsavedChanges.settle("Save them before closing the book?"))
            return;

        String closed = book.title();
        closeBook();
        book = null;
        bookTitle.setText(NO_BOOK);
        statusBar.info("Closed " + closed);
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
        chapterWatch.start(book);
        chapters.show(book, repository);
        settings.bookOpened(book.root());
        onOpened.run();
    }

    /**
     * Refreshes the chapter list while the open book's chapters folder is not watched, as the author may be back
     * from adding chapters with another program. Called when the window gains focus.
     */
    public void refreshUnwatched() {

        if (chapterWatch.isMissingChanges())
            chapters.refresh();
    }

    /** True if {@code folder} is the open book's. */
    private boolean isOpen(Path folder) {

        return book != null && realPath(book.root()).equals(realPath(folder));
    }

    /**
     * Readies the open book for an imported one: closes it if the import replaces it, or else settles its unsaved
     * changes, as the imported book opens in its place; false if the author cancels.
     */
    private boolean makeWayFor(Path imported) {

        if (book == null)
            return true;

        if (!isOpen(imported))
            return unsavedChanges.settle("Save them before opening the imported book?");

        closeOpenBook();
        return book == null;
    }

    /** Releases the open book, if any, discarding unsaved changes; callers save first if they need to. */
    private void closeBook() {

        chapterWatch.close();
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
