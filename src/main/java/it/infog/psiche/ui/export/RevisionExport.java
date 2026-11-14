package it.infog.psiche.ui.export;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.common.SafeFiles;
import it.infog.psiche.git.model.BookCommit;
import it.infog.psiche.ui.common.DateFormats;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.git.GitControls;

import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Exports the open book as one of its revisions left it, in any of the book's formats. The author picks the revision
 * from every commit of the book; its chapters and media are written to a temporary copy of the book on the Git
 * thread, the copy is exported as the book is, and deleted once the export ends, however it ends.
 */
public final class RevisionExport {

    private static final Logger log = LoggerFactory.getLogger(RevisionExport.class);
    private static final String COPY_PREFIX = "psiche-revision-";
    // The words of a revision's message its export's name takes, at most.
    private static final int NAME_WORDS = 6;

    private final GitControls git;
    private final Exports exports;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;

    public RevisionExport(GitControls git, Exports exports, StatusBar statusBar, ErrorReporter errors,
                          Supplier<Window> window) {

        this.git = git;
        this.exports = exports;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
    }

    /** Lists the commits of {@code book}, whose repository is open in {@link GitControls}, to export one. */
    public void export(BookFolder book) {

        git.ask(repo -> repo.history().commits(), commits -> choose(book, commits),
                error -> errors.report("Cannot read the book's revisions", error));
    }

    private void choose(BookFolder book, List<BookCommit> commits) {

        if (commits.isEmpty()) {
            Dialogs.inform(window.get(), "No revisions yet",
                    "Commit the book at least once, then export it as a revision left it.");
            return;
        }

        RevisionChooser.show(window.get(), commits).ifPresent(choice -> {
            BookCommit commit = choice.commit();
            statusBar.info("Reading the book at %s…".formatted(commit.shortId()));
            git.ask(repo -> {
                Path copy = copyFolder(book);
                try {
                    repo.history().writeFolders(commit.commitId(), List.of(book.chaptersDir(), book.mediaDir()), copy);
                } catch (RuntimeException e) {
                    delete(copy);
                    throw e;
                }
                return copy;
            }, copy -> {
                statusBar.info("");
                exports.exportRevision(book, copy, name(commit), choice.format(), () -> delete(copy));
            }, error -> errors.report("Cannot read the book at " + commit.shortId(), error));
        });
    }

    /**
     * The revision as the export's name gives it, by its day and the first words of its message, as
     * "2026-10-27, Rework the opening": a commit's id means nothing to most authors.
     */
    private static String name(BookCommit commit) {

        String words = Arrays.stream(commit.message().strip().split("\\s+"))
                .limit(NAME_WORDS)
                .collect(Collectors.joining(" "));
        String day = DateFormats.FILE_DAY.format(commit.time());

        return words.isEmpty() ? day : day + ", " + words;
    }

    /**
     * A folder for the copy, named as the book's own, since a book without a title in its properties takes its
     * folder's name, inside a new temporary folder that holds nothing else.
     */
    private static Path copyFolder(BookFolder book) {

        try {
            return Files.createTempDirectory(COPY_PREFIX).resolve(book.root().getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot make a temporary folder for the book's copy", e);
        }
    }

    /** Deletes the copy with the temporary folder made for it; a failure is only logged, as the export is done. */
    private static void delete(Path copy) {

        try {
            SafeFiles.deleteTree(copy.getParent());
        } catch (UncheckedIOException e) {
            log.warn("Cannot delete the temporary copy of the book at {}", copy, e);
        }
    }
}
