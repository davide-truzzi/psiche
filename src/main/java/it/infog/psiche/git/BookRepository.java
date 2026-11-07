package it.infog.psiche.git;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.settings.model.Credentials;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Git repository at the root of a book folder, limited to what an author needs: commit, push, and what has
 * changed since the last commit. What the commits recorded, such as the history of a chapter, is read through
 * {@link #history()}, and the book's remote repository is reached through {@link #remote()}.
 */
public final class BookRepository implements AutoCloseable {

    static final String REMOTE = "origin";
    static final String DEFAULT_BRANCH = "main";
    // As many characters of a commit's id as Git shows in a log.
    private static final int SHORT_ID_LENGTH = 7;

    private static final Set<RemoteRefUpdate.Status> PUSHED =
            EnumSet.of(RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE);

    private final Git git;
    private final BookHistory history;
    private final BookRemote remote;
    private final GitIgnore gitignore;

    public BookRepository(Git git) {

        this.git = git;
        history = new BookHistory(git.getRepository());
        remote = new BookRemote(git.getRepository());
        gitignore = new GitIgnore(this, git.getRepository());
    }

    /**
     * Opens the repository whose working tree is exactly {@code bookRoot}; parent repositories are not searched.
     *
     * @throws GitCommandException if {@code bookRoot} has no Git repository
     */
    public static BookRepository open(Path bookRoot) {

        try {
            return new BookRepository(Git.open(bookRoot.toFile()));
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
            return commitOf(git.commit().setMessage(message.strip()).setAllowEmpty(false).call());
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
            return commitOf(commit.call());
        } catch (EmptyCommitException e) {
            throw new GitCommandException("Nothing to commit: no changes since the last commit", e);
        } catch (GitAPIException | JGitInternalException e) {
            throw new GitCommandException("git commit failed: " + e.getMessage(), e);
        }
    }

    /**
     * True if a file Git tracks is not committed as it is on disk: modified, added or deleted. Files Git does not
     * track yet don't count, nor do ignored ones.
     *
     * @throws GitCommandException if the status cannot be read
     */
    public boolean hasUncommittedChanges() {

        return status().hasUncommittedChanges();
    }

    /**
     * True if everything in the book is committed: no file Git tracks is changed, and no new file waits to be added.
     * Ignored files don't count.
     *
     * @throws GitCommandException if the status cannot be read
     */
    public boolean isCommitted() {

        return status().isClean();
    }

    /**
     * The files that are not on disk as the last commit recorded them: changed, new, or never committed, as absolute
     * paths. Deleted and ignored files are left out.
     *
     * @throws GitCommandException if the status cannot be read
     */
    public Set<Path> uncommittedFiles() {

        Path workTree = git.getRepository().getWorkTree().toPath().toAbsolutePath().normalize();
        Status status = status();
        return Stream.of(status.getModified(), status.getChanged(), status.getAdded(), status.getUntracked(),
                        status.getConflicting())
                .flatMap(Set::stream)
                .map(workTree::resolve)
                .collect(Collectors.toUnmodifiableSet());
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

    /** What the book's commits recorded: a chapter's history, the deleted chapters, a file as a commit left it. */
    public BookHistory history() {

        return history;
    }

    /** The book's {@code .gitignore}: what Git keeps out of every copy of the book. */
    public GitIgnore gitignore() {

        return gitignore;
    }

    /** The book's remote repository: its address, and Test before connecting it. */
    public BookRemote remote() {

        return remote;
    }

    /**
     * Synchronize: brings in the remote's revisions, merges them with the book's, and sends the book's; see
     * {@link Synchronization}.
     *
     * @param credentials the username and token of an HTTPS remote; none for a folder
     * @throws it.infog.psiche.git.exceptions.UncommittedWorkException if the book has changes that are not committed
     * @throws GitCommandException if the remote cannot be reached, holds something else, or refuses the book
     */
    public SyncReport synchronize(Optional<Credentials> credentials) {

        return new Synchronization(this, git.getRepository()).run(credentials);
    }

    /**
     * Puts the remote's book in place of this one, an empty book Test found to have its title
     * ({@link it.infog.psiche.git.model.enums.RemoteState#HOLDS_BOOK}): its chapters, history and properties, silently
     * overwriting what the empty book had.
     *
     * @param credentials the username and token of an HTTPS remote; none for a folder
     * @return the identifier of the book now in the folder, the remote's; blank if it has none
     * @throws GitCommandException if the remote cannot be reached, or holds no book
     */
    public String adopt(Optional<Credentials> credentials) {

        return new Synchronization(this, git.getRepository()).adopt(credentials);
    }

    /**
     * Pushes the current branch to the branch of the same name on {@code origin}.
     *
     * @param credentials the username and token of an HTTPS remote; none for a folder
     * @return the pushed branch name
     * @throws GitCommandException if there is no {@code origin}, HEAD is detached, or the remote rejects the push
     */
    public String push(Optional<Credentials> credentials) {

        String fullBranch = currentBranch();
        if (!git.getRepository().getRemoteNames().contains(REMOTE)) {
            throw new GitCommandException("No '%s' remote is configured for this book".formatted(REMOTE));
        }
        Iterable<PushResult> results;
        try {
            results = git.push()
                    .setRemote(REMOTE)
                    .setRefSpecs(new RefSpec(fullBranch + ":" + fullBranch))
                    .setCredentialsProvider(BookRemote.provider(credentials))
                    .call();
        } catch (GitAPIException e) {
            throw new GitCommandException("git push failed: " + RemoteErrors.cleaned(e.getMessage()), e);
        }
        // JGit reports rejected updates in the result rather than throwing.
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                if (!PUSHED.contains(update.getStatus())) {
                    String reason = update.getMessage() == null ? "" : ": " + RemoteErrors.cleaned(update.getMessage());
                    throw new GitCommandException("Push of %s was rejected (%s)%s"
                            .formatted(update.getRemoteName(), update.getStatus(), reason));
                }
            }
        }
        return Repository.shortenRefName(fullBranch);
    }

    /**
     * {@code file} relative to the working tree of {@code repo}, with '/' separators as Git paths use.
     *
     * @throws IllegalArgumentException if {@code file} is outside the book folder
     */
    static String repositoryPath(Repository repo, Path file) {

        Path workTree = repo.getWorkTree().toPath().toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(workTree) || absolute.equals(workTree)) {
            throw new IllegalArgumentException(file + " is not a file in " + workTree);
        }
        return workTree.relativize(absolute).toString().replace(File.separatorChar, '/');
    }

    /** The short id of {@code commit}, as Git shows it in a log. */
    static String shortId(RevCommit commit) {

        return commit.abbreviate(SHORT_ID_LENGTH).name();
    }

    /** @throws GitCommandException if the status cannot be read */
    Status status() {

        try {
            return git.status().call();
        } catch (GitAPIException e) {
            throw new GitCommandException("git status failed: " + e.getMessage(), e);
        }
    }

    /** {@code file} relative to the working tree, with '/' separators as Git paths use. */
    private String repositoryPath(Path file) {

        return repositoryPath(git.getRepository(), file);
    }

    /** The commit as the author sees it: its short id and the first line of its message. */
    private static Commit commitOf(RevCommit commit) {

        return new Commit(shortId(commit), commit.getShortMessage());
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
