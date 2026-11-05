package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.ProjectArchive;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.ProjectManifest;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.Progress;
import it.infog.psiche.ui.common.StatusBar;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Export Psiche project, in the Book menu: the whole book folder, Git history and notes included, to one
 * {@code .psiche} file, by default next to the book's folder and named after it and the day. It runs on a thread of
 * its own, which can take long with large note files; meanwhile the book is frozen, as {@link #busyProperty} tells,
 * and the status bar shows a bar of how far it has come.
 */
public final class ProjectTransfer {

    private static final Logger log = LoggerFactory.getLogger(ProjectTransfer.class);
    private static final String KIND = "Psiche project";
    // How long closing the app waits for a stopped export to clean up its unfinished file.
    private static final long STOP_WAIT_SECONDS = 2;

    private final ExecutorService thread =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("project-transfer").daemon().factory());
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable settleNotes;

    /** @param settleNotes writes what the notes board has still to write, so the export has it */
    public ProjectTransfer(StatusBar statusBar, ErrorReporter errors, Supplier<Window> window, Runnable settleNotes) {

        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.settleNotes = settleNotes;
    }

    /** True while an export runs: the book must not change until it is done. */
    public ReadOnlyBooleanProperty busyProperty() {

        return busy.getReadOnlyProperty();
    }

    /**
     * Writes {@code book}, the open one, as it is saved on disk, to a {@code .psiche} file the author picks; callers
     * make sure no chapter has unsaved changes.
     */
    public void export(BookFolder book) {

        String folder = book.root().getFileName().toString();
        Optional<Path> target = Dialogs.chooseSaveFile(window.get(), "Export Psiche project", book.root().getParent(),
                folder + "-" + LocalDate.now(), KIND, ProjectArchive.EXTENSION);
        target.ifPresent(file -> export(book, file));
    }

    /** Writes {@code book}, the open one, as it is saved on disk, to {@code target}, freezing it meanwhile. */
    void export(BookFolder book, Path target) {

        settleNotes.run();
        ProjectManifest manifest = manifest(book);
        String task = "Exporting " + manifest.title();
        busy.set(true);
        statusBar.progress(task, 0);
        run(new Task<>() {
            @Override
            protected Path call() {
                ProjectArchive.export(book.root(), target, manifest,
                        Progress.percent(percent -> statusBar.progress(task, percent)));
                return target;
            }
        }, written -> statusBar.info("Exported " + manifest.title() + " to " + written),
                "Cannot export the book");
    }

    /**
     * Stops an export that runs, as the app closes, and waits a moment for it to remove its unfinished file; the
     * project file is not written.
     */
    public void shutdown() {

        thread.shutdownNow();
        try {
            if (!thread.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS))
                log.warn("The export did not stop in time; an unfinished hidden file may be left beside its place");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** What the project file says about {@code book}: its folder's name, and its title and id if they can be read. */
    private ProjectManifest manifest(BookFolder book) {

        String folder = book.root().getFileName().toString();
        try {
            BookProperties properties = book.properties();
            return new ProjectManifest(folder, properties.title(), properties.identifier(), LocalDateTime.now());
        } catch (UncheckedIOException e) {
            log.warn("Cannot read the properties of {}; exporting it by its folder's name", book.root(), e);
            return new ProjectManifest(folder, folder, "", LocalDateTime.now());
        }
    }

    /**
     * Runs {@code task} on the thread, then {@code done} with its result, or reports its failure as {@code failure}.
     */
    private <T> void run(Task<T> task, Consumer<T> done, String failure) {

        task.setOnSucceeded(event -> {
            busy.set(false);
            done.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            errors.report(failure, task.getException());
        });
        thread.execute(task);
    }
}
