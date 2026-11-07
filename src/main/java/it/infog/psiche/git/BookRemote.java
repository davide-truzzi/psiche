package it.infog.psiche.git;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.common.PropertiesFiles;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.FolderState;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.git.model.enums.RemoteState;
import it.infog.psiche.settings.model.Credentials;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * The book's remote repository, {@code origin} in the repository's configuration, so command-line Git sees it too,
 * with branch {@code main} following it. Only its address is kept there, never a username or a token.
 * <p>
 * Test checks an address before it is saved, without changing the book: that the repository can be reached and
 * written to, and what it holds compared with the book.
 */
public final class BookRemote {

    // Where Test fetches the remote's main branch, deleted as soon as it is compared.
    static final String TEST_REF = "refs/psiche/test/" + BookRepository.DEFAULT_BRANCH;

    private static final String MAIN = Constants.R_HEADS + BookRepository.DEFAULT_BRANCH;
    private static final String TRACKING = Constants.R_REMOTES + BookRepository.REMOTE + "/";
    private static final String FETCH_SPEC = "+" + Constants.R_HEADS + "*:" + TRACKING + "*";

    private final Repository repository;

    BookRemote(Repository repository) {

        this.repository = repository;
    }

    /** The remote's address, an {@code https://} address or a repository's folder; empty if the book has none. */
    public Optional<String> address() {

        return Optional.ofNullable(repository.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION,
                BookRepository.REMOTE, ConfigConstants.CONFIG_KEY_URL));
    }

    /**
     * Makes {@code address} the book's remote, in place of any it had. A folder for backups gets the book's own
     * repository, made now if it is not there yet.
     *
     * @return the address saved: for a folder, its repository's
     * @throws GitCommandException if the folder cannot be the book's remote, or its repository cannot be made
     */
    public String connect(RemoteAddress address) {

        String saved = switch (address.kind()) {
            case HTTPS -> address.address();
            case FOLDER -> folderRepository(Path.of(address.address())).toString();
        };

        StoredConfig config = repository.getConfig();
        try {
            RemoteConfig remote = new RemoteConfig(config, BookRepository.REMOTE);
            List.copyOf(remote.getURIs()).forEach(remote::removeURI);
            List.copyOf(remote.getPushURIs()).forEach(remote::removePushURI);
            remote.addURI(new URIish(saved));
            remote.setFetchRefSpecs(List.of(new RefSpec(FETCH_SPEC)));
            remote.update(config);

            //main follows the remote's main, as git pull and git status expect
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, BookRepository.DEFAULT_BRANCH,
                    ConfigConstants.CONFIG_KEY_REMOTE, BookRepository.REMOTE);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, BookRepository.DEFAULT_BRANCH,
                    ConfigConstants.CONFIG_KEY_MERGE, MAIN);
            config.save();
        } catch (URISyntaxException e) {
            throw new GitCommandException("Not a repository's address: " + RemoteErrors.cleaned(saved), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the book's remote", e);
        }

        return saved;
    }

    /** Disconnects the book from its remote, forgetting what it knew of the remote's branches. */
    public void remove() {

        StoredConfig config = repository.getConfig();
        config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, BookRepository.REMOTE);
        config.unset(ConfigConstants.CONFIG_BRANCH_SECTION, BookRepository.DEFAULT_BRANCH,
                ConfigConstants.CONFIG_KEY_REMOTE);
        config.unset(ConfigConstants.CONFIG_BRANCH_SECTION, BookRepository.DEFAULT_BRANCH,
                ConfigConstants.CONFIG_KEY_MERGE);
        try {
            config.save();
            for (Ref tracking : repository.getRefDatabase().getRefsByPrefix(TRACKING))
                delete(tracking.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove the book's remote", e);
        }
    }

    /**
     * Checks {@code address} as the book's remote, changing nothing the author sees: that it can be reached and
     * written to with {@code credentials}, and what it holds compared with the book. A folder for backups that does
     * not have the book's repository yet is empty. An empty book, one without chapters, may take the repository's
     * book in its place if that has its title: {@link RemoteState#HOLDS_BOOK}, see {@link BookRepository#adopt}.
     *
     * @param emptyBookTitle the book's title, if it has no chapters; empty for a book that has some
     * @param timeout        how long to wait for the host to answer
     * @throws GitCommandException for a failure none of the {@link RemoteState}s describe
     */
    public RemoteState test(RemoteAddress address, Optional<Credentials> credentials, Optional<String> emptyBookTitle,
                            Duration timeout) {

        String url = address.address();
        if (address.kind() == RemoteKind.FOLDER) {
            Path folder = Path.of(url);
            FolderState state = RemoteFolder.state(folder, workTree());
            if (!state.usable())
                return RemoteState.NOT_A_REPOSITORY;

            if (state == FolderState.NEW_REPOSITORY)
                return RemoteState.EMPTY;

            url = RemoteFolder.repository(folder, workTree()).toString();
        }

        try (Transport transport = Transport.open(repository, new URIish(url))) {
            transport.setTimeout(Math.toIntExact(timeout.toSeconds()));
            transport.setCredentialsProvider(provider(credentials));

            //the right to write, which hosts ask for as a push connection opens; nothing is sent
            transport.openPush().close();

            //the remote's main, if it has one, fetched beside the book's to compare them
            Ref main;
            try (FetchConnection fetch = transport.openFetch()) {
                main = fetch.getRef(MAIN);
            }
            if (main == null)
                return RemoteState.EMPTY;

            transport.fetch(NullProgressMonitor.INSTANCE, List.of(new RefSpec("+" + MAIN + ":" + TEST_REF)));
            ObjectId fetched = repository.resolve(TEST_REF);
            RemoteState state = compare(repository, fetched);
            boolean ownHistory = state == RemoteState.UNRELATED || repository.resolve(Constants.HEAD) == null;
            if (emptyBookTitle.isEmpty() || !ownHistory)
                return state;

            //an empty book, which may take the repository's if it is the same book
            Optional<String> title = propertiesIn(repository, fetched)
                    .map(properties -> properties.getProperty(BookFolder.TITLE_KEY));
            if (title.isEmpty())
                return state;

            return title.get().strip().equalsIgnoreCase(emptyBookTitle.get().strip())
                    ? RemoteState.HOLDS_BOOK : RemoteState.OTHER_TITLE;
        } catch (URISyntaxException e) {
            return RemoteState.NOT_A_REPOSITORY;
        } catch (IOException e) {
            return RemoteErrors.state(e).orElseThrow(() -> new GitCommandException(
                    "Test failed: " + RemoteErrors.cleaned(e.getMessage()), e));
        } finally {
            delete(TEST_REF);
        }
    }

    /** The book's properties as {@code commit} has them; empty if it has none. */
    static Optional<Properties> propertiesIn(Repository repository, ObjectId commit) throws IOException {

        try (RevWalk walk = new RevWalk(repository);
             TreeWalk file = TreeWalk.forPath(repository, BookFolder.PROPERTIES_PATH,
                     walk.parseCommit(commit).getTree())) {
            if (file == null)
                return Optional.empty();

            byte[] bytes = repository.open(file.getObjectId(0), Constants.OBJ_BLOB).getBytes();
            Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
            return Optional.of(PropertiesFiles.load(reader, BookFolder.PROPERTIES_PATH));
        }
    }

    /** What JGit asks for the credentials of a remote over HTTPS; none for a folder, or a book without a token. */
    static CredentialsProvider provider(Optional<Credentials> credentials) {

        return credentials
                .map(given -> new UsernamePasswordCredentialsProvider(given.username(), given.token().toCharArray()))
                .orElse(null);
    }

    /** The book's repository in {@code folder}, made if it is not there yet. */
    private Path folderRepository(Path folder) {

        FolderState state = RemoteFolder.state(folder, workTree());
        if (!state.usable())
            throw new GitCommandException(state.problem());

        return RemoteFolder.createRepository(folder, workTree());
    }

    /**
     * The remote's commit {@code fetched} compared with the book's last one: {@link RemoteState#SAME}, {@code BEHIND},
     * {@code AHEAD}, {@code DIVERGED} or {@code UNRELATED}.
     */
    static RemoteState compare(Repository repository, ObjectId fetched) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null)
            return RemoteState.AHEAD;

        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit local = walk.parseCommit(head);
            RevCommit remote = walk.parseCommit(fetched);
            if (local.equals(remote))
                return RemoteState.SAME;

            if (walk.isMergedInto(remote, local))
                return RemoteState.BEHIND;

            walk.reset();
            if (walk.isMergedInto(local, remote))
                return RemoteState.AHEAD;

            //a shared start, or none at all
            walk.reset();
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(local);
            walk.markStart(remote);
            return walk.next() != null ? RemoteState.DIVERGED : RemoteState.UNRELATED;
        }
    }

    /** Deletes the ref {@code name}, if it is there. */
    private void delete(String name) {

        try {
            RefUpdate update = repository.updateRef(name);
            update.setForceUpdate(true);
            update.delete();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + name, e);
        }
    }

    private Path workTree() {

        return repository.getWorkTree().toPath();
    }
}
