package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.ui.chapters.UnsavedChanges;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.git.GitControls;

import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * New book: asks its title and where to create it, then leaves the open book, synchronized first if it does so
 * automatically, and creates the new one, with its Git repository, to be opened.
 */
final class BookCreation {

    private final UnsavedChanges unsavedChanges;
    private final GitControls gitControls;
    private final BookLocations locations;
    private final Settings settings;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Consumer<Path> open;

    /**
     * @param settings where the language and author of new books are chosen
     * @param open     opens the book created, in place of the open one
     */
    BookCreation(UnsavedChanges unsavedChanges, GitControls gitControls, BookLocations locations, Settings settings,
                 ErrorReporter errors, Supplier<Window> window, Consumer<Path> open) {

        this.unsavedChanges = unsavedChanges;
        this.gitControls = gitControls;
        this.locations = locations;
        this.settings = settings;
        this.errors = errors;
        this.window = window;
        this.open = open;
    }

    /**
     * Asks for a new book's title and folder, once the open book's unsaved changes are settled, then creates and opens
     * it; nothing if the author cancels.
     *
     * @param current the open book, or {@code null}: where to start choosing the folder
     */
    void create(BookFolder current) {

        if (!unsavedChanges.settle("Save them before creating a new book?"))
            return;

        Optional<String> title = Dialogs.askTitle(window.get(), "New book", "", "Create");
        if (title.isEmpty())
            return;

        Optional<Path> parent = Dialogs.chooseDirectory(
                window.get(), "Choose where to create \"%s\"".formatted(title.get().strip()),
                locations.newBooksDirectory(current));
        if (parent.isEmpty())
            return;

        gitControls.leave(() -> create(parent.get(), title.get()));
    }

    /** Creates the book {@code title} in {@code parent}, with its Git repository, and opens it. */
    private void create(Path parent, String title) {

        BookFolder created;

        try {
            Preferences preferences = settings.preferences();
            created = BookFolder.create(parent, title, preferences.newBookLanguage(), preferences.newBookAuthor());
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

        open.accept(created.root());
    }
}
