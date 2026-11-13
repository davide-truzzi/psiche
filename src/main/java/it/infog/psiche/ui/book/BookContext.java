package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.BookState;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.media.ChapterMedia;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.chapters.UnsavedChanges;
import it.infog.psiche.ui.common.AppInfo;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.notes.NotesTransfer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The open book: opens, creates and closes books with their Git repository and chapter watcher, edits the book's
 * properties and how its pages are counted, and hands the book to the {@link ChapterContext} for its chapters. A book
 * with a remote is checked for newer revisions as it opens, and one that synchronizes automatically does so before
 * another takes its place or it closes.
 */
public final class BookContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BookContext.class);
    private static final String NO_BOOK = "No book open";
    private static final String CHOOSE_TITLE = "Choose the book's folder, the one holding chapters and media";
    private static final String NOT_A_BOOK_HEADER = "This is not a Psiche book";
    private static final String NOT_A_BOOK = "A book's folder is the one holding the chapters and media folders. "
            + "Choose that folder, or any folder inside it.";
    private static final ButtonType CHOOSE_AGAIN = new ButtonType("Choose again", ButtonData.OK_DONE);

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
    private final Keychain keychain;
    private final ChapterFolderWatch chapterWatch;
    private final BookLocations locations;
    private final BookCreation creation;

    private BookFolder book;
    private BookRepository repository;
    private BookState state;

    /**
     * @param onOpened runs each time a book has opened, to reveal its parts
     * @param settings where the books opened lately are remembered
     * @param keychain where the tokens of the books' remotes are kept
     */
    public BookContext(ChapterContext chapters, GitControls gitControls, StatusBar statusBar,
                       UnsavedChanges unsavedChanges, Exports exports, NotesTransfer notesTransfer,
                       ProjectTransfer projectTransfer, ErrorReporter errors, Supplier<Window> window,
                       Runnable onOpened, Settings settings, Keychain keychain) {

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
        this.keychain = keychain;

        chapterWatch = new ChapterFolderWatch(chapters, statusBar);
        locations = new BookLocations(settings);
        creation = new BookCreation(unsavedChanges, gitControls, locations, settings, errors, window, this::openBook);

        bookTitle.getStyleClass().add("book-title");
        statusBar.setOnPageMeasureChosen(this::choosePageMeasure);
        // An empty book that took its remote's is another book now: open it again, from its new files.
        gitControls.setOnBookTaken(() -> openBook(book.root()));
    }

    /** The open book's title, for the toolbar. */
    public Label title() {

        return bookTitle;
    }

    /** The window's title: the open book's, then the app's name; the app's name alone while no book is open. */
    public StringExpression windowTitle() {

        return Bindings.when(bookOpen).then(bookTitle.textProperty().concat(" - " + AppInfo.NAME))
                .otherwise(AppInfo.NAME);
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

        return locations.recentBooks(book);
    }

    /** Opens a book from the recent list, once unsaved changes are settled and the open book left. */
    void openRecent(Path directory) {

        if (unsavedChanges.settle("Save them before opening another book?"))
            gitControls.leave(() -> openBook(directory));
    }

    void clearRecentBooks() {

        settings.clearRecentBooks();
    }

    /** Takes {@code directory}, a book that is not found, off the recent list. */
    void forgetRecentBook(Path directory) {

        settings.forgetRecentBook(directory);
    }

    public void chooseBook() {

        if (!unsavedChanges.settle("Save them before opening another book?"))
            return;

        chooseBookFrom(locations.booksDirectory(book));
    }

    /**
     * Opens the book {@code dropped} on the welcome belongs to, a book's folder or anything inside it; see
     * {@link #openChosen}. Only while no book is open, so nothing is left unsaved.
     */
    public void openDropped(Path dropped) {

        openChosen(dropped);
    }

    /** Creates a book where the author chooses, and opens it in place of the open one. */
    public void createBook() {

        creation.create(book);
    }

    /**
     * Edits the open book's properties and cover, which are written as soon as the author saves the form, and its
     * remote, connected or removed then on the Git thread.
     */
    public void editProperties() {

        BookProperties current;
        try {
            current = book.properties();
        } catch (UncheckedIOException e) {
            errors.report("Cannot read the book properties", e);
            return;
        }

        Optional<Path> cover = book.cover();
        Optional<String> remoteAddress = Optional.ofNullable(repository).flatMap(repo -> repo.remote().address());
        RemoteSection remote = new RemoteSection(gitControls, keychain, book.root(), remoteAddress,
                repository != null, state.autoSync());
        BookPropertiesDialog.show(window.get(), current, cover, remote).ifPresent(edit -> {
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
            chapters.setBookProperties(edit.properties());
            statusBar.info("Book properties saved");
            if (repository != null) {
                remote.apply();
                state.saveAutoSync(remote.autoSync());
                gitControls.setAutoSync(remote.autoSync());
            }
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

        projectTransfer.importProject(locations.booksDirectory(book), this::isOpen, this::makeWayFor,
                this::openBook);
    }

    /**
     * Closes the open book, once unsaved changes are settled and it is synchronized if it does so automatically, and
     * shows the welcome as when the app starts.
     */
    public void closeOpenBook() {

        if (book != null && unsavedChanges.settle("Save them before closing the book?"))
            gitControls.leave(this::closeNow);
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
        // The title first, so the window's never shows the one of no book.
        bookTitle.setText(displayTitle());
        bookOpen.set(true);
        statusBar.setPageMeasure(pageMeasure());
        state = BookState.of(book);
        try {
            repository = BookRepository.open(book.root());
            gitControls.setRepository(repository, identifier());
            gitControls.setAutoSync(state.autoSync());
            statusBar.info("Opened " + book.title());
        } catch (GitCommandException e) {
            // Not fatal: the book can be edited without Git, so flag it in the status bar instead of an alert.
            statusBar.error(e.getMessage());
        }
        // Where the author left each chapter is theirs, not the book's.
        gitControls.ignoreQuietly(BookState.FILE, "Keep Psiche's own file out of Git");
        // Images travel with Synchronize, and have a history; what was deleted from a chapter stays on this device.
        gitControls.track(ChapterMedia.FORMER_GIT_IGNORE, ChapterMedia.GIT_IGNORE, book.mediaDir(),
                "Keep chapter images in Git");
        chapterWatch.start(book);
        chapters.show(book, repository, state);
        settings.bookOpened(book.root());
        onOpened.run();
        // After the commits above, on the Git thread, so a Synchronize it leads to finds them made.
        gitControls.checkRemote();
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

        return BookLocations.isFolderOf(book, folder);
    }

    /**
     * Readies the open book for an imported one, then runs {@code then}: settles its unsaved changes, synchronizes it
     * if it does so automatically, and closes it if the import replaces it; nothing runs if the author cancels.
     */
    private void makeWayFor(Path imported, Runnable then) {

        if (book == null) {
            then.run();
            return;
        }

        boolean replaced = isOpen(imported);
        if (!unsavedChanges.settle(replaced ? "Save them before closing the book?"
                : "Save them before opening the imported book?"))
            return;

        gitControls.leave(replaced ? () -> {
            closeNow();
            then.run();
        } : then);
    }

    /** Closes the open book, as it is, and shows the welcome as when the app starts. */
    private void closeNow() {

        String closed = book.title();
        closeBook();
        book = null;
        bookTitle.setText(NO_BOOK);
        statusBar.info("Closed " + closed);
    }

    /** Releases the open book, if any, discarding unsaved changes; callers save first if they need to. */
    private void closeBook() {

        chapterWatch.close();
        chapters.clear();

        if (repository != null) {
            repository.close();
            repository = null;
        }

        gitControls.setRepository(null, "");
        gitControls.setAutoSync(false);
        state = null;
        bookOpen.set(false);
    }

    /** The open book's identifier, which names its token in the keychain; blank if its properties cannot be read. */
    private String identifier() {

        try {
            return book.properties().identifier();
        } catch (UncheckedIOException e) {
            log.warn("Cannot read the identifier of {}", book.root(), e);
            return "";
        }
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

    /** Asks for a book's folder, starting in {@code start}, and opens the book it belongs to. */
    private void chooseBookFrom(Path start) {

        Dialogs.chooseDirectory(window.get(), CHOOSE_TITLE, start).ifPresent(this::openChosen);
    }

    /**
     * Opens the book {@code chosen} belongs to: the book itself, or the one it is inside, as its chapters or images
     * folder, which authors pick when they look for a file to open. Anything else is no book: the author is told what
     * a book's folder is, and may choose again from there.
     */
    private void openChosen(Path chosen) {

        Optional<Path> found = BookFolder.containing(chosen);
        if (found.isPresent()) {
            gitControls.leave(() -> openBook(found.get()));
            return;
        }

        Path folder = Files.isDirectory(chosen) ? chosen : chosen.getParent();
        Dialogs.choose(window.get(), NOT_A_BOOK_HEADER, NOT_A_BOOK, CHOOSE_AGAIN, ButtonType.CANCEL)
                .filter(CHOOSE_AGAIN::equals)
                .ifPresent(choice -> chooseBookFrom(folder));
    }
}
