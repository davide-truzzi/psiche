package it.infog.psiche.ui.notes;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.NotesArchive;
import it.infog.psiche.notes.model.ImportReport;
import it.infog.psiche.notes.model.NotesManifest;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Export notes and Import notes, in the Book menu: the book's notes to a zip and back, to move them to another
 * computer or keep a copy, by default next to the book's folder. Both run on a thread of their own. An export shows its
 * progress in the status bar; an import, which first saves this computer's notes to a backup zip, shows it in a dialog
 * over the window until it is done, then tells what it did. Notes are not in Git, so the zip is their only copy
 * elsewhere.
 */
public final class NotesTransfer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final String ZIP_KIND = "Zip archive";
    private static final String ZIP = "zip";
    private static final ButtonType IMPORT_ANYWAY = new ButtonType("Import anyway", ButtonData.OK_DONE);

    private final ExecutorService thread =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("notes-transfer").daemon().factory());
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final NotesPane notesPane;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable keepOutOfGit;

    /** @param keepOutOfGit keeps {@code notes/} out of Git, once a book's first notes are imported */
    public NotesTransfer(NotesPane notesPane, StatusBar statusBar, ErrorReporter errors, Supplier<Window> window,
                         Runnable keepOutOfGit) {

        this.notesPane = notesPane;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.keepOutOfGit = keepOutOfGit;
    }

    /** True while an export or an import runs; another waits until it is done. */
    public ReadOnlyBooleanProperty busyProperty() {

        return busy.getReadOnlyProperty();
    }

    /** Writes the notes of {@code book}, the open one, to a zip the author picks. */
    public void exportNotes(BookFolder book) {

        NoteStore store = notesPane.thread().store();
        if (store == null || store.isEmpty()) {
            Dialogs.inform(window.get(), "No notes to export", "This book has no notes yet.");
            return;
        }

        Optional<BookProperties> properties = properties(book, "Cannot export the notes");
        if (properties.isEmpty())
            return;

        String title = properties.get().title();
        Optional<Path> zip = Dialogs.chooseSaveFile(window.get(), "Export notes", book.root().getParent(),
                Dialogs.fileName(title) + " - notes " + LocalDateTime.now().format(DAY), ZIP_KIND, ZIP);
        if (zip.isEmpty())
            return;

        notesPane.settle();
        NotesManifest manifest = manifest(properties.get());
        NoteStore.CopyProgress progress =
                percent(percent -> statusBar.info("Exporting notes… %d %%".formatted(percent)));
        statusBar.info("Exporting notes…");
        run(() -> {
            new NotesArchive(store).export(zip.get(), manifest, progress);
            return zip.get();
        }, written -> statusBar.info("Exported the notes to " + written), "Cannot export the notes");
    }

    /**
     * Merges the notes in a zip the author picks into those of {@code book}, the open one, once this computer's are
     * saved to a backup zip; then runs {@code imported}, so the book's views show them.
     */
    public void importNotes(BookFolder book, Runnable imported) {

        NoteStore store = notesPane.thread().store();
        if (store == null)
            return;

        Optional<BookProperties> properties = properties(book, "Cannot import the notes");
        if (properties.isEmpty())
            return;

        Optional<Path> zip = Dialogs.chooseOpenFile(window.get(), "Import notes", book.root().getParent(), ZIP_KIND,
                ZIP);
        if (zip.isEmpty())
            return;

        run(() -> NotesArchive.manifest(zip.get()), manifest -> {
            if (isOtherBook(manifest, properties.get()) && !confirmOtherBook(zip.get(), manifest))
                return;

            notesPane.settle();
            importFrom(book, store, zip.get(), properties.get(), imported);
        }, "Cannot import the notes");
    }

    /** Stops the transfer thread, interrupting an export that runs. */
    public void shutdown() {

        thread.shutdownNow();
    }

    /**
     * What hears how far a copy has come, on any thread, and hands {@code shown} the percentage on the FX thread each
     * time it changes, so the window is not flooded with updates.
     */
    static NoteStore.CopyProgress percent(IntConsumer shown) {

        AtomicInteger last = new AtomicInteger(-1);
        return (copied, total) -> {
            int percent = total <= 0 ? 100 : (int) (copied * 100 / total);
            if (last.getAndSet(percent) != percent)
                Platform.runLater(() -> shown.accept(percent));
        };
    }

    // Import

    private void importFrom(BookFolder book, NoteStore store, Path zip, BookProperties properties,
                            Runnable imported) {

        // A book without notes has nothing to back up, and its first notes are kept out of Git.
        boolean first = store.isEmpty();
        String backupName = Dialogs.fileName(properties.title()) + " - notes backup "
                + LocalDateTime.now().format(MOMENT) + ".zip";
        Optional<Path> backup = first ? Optional.empty() : Optional.of(book.root().resolveSibling(backupName));
        NotesArchive archive = new NotesArchive(store);
        NotesManifest manifest = manifest(properties);

        ImportProgressDialog progress = new ImportProgressDialog(window.get());
        progress.show();
        run(() -> {
            if (backup.isPresent())
                archive.backup(backup.get(), manifest, progress.step("Saving a backup of this computer's notes"));
            ImportReport report = archive.importFrom(zip, progress.step("Merging the notes of " + zip.getFileName()));

            // Nothing changed: the backup would only repeat the notes the book still has.
            if (report.isEmpty() && backup.isPresent())
                Files.deleteIfExists(backup.get());
            return report;
        }, report -> {
            progress.close();
            if (first && !store.isEmpty())
                keepOutOfGit.run();
            notesPane.reloadShown();
            imported.run();
            Dialogs.inform(window.get(), report.isEmpty() ? "Nothing to import" : "Notes imported",
                    summary(report, report.isEmpty() ? Optional.empty() : backup));
        }, failure -> {
            progress.close();
            errors.report("Cannot import the notes" + backup.map(path -> "; this computer's notes as they were "
                    + "are in " + path).orElse(""), failure);
            notesPane.reloadShown();
            imported.run();
        });
    }

    private static boolean isOtherBook(NotesManifest manifest, BookProperties properties) {

        return !manifest.book().isBlank() && !properties.identifier().isBlank()
                && !manifest.book().equals(properties.identifier());
    }

    private boolean confirmOtherBook(Path zip, NotesManifest manifest) {

        String whose = manifest.title().isBlank() ? "another book" : "\"" + manifest.title() + "\"";
        Optional<ButtonType> choice = Dialogs.choose(window.get(), "These notes are from another book",
                ("%s holds the notes of %s. Notes for a chapter this book has go on its board; the others go to "
                        + "Detached notes. Import them anyway?").formatted(zip.getFileName(), whose),
                ButtonType.CANCEL, IMPORT_ANYWAY);

        return choice.filter(IMPORT_ANYWAY::equals).isPresent();
    }

    /** "2 notes added, 1 updated. 1 kept twice: …", and where the backup is. */
    private static String summary(ImportReport report, Optional<Path> backup) {

        List<String> lines = new ArrayList<>();
        if (report.isEmpty())
            lines.add("This computer already has every note as the zip has it.");
        if (report.added() > 0)
            lines.add(notes(report.added()) + " added.");
        if (report.updated() > 0)
            lines.add(notes(report.updated()) + " updated with the changes made on the other computer.");
        if (report.duplicated() > 0)
            lines.add(notes(report.duplicated()) + " changed on both computers, kept twice: the imported version is "
                    + "beside the other, with a red band.");
        backup.ifPresent(path -> lines.add(("This computer's notes as they were before are in \"%s\", next to the "
                + "book's folder.").formatted(path.getFileName())));

        return String.join("\n\n", lines);
    }

    private static String notes(int count) {

        return count == 1 ? "1 note" : count + " notes";
    }

    // Both

    private Optional<BookProperties> properties(BookFolder book, String failure) {

        try {
            return Optional.of(book.properties());
        } catch (UncheckedIOException e) {
            errors.report(failure, e);
            return Optional.empty();
        }
    }

    private static NotesManifest manifest(BookProperties properties) {

        return new NotesManifest(properties.identifier(), properties.title());
    }

    /** Runs {@code work} on the transfer thread, then hands its result to {@code done}, or reports its failure. */
    private <T> void run(Callable<T> work, Consumer<T> done, String failure) {

        run(work, done, error -> errors.report(failure, error));
    }

    /**
     * Runs {@code work} on the transfer thread, marking the transfer busy meanwhile; then hands its result to
     * {@code done}, or its failure to {@code failed}, on the FX thread.
     */
    private <T> void run(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            done.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            failed.accept(task.getException());
        });
        busy.set(true);
        thread.execute(task);
    }
}
