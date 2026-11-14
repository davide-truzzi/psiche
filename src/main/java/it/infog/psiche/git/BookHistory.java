package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.git.model.FileRevision;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a book's commits recorded, read without changing anything: the history of a chapter, whether a name was ever
 * used, the chapters that were deleted, a file as a commit left it, and the whole book's history. Get it from
 * {@link BookRepository#history()}.
 */
public final class BookHistory {

    private final Repository repo;

    BookHistory(Repository repo) {

        this.repo = repo;
    }

    /**
     * The commits that changed {@code file}, newest first, each with the lines of the file it added and removed (the
     * counts {@code git log --numstat} reports). Like {@code git log --follow}, the history goes on through renames:
     * the commit that renamed the file counts only the lines changed on the way, and the commits before it are those
     * of the file under its previous name. A merge is compared with its first parent.
     *
     * @param file a file inside the book folder
     * @return empty if the repository has no commits yet or none touched {@code file}
     * @throws IllegalArgumentException if {@code file} is outside the book folder
     */
    public List<FileRevision> revisions(Path file) {

        String path = BookRepository.repositoryPath(repo, file);
        try (RevWalk walk = new RevWalk(repo);
             DiffFormatter pathDiff = diffFormatter(repo);
             DiffFormatter renameDiff = diffFormatter(repo)) {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null) {
                return List.of();
            }
            renameDiff.setDetectRenames(true);
            // Selects the commits that changed the file, switching to its older name past the commit that renamed it.
            walk.setTreeFilter(FollowFilter.create(path, repo.getConfig().get(DiffConfig.KEY)));
            // Keep each commit's real parents, so it is compared with the commit it was made on.
            walk.setRewriteParents(false);
            walk.markStart(walk.parseCommit(head));

            List<FileRevision> revisions = new ArrayList<>();
            String pathInCommit = path;
            for (RevCommit commit : walk) {
                // The first commit has no parent; scan() compares it with an empty tree.
                RevCommit parent = commit.getParentCount() == 0 ? null : commit.getParent(0);
                Optional<DiffEntry> change = change(pathDiff, renameDiff, parent, commit, pathInCommit);
                int added = 0;
                int removed = 0;
                if (change.isPresent()) {
                    for (Edit edit : pathDiff.toFileHeader(change.get()).toEditList()) {
                        added += edit.getLengthB();
                        removed += edit.getLengthA();
                    }
                }
                String renamedFrom = change.filter(BookHistory::isRename).map(DiffEntry::getOldPath).orElse(null);
                revisions.add(new FileRevision(commit.getName(), BookRepository.shortId(commit), pathInCommit,
                        renamedFrom, commit.getShortMessage(), Instant.ofEpochSecond(commit.getCommitTime()),
                        added, removed));
                if (renamedFrom != null) {
                    pathInCommit = renamedFrom;
                }
            }
            return revisions;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the history of " + file, e);
        }
    }

    /**
     * True if any commit had a file at {@code file}'s path, even one deleted or renamed away since. Git keeps history
     * by path, so a new file created there would show that earlier file's commits in its {@link #revisions}.
     *
     * @throws IllegalArgumentException if {@code file} is outside the book folder
     */
    public boolean hasHistory(Path file) {

        String path = BookRepository.repositoryPath(repo, file);

        try (RevWalk walk = new RevWalk(repo)) {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null)
                return false;

            walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF));
            walk.markStart(walk.parseCommit(head));
            return walk.next() != null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the history of " + file, e);
        }
    }

    /**
     * The chapter files in {@code chaptersDir} that a commit deleted and that are not on disk now, most recently
     * deleted first. A chapter deleted more than once appears once, with its latest deletion. Renamed chapters are
     * not deleted ones, and a chapter whose name is in use again, as after it was restored, is not listed.
     *
     * @param chaptersDir the book's chapter folder
     * @return empty if the repository has no commits yet
     * @throws IllegalArgumentException if {@code chaptersDir} is outside the book folder
     */
    public List<DeletedChapter> deletedChapters(Path chaptersDir) {

        String directory = BookRepository.repositoryPath(repo, chaptersDir);
        Path workTree = repo.getWorkTree().toPath();

        try (RevWalk walk = new RevWalk(repo); DiffFormatter diff = diffFormatter(repo)) {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null)
                return List.of();

            // Only commits that changed the chapters, each compared with its real first parent.
            diff.setPathFilter(PathFilter.create(directory));
            diff.setDetectRenames(true);
            walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(directory), TreeFilter.ANY_DIFF));
            walk.setRewriteParents(false);
            walk.markStart(walk.parseCommit(head));

            List<DeletedChapter> deleted = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (RevCommit commit : walk) {
                if (commit.getParentCount() == 0)
                    continue;

                RevCommit parent = commit.getParent(0);
                for (DiffEntry entry : diff.scan(parent, commit)) {
                    String path = entry.getOldPath();
                    if (entry.getChangeType() == ChangeType.DELETE && isChapter(path, directory) && seen.add(path)
                            && !Files.exists(workTree.resolve(path)))
                        deleted.add(new DeletedChapter(path, parent.getName(), BookRepository.shortId(commit),
                                                       Instant.ofEpochSecond(commit.getCommitTime())));
                }
            }

            return deleted;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the deleted chapters of " + chaptersDir, e);
        }
    }

    /**
     * Every commit that changed the chapters in {@code chaptersDir}, oldest first, each with the chapters it changed
     * and their text before and after it: the book's writing history. Commits that changed only other files, such as
     * the book's properties or images, are left out. Commits made on other computers come once each, in their own
     * place; a merge brings only what the author changed by hand while merging, not the commits it brought in.
     *
     * @param chaptersDir the book's chapter folder
     * @return empty if the repository has no commits yet
     * @throws IllegalArgumentException if {@code chaptersDir} is outside the book folder
     */
    public List<BookRevision> bookRevisions(Path chaptersDir) {

        return BookRevisions.read(repo, BookRepository.repositoryPath(repo, chaptersDir));
    }

    /**
     * The file of {@code revision} as its commit recorded it, under the name it had then, read as UTF-8 like the
     * chapters on disk.
     *
     * @return empty if the file did not exist in that commit, as in a commit that deleted it
     * @throws GitCommandException if the revision's commit is not a commit of this repository
     */
    public Optional<String> contentOf(FileRevision revision) {

        return contentAt(revision.commitId(), revision.path());
    }

    /**
     * The last version of a deleted chapter, from the commit before the one that deleted it.
     *
     * @throws GitCommandException if that commit is not a commit of this repository or does not have the chapter
     */
    public String contentOf(DeletedChapter chapter) {

        return contentAt(chapter.lastCommitId(), chapter.path()).orElseThrow(() -> new GitCommandException(
                "%s is not in commit %s".formatted(chapter.path(), chapter.lastCommitId())));
    }

    private Optional<String> contentAt(String commitId, String path) {

        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            try (TreeWalk entry = TreeWalk.forPath(repo, path, commit.getTree())) {
                if (entry == null) {
                    return Optional.empty();
                }
                byte[] content = repo.open(entry.getObjectId(0)).getBytes();
                return Optional.of(new String(content, StandardCharsets.UTF_8));
            }
        } catch (InvalidObjectIdException | MissingObjectException | IncorrectObjectTypeException e) {
            throw new GitCommandException("%s is not a commit of this book".formatted(commitId), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read %s at %s".formatted(path, commitId), e);
        }
    }

    static DiffFormatter diffFormatter(Repository repo) {

        DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diff.setRepository(repo);
        diff.setDiffComparator(RawTextComparator.DEFAULT);
        return diff;
    }

    /**
     * How {@code commit} changed the file at {@code path}. If the commit added it, looks for the file it was renamed
     * or copied from, so the change counts only the lines edited on the way.
     *
     * @param renameDiff a formatter that detects renames across the whole tree
     */
    private static Optional<DiffEntry> change(DiffFormatter pathDiff, DiffFormatter renameDiff, RevCommit parent,
                                              RevCommit commit, String path) throws IOException {

        pathDiff.setPathFilter(PathFilter.create(path));
        List<DiffEntry> entries = pathDiff.scan(parent, commit);
        boolean added = entries.size() == 1 && entries.getFirst().getChangeType() == ChangeType.ADD;
        if (parent != null && added) {
            // Renames show only when deleted and added files are compared together, so scan the whole tree.
            for (DiffEntry entry : renameDiff.scan(parent, commit)) {
                if (isRename(entry) && entry.getNewPath().equals(path)) {
                    return Optional.of(entry);
                }
            }
        }
        return entries.stream().findFirst();
    }

    private static boolean isRename(DiffEntry entry) {

        return entry.getChangeType() == ChangeType.RENAME || entry.getChangeType() == ChangeType.COPY;
    }

    /** A Markdown file directly in {@code directory}, as chapters are. */
    static boolean isChapter(String path, String directory) {

        if (!path.startsWith(directory + "/"))
            return false;

        String name = path.substring(directory.length() + 1);
        return !name.contains("/") && name.endsWith(".md");
    }
}
