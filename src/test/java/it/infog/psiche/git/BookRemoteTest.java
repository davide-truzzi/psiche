package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.FolderState;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.git.model.enums.RemoteState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The book's remote, with local bare repositories standing for a USB stick and a host. */
class BookRemoteTest extends GitBookTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private Path backup;
    private BookRemote remote;

    @BeforeEach
    void draftTheBook() throws Exception {

        backup = Files.createDirectories(tempDir.resolve("Backup"));
        remote = repository.remote();
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
    }

    @Test
    void aBookHasNoRemoteAtFirst() {

        assertThat(remote.address()).isEmpty();
    }

    @Test
    void anHttpsRemoteIsOriginWithMainFollowingIt() {

        String address = "https://github.com/davide/alba.git";

        assertThat(remote.connect(new RemoteAddress(RemoteKind.HTTPS, address))).isEqualTo(address);

        StoredConfig config = git.getRepository().getConfig();
        assertThat(remote.address()).contains(address);
        assertThat(config.getString("remote", "origin", "fetch")).isEqualTo("+refs/heads/*:refs/remotes/origin/*");
        assertThat(config.getString("branch", "main", "remote")).isEqualTo("origin");
        assertThat(config.getString("branch", "main", "merge")).isEqualTo("refs/heads/main");
    }

    @Test
    void connectingAgainReplacesTheAddress() {

        remote.connect(new RemoteAddress(RemoteKind.HTTPS, "https://github.com/davide/alba.git"));
        remote.connect(new RemoteAddress(RemoteKind.HTTPS, "https://gitlab.com/davide/alba.git"));

        assertThat(git.getRepository().getConfig().getStringList("remote", "origin", "url"))
                .containsExactly("https://gitlab.com/davide/alba.git");
    }

    @Test
    void aFolderGetsTheBooksBareRepositoryInPsichesOwn() throws IOException {

        Files.writeString(backup.resolve("holiday.jpg"), "");

        String saved = remote.connect(folder(backup));

        assertThat(saved).isEqualTo(bookRepository().toString());
        assertThat(RemoteFolder.isBareRepository(bookRepository())).isTrue();
        assertThat(remote.address()).contains(saved);
    }

    @Test
    void aFolderWhosePsicheRepositoryHoldsOtherFilesCannotBeConnected() throws IOException {

        Files.createDirectories(backup.resolve(RemoteFolder.BACKUPS));
        Files.writeString(backup.resolve(RemoteFolder.BACKUPS).resolve("holiday.jpg"), "");

        assertThatThrownBy(() -> remote.connect(folder(backup))).isInstanceOf(GitCommandException.class)
                .hasMessage(FolderState.HAS_FILES.problem());
        assertThat(remote.address()).isEmpty();
    }

    @Test
    void removingForgetsTheRemoteAndItsBranches() throws Exception {

        remote.connect(folder(backup));
        repository.push(Optional.empty());
        git.fetch().setRemote("origin").call();

        remote.remove();

        assertThat(remote.address()).isEmpty();
        assertThat(git.getRepository().getConfig().getSubsections("remote")).isEmpty();
        assertThat(git.getRepository().getConfig().getString("branch", "main", "remote")).isNull();
        assertThat(git.getRepository().getRefDatabase().getRefsByPrefix("refs/remotes/")).isEmpty();
    }

    @Test
    void aFolderWithoutTheBooksRepositoryYetIsEmpty() {

        assertThat(test(folder(backup))).isEqualTo(RemoteState.EMPTY);
        assertThat(backup).isEmptyDirectory();
    }

    @Test
    void anEmptyRepositoryIsEmpty() throws Exception {

        bare("empty.git").close();

        assertThat(test(folder(tempDir.resolve("empty.git"))))
                .isEqualTo(RemoteState.EMPTY);
    }

    @Test
    void aRepositoryWithTheBookAsItIsIsTheSame() {

        remote.connect(folder(backup));
        repository.push(Optional.empty());

        assertThat(test(folder(backup))).isEqualTo(RemoteState.SAME);
    }

    @Test
    void aRepositoryWithoutTheBooksNewRevisionsIsBehind() throws Exception {

        remote.connect(folder(backup));
        repository.push(Optional.empty());
        write("chapters/02-crossing.md", "The river was high.");
        commitAll("Crossing");

        assertThat(test(folder(backup))).isEqualTo(RemoteState.BEHIND);
    }

    @Test
    void aRepositoryWithRevisionsFromAnotherDeviceIsAhead() throws Exception {

        remote.connect(folder(backup));
        repository.push(Optional.empty());
        commitOnAnotherDevice("chapters/03-harbour.md", "Ships at anchor.");

        assertThat(test(folder(backup))).isEqualTo(RemoteState.AHEAD);
    }

    @Test
    void aRepositoryAndABookThatBothMovedOnHaveDiverged() throws Exception {

        remote.connect(folder(backup));
        repository.push(Optional.empty());
        commitOnAnotherDevice("chapters/03-harbour.md", "Ships at anchor.");
        write("chapters/02-crossing.md", "The river was high.");
        commitAll("Crossing");

        assertThat(test(folder(backup))).isEqualTo(RemoteState.DIVERGED);
    }

    @Test
    void aRepositoryStartedWithAReadmeIsUnrelated() throws Exception {

        Path hosted = tempDir.resolve("hosted.git");
        bare("hosted.git").close();
        try (Git host = Git.init().setDirectory(tempDir.resolve("host-readme").toFile()).setInitialBranch(BRANCH)
                .call()) {
            Files.writeString(tempDir.resolve("host-readme/README.md"), "# alba");
            host.add().addFilepattern(".").call();
            host.commit().setMessage("Initial commit").call();
            host.push().setRemote(hosted.toString()).setRefSpecs(new RefSpec("HEAD:refs/heads/" + BRANCH)).call();
        }

        assertThat(test(folder(hosted))).isEqualTo(RemoteState.UNRELATED);
    }

    @Test
    void aFolderThatCannotBeTheRemoteIsNotARepository() throws Exception {

        Git.init().setDirectory(backup.toFile()).call().close();

        assertThat(test(folder(backup))).isEqualTo(RemoteState.NOT_A_REPOSITORY);
    }

    @Test
    void testingChangesNeitherTheBookNorItsRemote() throws Exception {

        remote.connect(folder(backup));
        repository.push(Optional.empty());
        commitOnAnotherDevice("chapters/03-harbour.md", "Ships at anchor.");
        ObjectId head = git.getRepository().resolve("HEAD");

        test(folder(backup));

        assertThat(git.getRepository().resolve("HEAD")).isEqualTo(head);
        assertThat(git.getRepository().resolve(BookRemote.TEST_REF)).isNull();
        assertThat(git.getRepository().resolve("refs/remotes/origin/main")).isEqualTo(head);
        assertThat(git.status().call().isClean()).isTrue();
    }

    /** Commits {@code file} in a clone of the book's repository in the backup folder, and pushes it there. */
    private void commitOnAnotherDevice(String file, String text) throws Exception {

        Path device = tempDir.resolve("other-device");
        try (Git other = Git.cloneRepository().setURI(bookRepository().toString())
                .setDirectory(device.toFile()).call()) {
            Files.createDirectories(device.resolve(file).getParent());
            Files.writeString(device.resolve(file), text);
            other.add().addFilepattern(".").call();
            other.commit().setMessage("From the other device").call();
            other.push().call();
        }
    }

    /** The book's repository in the backup folder, as Psiche makes it. */
    private Path bookRepository() {

        return backup.resolve(RemoteFolder.BACKUPS).resolve("book.git");
    }

    private Git bare(String name) throws Exception {

        return Git.init().setBare(true).setDirectory(tempDir.resolve(name).toFile()).setInitialBranch(BRANCH).call();
    }

    /** Test, without credentials, for a book that has chapters. */
    private RemoteState test(RemoteAddress address) {

        return remote.test(address, Optional.empty(), Optional.empty(), TIMEOUT);
    }

    private static RemoteAddress folder(Path folder) {

        return new RemoteAddress(RemoteKind.FOLDER, folder.toString());
    }
}
