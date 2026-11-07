package it.infog.psiche.git;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.exceptions.RemoteStateException;
import it.infog.psiche.git.exceptions.UncommittedWorkException;
import it.infog.psiche.git.model.Conflict;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.git.model.enums.RemoteState;
import it.infog.psiche.settings.model.Credentials;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Synchronize: brings in what the book's remote has that the book lacks, merges it as {@link BookMerge} does, and
 * sends the book's own revisions, in one go, on branch {@code main}. Work that is not committed blocks it.
 */
final class Synchronization {

    private static final String MAIN = Constants.R_HEADS + BookRepository.DEFAULT_BRANCH;
    private static final String TRACKING = Constants.R_REMOTES + BookRepository.REMOTE + "/"
            + BookRepository.DEFAULT_BRANCH;
    private static final String MERGE_MESSAGE = "Merge with the remote";

    private final BookRepository book;
    private final Repository repository;

    Synchronization(BookRepository book, Repository repository) {

        this.book = book;
        this.repository = repository;
    }

    /**
     * Synchronizes the book with its remote.
     *
     * @param credentials the username and token of an HTTPS remote; none for a folder
     * @param timeout     how long to wait for the host to answer; zero for JGit's default
     * @throws UncommittedWorkException if the book has changes that are not committed; nothing is done then
     * @throws RemoteStateException     if the remote cannot be reached or refuses the credentials
     * @throws GitCommandException      if the remote holds something else, or refuses the book
     */
    SyncReport run(Optional<Credentials> credentials, Duration timeout) {

        //preconditions: the book committed, on main
        if (!book.isCommitted())
            throw new UncommittedWorkException();

        requireMain();

        //what the remote has
        fetch(credentials, timeout);
        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId theirsId = repository.resolve(TRACKING);
            RemoteState found = theirsId == null ? RemoteState.EMPTY : BookRemote.compare(repository, theirsId);
            ObjectId oursId = repository.resolve(Constants.HEAD);
            RevCommit ours = oursId == null ? null : walk.parseCommit(oursId);
            RevCommit theirs = theirsId == null ? null : walk.parseCommit(theirsId);

            return switch (found) {
                case SAME -> report(found, List.of(), List.of());
                case EMPTY, BEHIND -> {
                    book.push(credentials, timeout);
                    yield report(found, List.of(), List.of());
                }
                case AHEAD -> report(found, moveTo(ours, theirs), List.of());
                case DIVERGED -> merge(walk, ours, theirs, credentials, timeout);
                case UNRELATED -> throw new GitCommandException(RemoteState.UNRELATED.message());
                default -> throw new IllegalStateException("Not a comparison of the book with its remote: " + found);
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot synchronize the book", e);
        }
    }

    /**
     * Brings in what the remote has, changing nothing the author sees, and compares it with the book.
     *
     * @param credentials the username and token of an HTTPS remote; none for a folder
     * @param timeout     how long to wait for the host to answer; zero for JGit's default
     * @return {@link RemoteState#EMPTY}, {@code SAME}, {@code BEHIND}, {@code AHEAD}, {@code DIVERGED} or
     *         {@code UNRELATED}
     * @throws RemoteStateException if the remote cannot be reached or refuses the credentials
     */
    RemoteState check(Optional<Credentials> credentials, Duration timeout) {

        fetch(credentials, timeout);
        try {
            ObjectId theirs = repository.resolve(TRACKING);
            return theirs == null ? RemoteState.EMPTY : BookRemote.compare(repository, theirs);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot compare the book with its remote", e);
        }
    }

    /**
     * Puts the remote's book in place of this one, which Test found empty with the same title: its files, history
     * and properties, silently overwriting what is in the way. The empty book's own commits are left behind.
     *
     * @return the identifier of the book now in the folder, the remote's
     * @throws GitCommandException if the remote cannot be reached, or holds no book
     */
    String adopt(Optional<Credentials> credentials) {

        requireMain();
        fetch(credentials, Duration.ZERO);
        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId theirsId = repository.resolve(TRACKING);
            if (theirsId == null)
                throw new GitCommandException(RemoteState.EMPTY.message());

            // As git reset --hard: the remote's files over the index, whatever this book had.
            RevCommit theirs = walk.parseCommit(theirsId);
            checkout(null, theirs, false);

            RefUpdate update = repository.updateRef(MAIN);
            update.setNewObjectId(theirs);
            update.setForceUpdate(true);
            update.setRefLogMessage("synchronize: the remote's book in place of an empty one", false);
            RefUpdate.Result result = update.update();
            if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED)
                throw new GitCommandException("Cannot bring in the remote's book (%s)".formatted(result));

            return BookRemote.propertiesIn(repository, theirs)
                    .map(properties -> properties.getProperty(BookFolder.IDENTIFIER_KEY, ""))
                    .orElse("");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot bring in the remote's book", e);
        }
    }

    /** Merges {@code theirs} into {@code ours} as a commit, puts the book's folder in step with it, and sends it. */
    private SyncReport merge(RevWalk walk, RevCommit ours, RevCommit theirs, Optional<Credentials> credentials,
                             Duration timeout) throws IOException {

        RevCommit base = mergeBase(walk, ours, theirs);
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            BookMerge merge = new BookMerge(repository, inserter);

            //the merge as a commit with both parents
            CommitBuilder commit = new CommitBuilder();
            PersonIdent author = new PersonIdent(repository);
            commit.setTreeId(merge.tree(base, ours, theirs));
            commit.setParentIds(ours, theirs);
            commit.setAuthor(author);
            commit.setCommitter(author);
            commit.setMessage(MERGE_MESSAGE);
            ObjectId mergeId = inserter.insert(commit);
            inserter.flush();

            List<Path> changed = moveTo(ours, walk.parseCommit(mergeId));
            book.push(credentials, timeout);
            return report(RemoteState.DIVERGED, changed, merge.conflicts());
        }
    }

    /**
     * Puts the book's folder and {@code main} at {@code target}, from {@code current}, its last commit or none yet.
     *
     * @return the files that changed on disk, as absolute paths
     */
    private List<Path> moveTo(RevCommit current, RevCommit target) throws IOException {

        List<Path> changed = changedFiles(current, target);

        //the folder first: if a file cannot be written, main stays where it was
        checkout(current, target, true);

        RefUpdate update = repository.updateRef(MAIN);
        update.setNewObjectId(target);
        update.setExpectedOldObjectId(current == null ? ObjectId.zeroId() : current);
        update.setRefLogMessage("synchronize: " + target.getShortMessage(), false);
        RefUpdate.Result result = update.update();
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD)
            throw new GitCommandException("Cannot move the book to the remote's revisions (%s)".formatted(result));

        return changed;
    }

    /**
     * Writes {@code target}'s files over {@code current}'s, or over a folder without commits, in the book's folder and
     * in the index.
     *
     * @param safely true to refuse, changing nothing, if a file not committed is in the way; false to overwrite it
     * @throws UncommittedWorkException if {@code safely} and a file not committed is in the way
     */
    private void checkout(RevCommit current, RevCommit target, boolean safely) throws IOException {

        DirCache index = repository.lockDirCache();
        try {
            DirCacheCheckout checkout = current == null
                    ? new DirCacheCheckout(repository, index, target.getTree())
                    : new DirCacheCheckout(repository, current.getTree(), index, target.getTree());
            checkout.setFailOnConflict(safely);
            checkout.checkout();
        } catch (CheckoutConflictException e) {
            throw new UncommittedWorkException();
        } finally {
            index.unlock();
        }
    }

    /** The files that differ between {@code from}, or no commit, and {@code to}, as absolute paths. */
    private List<Path> changedFiles(RevCommit from, RevCommit to) throws IOException {

        List<Path> changed = new ArrayList<>();
        try (ObjectReader reader = repository.newObjectReader(); TreeWalk tree = new TreeWalk(reader)) {
            if (from == null)
                tree.addTree(new EmptyTreeIterator());
            else
                tree.addTree(new CanonicalTreeParser(null, reader, from.getTree()));
            tree.addTree(to.getTree());
            tree.setRecursive(true);
            tree.setFilter(TreeFilter.ANY_DIFF);
            while (tree.next())
                changed.add(file(tree.getPathString()));
        }

        return changed;
    }

    /** The last commit both {@code ours} and {@code theirs} come from. */
    private static RevCommit mergeBase(RevWalk walk, RevCommit ours, RevCommit theirs) throws IOException {

        walk.reset();
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(ours);
        walk.markStart(theirs);
        RevCommit base = walk.next();
        walk.reset();
        walk.setRevFilter(RevFilter.ALL);

        return base;
    }

    /**
     * Brings in the remote's branches, with the author's credentials for an HTTPS remote.
     *
     * @param timeout how long to wait for the host to answer; zero for JGit's default
     */
    private void fetch(Optional<Credentials> credentials, Duration timeout) {

        try {
            Git.wrap(repository).fetch()
                    .setRemote(BookRepository.REMOTE)
                    .setRemoveDeletedRefs(true)
                    .setCredentialsProvider(BookRemote.provider(credentials))
                    .setTimeout(Math.toIntExact(timeout.toSeconds()))
                    .call();
        } catch (GitAPIException e) {
            throw RemoteErrors.state(e)
                    .<GitCommandException>map(state -> new RemoteStateException(state, e))
                    .orElseGet(() -> new GitCommandException(
                            "Cannot bring in the remote's revisions: " + RemoteErrors.cleaned(e.getMessage()), e));
        }
    }

    private void requireMain() {

        try {
            String branch = repository.getFullBranch();
            if (!MAIN.equals(branch))
                throw new GitCommandException("Synchronize works on branch %s only; the book is on %s"
                        .formatted(BookRepository.DEFAULT_BRANCH, Repository.shortenRefName(String.valueOf(branch))));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read HEAD", e);
        }
    }

    private Path file(String path) {

        return repository.getWorkTree().toPath().resolve(path);
    }

    private static SyncReport report(RemoteState found, List<Path> changed, List<Conflict> conflicts) {

        return new SyncReport(found, List.copyOf(changed), List.copyOf(conflicts));
    }
}
