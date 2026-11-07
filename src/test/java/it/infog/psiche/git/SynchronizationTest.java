package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.exceptions.RemoteStateException;
import it.infog.psiche.git.exceptions.UncommittedWorkException;
import it.infog.psiche.git.model.Conflict;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.git.model.enums.RemoteState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synchronize, with a local bare repository standing for the remote and a clone of it for another device. */
class SynchronizationTest extends GitBookTest {

    private static final String DEPARTURE_FILE = "chapters/01-departure.md";
    // A copy made today, as the other device's commits are made today.
    private static final String DEPARTURE_COPY = "chapters/01-departure (other device, %s).md"
            .formatted(LocalDate.now());

    private Path backup;
    private Path device;
    private Git other;

    @BeforeEach
    void connectTheBook() throws Exception {

        backup = Files.createDirectories(tempDir.resolve("Backup"));
        write(DEPARTURE_FILE, DEPARTURE);
        commitAll("First draft");
        String saved = repository.remote().connect(folder(backup));
        repository.push(Optional.empty(), Duration.ZERO);

        device = tempDir.resolve("other-device");
        other = Git.cloneRepository().setURI(saved).setDirectory(device.toFile()).call();
    }

    @AfterEach
    void closeTheOtherDevice() {

        other.close();
    }

    @Test
    void aRemoteWithTheBookAsItIsNeedsNothing() throws Exception {

        ObjectId head = head();

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.found()).isEqualTo(RemoteState.SAME);
        assertThat(report.changed()).isEmpty();
        assertThat(head()).isEqualTo(head);
    }

    @Test
    void theBooksNewRevisionsAreSent() throws Exception {

        write("chapters/02-crossing.md", "The river was high.\n");
        ObjectId crossing = commitAll("Crossing").getId();

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.found()).isEqualTo(RemoteState.BEHIND);
        assertThat(remoteMain()).isEqualTo(crossing);
    }

    @Test
    void anEmptyRemoteGetsTheBook() throws Exception {

        Path empty = bare("empty.git");
        git.getRepository().getConfig().setString("remote", "origin", "url", empty.toString());
        git.getRepository().getConfig().save();

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.found()).isEqualTo(RemoteState.EMPTY);
        try (Git remote = Git.open(empty.toFile())) {
            assertThat(remote.getRepository().resolve("refs/heads/" + BRANCH)).isEqualTo(head());
        }
    }

    @Test
    void theOtherDevicesRevisionsAreBroughtIn() throws Exception {

        RevCommit harbour = commitOnTheOtherDevice("chapters/03-harbour.md", "Ships at anchor.\n");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.found()).isEqualTo(RemoteState.AHEAD);
        assertThat(head()).isEqualTo(harbour.getId());
        assertThat(bookRoot.resolve("chapters/03-harbour.md")).hasContent("Ships at anchor.");
        assertThat(report.changed()).containsExactly(bookRoot.resolve("chapters/03-harbour.md"));
        assertThat(git.status().call().isClean()).isTrue();
    }

    @Test
    void aChapterDeletedOnTheOtherDeviceGoesHereToo() throws Exception {

        Files.delete(device.resolve(DEPARTURE_FILE));
        other.rm().addFilepattern(DEPARTURE_FILE).call();
        other.commit().setMessage("Delete departure").call();
        other.push().call();

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(bookRoot.resolve(DEPARTURE_FILE)).doesNotExist();
        assertThat(report.changed()).containsExactly(bookRoot.resolve(DEPARTURE_FILE));
    }

    @Test
    void differentChaptersChangedOnBothDevicesAreMergedAndSent() throws Exception {

        commitOnTheOtherDevice("chapters/03-harbour.md", "Ships at anchor.\n");
        write("chapters/02-crossing.md", "The river was high.\n");
        commitAll("Crossing");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.found()).isEqualTo(RemoteState.DIVERGED);
        assertThat(report.conflicts()).isEmpty();
        assertThat(report.changed()).containsExactly(bookRoot.resolve("chapters/03-harbour.md"));
        assertThat(bookRoot.resolve("chapters/02-crossing.md")).exists();
        assertThat(bookRoot.resolve("chapters/03-harbour.md")).exists();
        RevCommit merge = git.log().setMaxCount(1).call().iterator().next();
        assertThat(merge.getParentCount()).isEqualTo(2);
        assertThat(merge.getShortMessage()).isEqualTo("Merge with the remote");
        assertThat(remoteMain()).isEqualTo(merge.getId());
        assertThat(git.status().call().isClean()).isTrue();
    }

    @Test
    void differentLinesOfOneChapterAreMergedLineByLine() throws Exception {

        commitOnTheOtherDevice(DEPARTURE_FILE, DEPARTURE.replace("Nobody spoke.", "Nobody said a word."));
        write(DEPARTURE_FILE, DEPARTURE.replace("By noon", "By midday"));
        commitAll("Midday");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(report.conflicts()).isEmpty();
        assertThat(bookRoot.resolve(DEPARTURE_FILE)).content()
                .contains("Nobody said a word.", "By midday")
                .doesNotContain("<<<<<<<");
    }

    @Test
    void theSameLinesChangedOnBothDevicesKeepThisDevicesAndCopyTheOther() throws Exception {

        String theirs = DEPARTURE.replace("Nobody spoke.", "Nobody said a word.");
        String ours = DEPARTURE.replace("Nobody spoke.", "Everyone was silent.");
        commitOnTheOtherDevice(DEPARTURE_FILE, theirs);
        write(DEPARTURE_FILE, ours);
        commitAll("Silent");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        Path copy = bookRoot.resolve(DEPARTURE_COPY);
        assertThat(bookRoot.resolve(DEPARTURE_FILE)).hasContent(ours);
        assertThat(copy).hasContent(theirs);
        assertThat(report.conflicts()).containsExactly(new Conflict(bookRoot.resolve(DEPARTURE_FILE),
                Optional.of(copy)));
        assertThat(report.changed()).containsExactly(copy);
        assertThat(remoteMain()).isEqualTo(head());
        assertThat(git.status().call().isClean()).isTrue();
    }

    @Test
    void aCopyTakesANumberWhenItsNameIsTaken() throws Exception {

        write(DEPARTURE_COPY, "An older copy.\n");
        commitAll("An older copy");
        repository.synchronize(Optional.empty(), Duration.ZERO);
        other.pull().call();
        commitOnTheOtherDevice(DEPARTURE_FILE, "Theirs.\n");
        write(DEPARTURE_FILE, "Ours.\n");
        commitAll("Ours");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        Path numbered = bookRoot.resolve(DEPARTURE_COPY.replace(")", ", 2)"));
        assertThat(report.conflicts()).extracting(Conflict::copy).containsExactly(Optional.of(numbered));
        assertThat(numbered).hasContent("Theirs.");
        assertThat(bookRoot.resolve(DEPARTURE_COPY)).hasContent("An older copy.");
    }

    @Test
    void aChapterDeletedHereAndChangedThereComesBackAsACopy() throws Exception {

        commitOnTheOtherDevice(DEPARTURE_FILE, "Changed there.\n");
        Files.delete(bookRoot.resolve(DEPARTURE_FILE));
        commitAll("Delete departure");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(bookRoot.resolve(DEPARTURE_FILE)).doesNotExist();
        assertThat(bookRoot.resolve(DEPARTURE_COPY)).hasContent("Changed there.");
        assertThat(report.conflicts()).containsExactly(new Conflict(bookRoot.resolve(DEPARTURE_FILE),
                Optional.of(bookRoot.resolve(DEPARTURE_COPY))));
    }

    @Test
    void aChapterChangedHereAndDeletedThereStays() throws Exception {

        Files.delete(device.resolve(DEPARTURE_FILE));
        other.rm().addFilepattern(DEPARTURE_FILE).call();
        other.commit().setMessage("Delete departure").call();
        other.push().call();
        write(DEPARTURE_FILE, "Changed here.\n");
        commitAll("Change departure");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(bookRoot.resolve(DEPARTURE_FILE)).hasContent("Changed here.");
        assertThat(report.conflicts()).containsExactly(new Conflict(bookRoot.resolve(DEPARTURE_FILE),
                Optional.empty()));
    }

    @Test
    void anImageChangedOnBothDevicesKeepsThisDevicesWithoutACopy() throws Exception {

        String image = "media/01-departure/map.png";
        write(image, "\u0000PNG first");
        commitAll("Map");
        repository.synchronize(Optional.empty(), Duration.ZERO);
        other.pull().call();
        commitOnTheOtherDevice(image, "\u0000PNG theirs");
        write(image, "\u0000PNG ours");
        commitAll("Our map");

        SyncReport report = repository.synchronize(Optional.empty(), Duration.ZERO);

        assertThat(bookRoot.resolve(image)).hasContent("\u0000PNG ours");
        assertThat(report.conflicts()).containsExactly(new Conflict(bookRoot.resolve(image), Optional.empty()));
        assertThat(report.changed()).isEmpty();
    }

    @Test
    void uncommittedWorkBlocksIt() throws Exception {

        commitOnTheOtherDevice("chapters/03-harbour.md", "Ships at anchor.\n");
        write(DEPARTURE_FILE, "Not committed.\n");
        ObjectId head = head();

        assertThatThrownBy(() -> repository.synchronize(Optional.empty(), Duration.ZERO))
                .isInstanceOf(UncommittedWorkException.class);
        assertThat(head()).isEqualTo(head);
        assertThat(bookRoot.resolve("chapters/03-harbour.md")).doesNotExist();
    }

    @Test
    void aNewChapterNotCommittedBlocksIt() throws Exception {

        write("chapters/02-crossing.md", "Not committed.\n");

        assertThatThrownBy(() -> repository.synchronize(Optional.empty(), Duration.ZERO))
                .isInstanceOf(UncommittedWorkException.class);
    }

    @Test
    void aRemoteHoldingSomethingElseIsRefused() throws Exception {

        try (Git stranger = Git.init().setDirectory(tempDir.resolve("stranger").toFile()).setInitialBranch(BRANCH)
                .call()) {
            Files.writeString(tempDir.resolve("stranger/README.md"), "# alba");
            stranger.add().addFilepattern(".").call();
            stranger.commit().setMessage("Initial commit").call();
            stranger.push().setRemote(device.resolve(".git").toString()).setForce(true)
                    .setRefSpecs(new RefSpec("HEAD:refs/heads/elsewhere")).call();
        }
        other.checkout().setName("elsewhere").call();
        other.push().setForce(true).setRefSpecs(new RefSpec("refs/heads/elsewhere:refs/heads/" + BRANCH)).call();

        assertThatThrownBy(() -> repository.synchronize(Optional.empty(), Duration.ZERO))
                .isInstanceOf(GitCommandException.class)
                .hasMessage(RemoteState.UNRELATED.message());
    }

    @Test
    void theCheckAtOpenTellsWhatTheRemoteHasAndChangesNothing() throws Exception {

        assertThat(repository.checkRemote(Optional.empty(), Duration.ofSeconds(10))).isEqualTo(RemoteState.SAME);

        write("chapters/02-crossing.md", "The river was high.\n");
        commitAll("Crossing");
        assertThat(repository.checkRemote(Optional.empty(), Duration.ofSeconds(10))).isEqualTo(RemoteState.BEHIND);

        commitOnTheOtherDevice("chapters/03-harbour.md", "Ships at anchor.\n");
        ObjectId head = head();
        assertThat(repository.checkRemote(Optional.empty(), Duration.ofSeconds(10)))
                .isEqualTo(RemoteState.DIVERGED);
        assertThat(head()).isEqualTo(head);
        assertThat(bookRoot.resolve("chapters/03-harbour.md")).doesNotExist();
    }

    @Test
    void theCheckAtOpenFindsTheOtherDevicesRevisions() throws Exception {

        commitOnTheOtherDevice("chapters/03-harbour.md", "Ships at anchor.\n");

        assertThat(repository.checkRemote(Optional.empty(), Duration.ofSeconds(10))).isEqualTo(RemoteState.AHEAD);
    }

    @Test
    void aFolderRemoteThatIsNotThereCannotBeReached() throws Exception {

        // As a USB stick that is not plugged in.
        Path remote = Path.of(repository.remote().address().orElseThrow());
        Files.move(remote, tempDir.resolve("unplugged.git"));

        assertThatThrownBy(() -> repository.checkRemote(Optional.empty(), Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(RemoteStateException.class,
                        e -> assertThat(e.state()).isEqualTo(RemoteState.NOT_A_REPOSITORY));
        assertThatThrownBy(() -> repository.synchronize(Optional.empty(), Duration.ofSeconds(10)))
                .isInstanceOf(RemoteStateException.class);
    }

    /** Commits {@code file} with {@code text} on the other device, and sends it to the remote. */
    private RevCommit commitOnTheOtherDevice(String file, String text) throws Exception {

        return commitAndPush(other, file, text, BookRepository.REMOTE);
    }

    private ObjectId head() throws Exception {

        return git.getRepository().resolve("HEAD");
    }

    private ObjectId remoteMain() throws Exception {

        try (Git remote = Git.open(Path.of(repository.remote().address().orElseThrow()).toFile())) {
            return remote.getRepository().resolve("refs/heads/" + BRANCH);
        }
    }
}
