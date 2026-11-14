package it.infog.psiche.git;

import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads every commit that changed a book's chapters, oldest first, each with the chapters' text before and after it,
 * for the writing history. Get it through {@link BookHistory#bookRevisions}.
 * <p>
 * Every commit comes once, whichever computer made it: each is compared with its first parent, the commit it was made
 * on. A merge brought in commits that are listed on their own, so it keeps only what the author changed by hand while
 * merging: the chapters whose merged text is different from every parent's.
 */
final class BookRevisions {

    private final Repository repo;
    // The chapters' folder, as a path in the repository.
    private final String directory;

    private BookRevisions(Repository repo, String directory) {

        this.repo = repo;
        this.directory = directory;
    }

    /** @param directory the chapters' folder, as a path in the repository */
    static List<BookRevision> read(Repository repo, String directory) {

        return new BookRevisions(repo, directory).read();
    }

    private List<BookRevision> read() {

        try (RevWalk walk = new RevWalk(repo); DiffFormatter diff = BookHistory.diffFormatter(repo)) {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null)
                return List.of();

            // Only the chapters' folder, with renames among the chapters found.
            diff.setPathFilter(PathFilter.create(directory));
            diff.setDetectRenames(true);
            // Parents before their children, so the work of both computers comes in the order it was built on.
            walk.sort(RevSort.TOPO);
            walk.sort(RevSort.REVERSE, true);
            walk.markStart(walk.parseCommit(head));

            List<BookRevision> revisions = new ArrayList<>();
            for (RevCommit commit : walk) {
                // The first commit has no parent; scan() compares it with an empty tree.
                RevCommit parent = commit.getParentCount() == 0 ? null : commit.getParent(0);
                List<DiffEntry> entries = diff.scan(parent, commit).stream().filter(this::touchesChapter).toList();
                if (entries.isEmpty())
                    continue;

                boolean merge = commit.getParentCount() > 1;
                List<ChapterChange> changes = new ArrayList<>();
                for (DiffEntry entry : entries)
                    if (!merge || resolvedByHand(walk, commit, entry))
                        changes.add(change(entry));

                changes.sort(Comparator.comparing(ChapterChange::path));
                revisions.add(new BookRevision(commit.getName(), BookRepository.shortId(commit),
                        commit.getFullMessage().strip(), Instant.ofEpochSecond(commit.getCommitTime()), merge,
                        changes));
            }

            return revisions;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the history of " + directory, e);
        }
    }

    private boolean touchesChapter(DiffEntry entry) {

        return BookHistory.isChapter(entry.getOldPath(), directory)
                || BookHistory.isChapter(entry.getNewPath(), directory);
    }

    /**
     * True if the merge's version of the entry's chapter is that of none of its parents: the author wrote it while
     * merging, as when resolving a conflict. The entry already differs from the first parent.
     */
    private static boolean resolvedByHand(RevWalk walk, RevCommit merge, DiffEntry entry) throws IOException {

        String path = entry.getChangeType() == ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
        ObjectId merged = entry.getNewId().toObjectId();
        for (int i = 1; i < merge.getParentCount(); i++) {
            RevCommit parent = walk.parseCommit(merge.getParent(i));
            try (TreeWalk file = TreeWalk.forPath(walk.getObjectReader(), path, parent.getTree())) {
                // A file the parent does not have reads as the zero id, as the merge's deleted file does.
                ObjectId theirs = file == null ? ObjectId.zeroId() : file.getObjectId(0);
                if (theirs.equals(merged))
                    return false;
            }
        }

        return true;
    }

    private ChapterChange change(DiffEntry entry) throws IOException {

        ChapterChangeKind kind = kind(entry);
        String path = kind == ChapterChangeKind.DELETED ? entry.getOldPath() : entry.getNewPath();
        boolean moved = kind == ChapterChangeKind.RENAMED || kind == ChapterChangeKind.COPIED;
        String before = kind == ChapterChangeKind.ADDED ? "" : text(entry.getOldId());
        String after = kind == ChapterChangeKind.DELETED ? "" : text(entry.getNewId());

        return new ChapterChange(path, moved ? entry.getOldPath() : null, kind, before, after);
    }

    /**
     * What the entry did to the chapters. A file renamed into the chapters, such as a text file given the Markdown
     * extension, is a new chapter; a chapter renamed out of them is a deleted one.
     */
    private ChapterChangeKind kind(DiffEntry entry) {

        boolean wasChapter = BookHistory.isChapter(entry.getOldPath(), directory);
        boolean isChapter = BookHistory.isChapter(entry.getNewPath(), directory);

        return switch (entry.getChangeType()) {
            case ADD -> ChapterChangeKind.ADDED;
            case MODIFY -> ChapterChangeKind.CHANGED;
            case DELETE -> ChapterChangeKind.DELETED;
            case RENAME -> !isChapter ? ChapterChangeKind.DELETED
                    : wasChapter ? ChapterChangeKind.RENAMED : ChapterChangeKind.ADDED;
            case COPY -> wasChapter ? ChapterChangeKind.COPIED : ChapterChangeKind.ADDED;
        };
    }

    /** A chapter's text, read as UTF-8 like the chapters on disk. */
    private String text(AbbreviatedObjectId blob) throws IOException {

        return new String(repo.open(blob.toObjectId(), Constants.OBJ_BLOB).getBytes(), StandardCharsets.UTF_8);
    }
}
