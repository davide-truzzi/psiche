package it.infog.psiche.ui.export;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.history.HistoryPdf;
import it.infog.psiche.export.history.model.WritingHistory;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.ui.common.AppInfo;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.git.GitControls;

import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Exports the open book's writing history to a PDF: every committed revision of its chapters, for a jury or a
 * publisher who asks to see how the book was written. The history is read on the Git thread, after the commands
 * clicked before; if the latest changes are not committed, the author may commit them first. The PDF is written on
 * the export thread.
 */
public final class HistoryExport {

    private static final String NAME = "the writing history";
    private static final ButtonType COMMIT_FIRST = new ButtonType("Commit first", ButtonData.OK_DONE);
    private static final ButtonType EXPORT_ANYWAY = new ButtonType("Export anyway", ButtonData.OTHER);

    private final HistoryPdf pdf = new HistoryPdf();
    private final GitControls git;
    private final Exports exports;
    private final BooleanSupplier unsavedWork;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;

    /** @param unsavedWork tells whether chapters have unsaved changes, which are not committed either */
    public HistoryExport(GitControls git, Exports exports, BooleanSupplier unsavedWork, StatusBar statusBar,
                         ErrorReporter errors, Supplier<Window> window) {

        this.git = git;
        this.exports = exports;
        this.unsavedWork = unsavedWork;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
    }

    /** Reads the history of {@code book}, whose repository is open in {@link GitControls}, and exports it. */
    public void export(BookFolder book) {

        Path chaptersDir = book.chaptersDir();
        statusBar.info("Reading the book's history…");
        git.ask(repo -> new History(repo.history().bookRevisions(chaptersDir), repo.hasUncommittedChanges()),
                history -> export(book, history), error -> errors.report("Cannot read the book's history", error));
    }

    private void export(BookFolder book, History history) {

        statusBar.info("");
        if (history.revisions().isEmpty()) {
            Dialogs.inform(window.get(), "No revisions yet",
                    "Commit the book's chapters at least once, then export its writing history.");
            return;
        }

        if ((history.uncommitted() || unsavedWork.getAsBoolean()) && !exportAnyway())
            return;

        BookProperties properties;
        try {
            properties = book.properties();
        } catch (UncheckedIOException e) {
            errors.report("Cannot export " + NAME, e);
            return;
        }

        //where
        ExportFormat format = ExportFormat.PDF;
        Optional<Path> target = Dialogs.chooseSaveFile(window.get(), "Export the writing history",
                book.root().getParent(), Dialogs.fileName(properties.title()) + " - writing history", format.label(),
                format.extension());
        if (target.isEmpty())
            return;

        //write
        WritingHistory writing = new WritingHistory(properties, history.revisions(), AppInfo.version().orElse(""),
                Instant.now(), ZoneId.systemDefault());
        exports.run(NAME, target.get(), () -> pdf.write(writing, target.get()));
    }

    /**
     * Tells the author the latest changes will not be in the history, and lets them commit first, which ends the export
     * and puts the cursor beside Commit.
     *
     * @return true to export without them
     */
    private boolean exportAnyway() {

        Optional<ButtonType> choice = Dialogs.choose(window.get(), "Your latest changes are not committed",
                "The writing history holds only committed revisions, so it would not show them. Commit them first, "
                        + "or export the history without them?", COMMIT_FIRST, EXPORT_ANYWAY, ButtonType.CANCEL);
        if (choice.filter(COMMIT_FIRST::equals).isPresent()) {
            git.focusCommitMessage();
            statusBar.info("Describe your changes beside Commit and commit them, then export the writing history "
                    + "again");
        }

        return choice.filter(EXPORT_ANYWAY::equals).isPresent();
    }

    /** The book's revisions, and whether it has changes no revision holds yet. */
    private record History(List<BookRevision> revisions,
                           boolean uncommitted) {
    }
}
