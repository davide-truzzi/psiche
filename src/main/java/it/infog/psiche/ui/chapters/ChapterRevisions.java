package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.ui.git.GitControls;

import java.nio.file.Path;
import java.util.Set;

/**
 * Which chapters changed since the book's last revision, its last commit, for the dot before each chapter's name.
 * Read from Git on its thread, so the answer comes a moment later; then the chapter list redraws.
 */
final class ChapterRevisions {

    private final GitControls gitControls;
    private final Runnable redraw;

    private BookRepository repository;
    private Set<Path> uncommitted = Set.of();

    /** @param redraw redraws the chapter list once the answer is in */
    ChapterRevisions(GitControls gitControls, Runnable redraw) {

        this.gitControls = gitControls;
        this.redraw = redraw;
    }

    /**
     * Follows {@code repository}, the open book's, or none with {@code null}: a book without Git has no revisions.
     * Nothing is known yet to have changed; {@link #check} reads it.
     */
    void show(BookRepository repository) {

        this.repository = repository;
        uncommitted = Set.of();
    }

    /** Reads again which chapters changed, after a save, a commit, or a change made outside the app. */
    void check() {

        if (repository == null) {
            redraw.run();
            return;
        }

        // The answer is dropped if another book opened meanwhile.
        BookRepository checked = repository;
        gitControls.read(checked, BookRepository::uncommittedFiles, files -> {
            if (repository != checked)
                return;

            uncommitted = files;
            redraw.run();
        });
    }

    /** True while the open book has Git, so its chapters have revisions. */
    boolean hasRevisions() {

        return repository != null;
    }

    /** True if {@code chapter} on disk is not as the last revision recorded it, or was never in one. */
    boolean changedSinceLastRevision(Chapter chapter) {

        return uncommitted.contains(chapter.file().toAbsolutePath().normalize());
    }
}
