package it.infog.psiche.git;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.git.model.FileRevision;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Git repository at the root of a book folder, limited to what an author needs: commit, push, the history
 * of a chapter, and the chapters that were deleted.
 */
public final class BookRepository implements AutoCloseable {

    static final String REMOTE = "origin";
    static final String DEFAULT_BRANCH = "main";
    static final String GITIGNORE = ".gitignore";

    private static final Set<RemoteRefUpdate.Status> PUSHED =
            EnumSet.of(RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE);

    private final Git git;
    private final CredentialsProvider credentials;

    public BookRepository(Git git, CredentialsProvider credentials) {
        this.git = git;
        this.credentials = credentials;
    }

    /**
     * Opens the repository whose working tree is exactly {@code bookRoot}; parent repositories are not searched.
     * HTTPS remotes authenticate with credentials from {@code ~/.netrc}.
     *
     * @throws GitCommandException if {@code bookRoot} has no Git repository
     */
    public static BookRepository open(Path bookRoot) {
        try {
            return new BookRepository(Git.open(bookRoot.toFile()), new NetRCCredentialsProvider());
        } catch (RepositoryNotFoundException e) {
            throw new GitCommandException(
                    "%s is not a Git repository. Run 'git init' in the book folder to enable commits.".formatted(bookRoot), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open Git repository in " + bookRoot, e);
        }
    }

    /**
     * Equivalent of {@code git init}: creates an empty repository on branch {@code main} whose working tree is
     * {@code bookRoot}, regardless of the author's {@code init.defaultBranch}.
     *
     * @throws GitCommandException if the repository cannot be created
     */
    public static void init(Path bookRoot) {
        try {
            Git.init().setDirectory(bookRoot.toFile()).setInitialBranch(DEFAULT_BRANCH).call().close();
        } catch (GitAPIException e) {
            throw new GitCommandException("git init failed: " + e.getMessage(), e);
        }
    }

    /** Equivalent of {@code git add --all}: stages new, modified and deleted files. */
    private void stageAll() {
        try {
            git.add().addFilepattern(".").call();
            git.add().addFilepattern(".").setUpdate(true).call();
        } catch (GitAPIException e) {
            throw new GitCommandException("git add failed: " + e.getMessage(), e);
        }
    }

    /**
     * Records every change in the book (new, modified and deleted files) as one commit. Staging happens inside, so
     * authors never have to think about the index.
     *
     * @throws IllegalArgumentException if {@code message} is blank; nothing is staged then
     * @throws GitCommandException if nothing changed since the last commit
     */
    public Commit commit(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Commit message must not be blank");
        }
        stageAll();
        try {
            RevCommit commit = git.commit().setMessage(message.strip()).setAllowEmpty(false).call();
            return new Commit(commit.abbreviate(7).name(), commit.getShortMessage());
        } catch (EmptyCommitException e) {
            throw new GitCommandException("Nothing to commit: no changes since the last commit", e);
        } catch (GitAPIException e) {
            throw new GitCommandException("git commit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Records only {@code files} as one commit: each as it is on disk, or as deleted if it is gone. Every other
     * change, and every file Git does not track yet, such as an editor's backup file, stays out of the commit.
     *
     * @param files files in the book, each tracked, on disk, or both
     * @throws GitCommandException if none of {@code files} changed since the last commit
     */
    public Commit commitOnly(String message, List<Path> files) {

        List<String> paths = files.stream().map(this::repositoryPath).toList();
        try {
            //stage the files on disk, so a new one can be committed
            for (int i = 0; i < files.size(); i++)
                if (Files.exists(files.get(i)))
                    git.add().addFilepattern(paths.get(i)).call();

            //commit them alone
            CommitCommand commit = git.commit().setMessage(message.strip()).setAllowEmpty(false);
            paths.forEach(commit::setOnly);
            RevCommit committed = commit.call();
            return new Commit(committed.abbreviate(7).name(), committed.getShortMessage());
        } catch (EmptyCommitException e) {
            throw new GitCommandException("Nothing to commit: no changes since the last commit", e);
        } catch (GitAPIException | JGitInternalException e) {
            throw new GitCommandException("git commit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Keeps {@code pattern} out of Git in every copy of the book: adds it to the book's {@code .gitignore}, unless
     * it is there already, and commits that file alone with {@code message}.
     *
     * @param pattern a line of {@code .gitignore}, such as {@code notes/}
     * @return false if {@code .gitignore} already had it, so nothing was committed
     * @throws GitCommandException if the commit fails
     */
    public boolean ignore(String pattern, String message) {

        Path file = git.getRepository().getWorkTree().toPath().resolve(GITIGNORE);
        try {
            String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            if (text.lines().map(String::strip).anyMatch(pattern::equals))
                return false;

            //add the line, after a line break if the file does not end with one
            String separator = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
            Files.writeString(file, text + separator + pattern + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update " + file, e);
        }

        commitOnly(message, List.of(file));
        return true;
    }

    /**
     * True if a file Git tracks is not committed as it is on disk: modified, added or deleted. Files Git does not
     * track yet don't count, nor do ignored ones.
     *
     * @throws GitCommandException if the status cannot be read
     */
    public boolean hasUncommittedChanges() {

        try {
            return git.status().call().hasUncommittedChanges();
        } catch (GitAPIException e) {
            throw new GitCommandException("git status failed: " + e.getMessage(), e);
        }
    }

    /**
     * True if the last commit has {@code file}.
     *
     * @throws IllegalArgumentException if {@code file} is outside the book folder
     */
    public boolean isCommitted(Path file) {

        String path = repositoryPath(file);
        Repository repo = git.getRepository();

        try (RevWalk walk = new RevWalk(repo)) {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null)
                return false;

            try (TreeWalk tree = TreeWalk.forPath(repo, path, walk.parseCommit(head).getTree())) {
                return tree != null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the last commit of " + file, e);
        }
    }

    /**
     * Pushes the current branch to the branch of the same name on {@code origin}.
     *
     * @return the pushed branch name
     * @throws GitCommandException if there is no {@code origin}, HEAD is detached, or the remote rejects the push
     */
    public String push() {
        String fullBranch = currentBranch();
        if (!git.getRepository().getRemoteNames().contains(REMOTE)) {
            throw new GitCommandException("No '%s' remote is configured for this book".formatted(REMOTE));
        }
        Iterable<PushResult> results;
        try {
            results = git.push()
                    .setRemote(REMOTE)
                    .setRefSpecs(new RefSpec(fullBranch + ":" + fullBranch))
                    .setCredentialsProvider(credentials)
                    .call();
        } catch (GitAPIException e) {
            throw new GitCommandException("git push failed: " + e.getMessage(), e);
        }
        // JGit reports rejected updates in the result rather than throwing.
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                if (!PUSHED.contains(update.getStatus())) {
                    String reason = update.getMessage() == null ? "" : ": " + update.getMessage();
                    throw new GitCommandException("Push of %s was rejected (%s)%s"
                            .formatted(update.getRemoteName(), update.getStatus(), reason));
                }
            }
        }
        return Repository.shortenRefName(fullBranch);
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
    public List<FileRevision> history(Path file) {
        String path = repositoryPath(file);
        Repository repo = git.getRepository();
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
                String renamedFrom = change.filter(BookRepository::isRename).map(DiffEntry::getOldPath).orElse(null);
                revisions.add(new FileRevision(commit.getName(), commit.abbreviate(7).name(), pathInCommit,
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

    private static DiffFormatter diffFormatter(Repository repo) {
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

    /**
     * True if any commit had a file at {@code file}'s path, even one deleted or renamed away since. Git keeps history
     * by path, so a new file created there would show that earlier file's commits in its {@link #history}.
     *
     * @throws IllegalArgumentException if {@code file} is outside the book folder
     */
    public boolean hasHistory(Path file) {

        String path = repositoryPath(file);
        Repository repo = git.getRepository();

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

        String directory = repositoryPath(chaptersDir);
        Path workTree = git.getRepository().getWorkTree().toPath();
        Repository repo = git.getRepository();

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
                        deleted.add(new DeletedChapter(path, parent.getName(), commit.abbreviate(7).name(),
                                                       Instant.ofEpochSecond(commit.getCommitTime())));
                }
            }

            return deleted;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the deleted chapters of " + chaptersDir, e);
        }
    }

    /** A Markdown file directly in {@code directory}, as chapters are. */
    private static boolean isChapter(String path, String directory) {

        if (!path.startsWith(directory + "/"))
            return false;

        String name = path.substring(directory.length() + 1);
        return !name.contains("/") && name.endsWith(".md");
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
        Repository repo = git.getRepository();
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

    /** {@code file} relative to the working tree, with '/' separators as Git paths use. */
    private String repositoryPath(Path file) {
        Path workTree = git.getRepository().getWorkTree().toPath().toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(workTree) || absolute.equals(workTree)) {
            throw new IllegalArgumentException(file + " is not a file in " + workTree);
        }
        return workTree.relativize(absolute).toString().replace(File.separatorChar, '/');
    }

    private String currentBranch() {
        try {
            String fullBranch = git.getRepository().getFullBranch();
            if (fullBranch == null || !fullBranch.startsWith(Constants.R_HEADS)) {
                throw new GitCommandException("HEAD is not on a branch; check out a branch before pushing");
            }
            return fullBranch;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read HEAD", e);
        }
    }

    @Override
    public void close() {
        git.close();
    }
}
