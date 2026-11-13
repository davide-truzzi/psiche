package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.ProjectArchive;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.ProjectManifest;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.Progress;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.common.WorkThread;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Export and Import Psiche project, in the Book menu. An export writes the whole book folder, Git history and notes
 * included, to one {@code .psiche} file, by default next to the book's folder and named after it and the day; an
 * import unpacks one into a folder the author picks, backing up first, if asked, the folder it replaces. Either runs
 * on a thread of its own, which can take long with large note files; meanwhile the book is frozen, as
 * {@link #busyProperty} tells, and the status bar shows a bar of how far it has come.
 */
public final class ProjectTransfer {

    private static final Logger log = LoggerFactory.getLogger(ProjectTransfer.class);
    private static final String KIND = "Psiche project";
    // How long closing the app waits for a stopped transfer to clean up its unfinished files.
    private static final long STOP_WAIT_SECONDS = 2;
    private static final ButtonType BACK_UP = new ButtonType("Back up and replace", ButtonData.YES);
    private static final ButtonType REPLACE = new ButtonType("Replace", ButtonData.NO);
    private static final StopWarning EXPORTING = new StopWarning("The book is being exported",
            "Closing now stops the export, and the project file is not written.", "Keep exporting");
    private static final StopWarning IMPORTING = new StopWarning("A book is being imported",
            "Closing now stops the import: the folder it replaces stays as it was, and no backup is written.",
            "Keep importing");

    private final WorkThread thread = new WorkThread("project-transfer");
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable settleNotes;

    private StopWarning running = EXPORTING;

    /** @param settleNotes writes what the notes board has still to write, so the export has it */
    public ProjectTransfer(StatusBar statusBar, ErrorReporter errors, Supplier<Window> window, Runnable settleNotes) {

        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.settleNotes = settleNotes;
    }

    /** True while an export or an import runs: the book must not change until it is done. */
    public ReadOnlyBooleanProperty busyProperty() {

        return thread.busyProperty();
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
        ProjectManifest manifest = manifest(book.root());
        String task = "Exporting " + manifest.title();
        start(EXPORTING, task);
        run(() -> {
            ProjectArchive.export(book.root(), target, manifest,
                    Progress.percent(percent -> statusBar.progress(task, percent)));
            return target;
        }, written -> statusBar.info("Exported " + manifest.title() + " to", written), "Cannot export the book");
    }

    /**
     * Asks for a {@code .psiche} file and the folder to unpack it in, then imports it and opens the book. If a folder
     * of the book's name is there already, the author chooses to back it up first, to replace it, or to stop.
     *
     * @param booksDirectory where the choosers start; null to let them choose
     * @param isOpen         tells whether a folder is the open book's
     * @param makeWay        readies the open book for the imported one, given its folder: closes it if replaced, or
     *                       settles its unsaved changes; then runs the import it is given, not if the author cancels
     * @param open           opens the imported book
     */
    public void importProject(Path booksDirectory, Predicate<Path> isOpen, BiConsumer<Path, Runnable> makeWay,
                              Consumer<Path> open) {

        //project file
        Optional<Path> archive = Dialogs.chooseOpenFile(window.get(), "Import Psiche project", booksDirectory, KIND,
                ProjectArchive.EXTENSION);
        if (archive.isEmpty())
            return;

        ProjectManifest manifest;
        try {
            manifest = ProjectArchive.manifest(archive.get());
        } catch (UncheckedIOException e) {
            errors.report("Cannot import the book", e);
            return;
        }

        //where
        Optional<Path> parent = Dialogs.chooseDirectory(window.get(),
                "Choose where to import \"%s\"".formatted(manifest.title()), booksDirectory);
        if (parent.isEmpty())
            return;

        Path target = parent.get().resolve(manifest.folder());
        if (isInside(archive.get(), target)) {
            Dialogs.inform(window.get(), "The project file is in the folder it replaces",
                    "Move %s out of %s first, then import it.".formatted(archive.get().getFileName(), target));
            return;
        }

        //what it replaces
        Optional<Path> backup = Optional.empty();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            ButtonType choice = askReplace(target, isOpen.test(target));
            if (choice == BACK_UP) {
                backup = chooseBackup(target, archive.get());
                if (backup.isEmpty())
                    return;
            } else if (choice != REPLACE) {
                return;
            }
        }

        Optional<Path> chosenBackup = backup;
        makeWay.accept(target, () -> importProject(archive.get(), manifest, parent.get(), chosenBackup, open));
    }

    /**
     * Unpacks {@code archive}, described by {@code manifest}, into {@code parent}, once {@code backup}, if any, holds
     * the folder it replaces, then opens it; the open book, if any, is frozen meanwhile.
     */
    void importProject(Path archive, ProjectManifest manifest, Path parent, Optional<Path> backup,
                       Consumer<Path> open) {

        Path target = parent.resolve(manifest.folder());
        String task = "Importing " + manifest.title();
        String backingUp = "Backing up " + manifest.folder();
        start(IMPORTING, backup.isPresent() ? backingUp : task);
        run(() -> {
            backup.ifPresent(file -> ProjectArchive.export(target, file, manifest(target),
                    Progress.percent(percent -> statusBar.progress(backingUp, percent))));
            return ProjectArchive.importTo(archive, parent,
                    Progress.percent(percent -> statusBar.progress(task, percent)));
        }, imported -> {
            open.accept(imported);
            statusBar.info("Imported " + manifest.title() + backup.map(file -> ", the old folder backed up to " + file)
                    .orElse(""));
        }, "Cannot import the book");
    }

    /**
     * Lets the author keep a transfer that runs going as the window closes; false means stay open. If they close
     * anyway, {@link #shutdown} stops it.
     */
    public boolean confirmStop() {

        if (!thread.isBusy())
            return true;

        ButtonType closeAnyway = new ButtonType("Close anyway", ButtonData.OK_DONE);
        return Dialogs.choose(window.get(), running.header(), running.content(),
                        new ButtonType(running.keep(), ButtonData.CANCEL_CLOSE), closeAnyway)
                .filter(closeAnyway::equals)
                .isPresent();
    }

    /**
     * Stops a transfer that runs, as the app closes, and waits a moment for it to remove its unfinished files; the
     * project file is not written, and a folder an import was to replace stays as it was.
     */
    public void shutdown() {

        try {
            if (!thread.shutdown(STOP_WAIT_SECONDS))
                log.warn("The transfer did not stop in time; unfinished hidden files may be left beside its place");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Asks whether to back up {@code target} before the imported book replaces it, to replace it, or to stop.
     *
     * @param open true if it is the open book's folder, which closes first
     */
    private ButtonType askReplace(Path target, boolean open) {

        String header = open
                ? "The imported book replaces the open one"
                : "There is already a folder named " + target.getFileName();
        String content = "Importing replaces %s%s, and what is in it now is lost.".formatted(target,
                open ? ", which closes first" : "") + " Back it up first, as a Psiche project?";
        return Dialogs.choose(window.get(), header, content, BACK_UP, REPLACE, ButtonType.CANCEL)
                .orElse(ButtonType.CANCEL);
    }

    /**
     * Asks where to back up {@code target}, beside it by default; empty if the author cancels, or picks a place the
     * import would lose: inside {@code target}, or {@code archive} itself.
     */
    private Optional<Path> chooseBackup(Path target, Path archive) {

        String folder = target.getFileName().toString();
        Optional<Path> backup = Dialogs.chooseSaveFile(window.get(), "Back up \"%s\"".formatted(folder),
                target.getParent(), folder + "-backup-" + LocalDate.now(), KIND, ProjectArchive.EXTENSION);
        if (backup.isEmpty())
            return backup;

        if (isInside(backup.get(), target)) {
            Dialogs.inform(window.get(), "The backup cannot be in the folder it backs up",
                    "The import replaces %s, and the backup with it. Import again and save it elsewhere."
                            .formatted(target));
            return Optional.empty();
        }
        if (normal(backup.get()).equals(normal(archive))) {
            Dialogs.inform(window.get(), "The backup cannot replace the file being imported",
                    "Import again and give the backup another name.");
            return Optional.empty();
        }

        return backup;
    }

    /** Shows {@code task} in the status bar, at nought; the book freezes as the work starts on the thread. */
    private void start(StopWarning warning, String task) {

        running = warning;
        statusBar.progress(task, 0);
    }

    /**
     * What the project file says about the book in {@code folder}: its folder's name, and its title and id if they
     * can be read.
     */
    private static ProjectManifest manifest(Path folder) {

        String name = folder.getFileName().toString();
        try {
            BookProperties properties = BookFolder.open(folder).properties();
            return new ProjectManifest(name, properties.title(), properties.identifier(), LocalDateTime.now());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            log.warn("Cannot read the properties of {}; exporting it by its folder's name", folder, e);
            return new ProjectManifest(name, name, "", LocalDateTime.now());
        }
    }

    /** True if {@code path} is {@code folder} or in it. */
    private static boolean isInside(Path path, Path folder) {

        return normal(path).startsWith(normal(folder));
    }

    private static Path normal(Path path) {

        return path.toAbsolutePath().normalize();
    }

    /**
     * Runs {@code work} on the thread, then {@code done} with its result, or reports its failure as {@code failure}.
     */
    private <T> void run(Callable<T> work, Consumer<T> done, String failure) {

        thread.run(work, done, error -> errors.report(failure, error));
    }

    /** What closing the window asks while a transfer runs. */
    private record StopWarning(String header,
                               String content,
                               String keep) {
    }
}
