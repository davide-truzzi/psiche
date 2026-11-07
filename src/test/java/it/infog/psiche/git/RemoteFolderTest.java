package it.infog.psiche.git;

import it.infog.psiche.git.model.enums.FolderState;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteFolderTest {

    @TempDir
    Path tempDir;

    @Test
    void anyFolderGetsTheBooksRepositoryInPsichesOwn() throws IOException {

        Path drive = folder("USB");
        Files.writeString(drive.resolve("holiday.jpg"), "");

        assertThat(RemoteFolder.state(drive, book("alba"))).isEqualTo(FolderState.NEW_REPOSITORY);
        assertThat(RemoteFolder.repository(drive, book("alba")))
                .isEqualTo(drive.resolve(RemoteFolder.BACKUPS).resolve("alba.git"));
    }

    @Test
    void psichesOwnFolderMayBeChosenItself() throws Exception {

        Path drive = folder("USB");
        Path made = RemoteFolder.createRepository(drive, book("alba"));
        Path backups = drive.resolve(RemoteFolder.BACKUPS);

        assertThat(RemoteFolder.state(backups, book("alba"))).isEqualTo(FolderState.BOOK_REPOSITORY);
        assertThat(RemoteFolder.repository(backups, book("alba"))).isEqualTo(made);
    }

    @Test
    void hiddenFilesSystemsLeaveInPsichesFolderDoNotCount() throws Exception {

        Path drive = folder("USB");
        RemoteFolder.createRepository(drive, book("sera"));
        Files.writeString(drive.resolve(RemoteFolder.BACKUPS).resolve(".DS_Store"), "");

        assertThat(RemoteFolder.state(drive, book("alba"))).isEqualTo(FolderState.NEW_REPOSITORY);
    }

    @Test
    void aFolderOfOtherBooksRepositoriesGetsThisOneToo() throws Exception {

        Path backup = folder("Backup");
        RemoteFolder.createRepository(backup, book("sera"));

        assertThat(RemoteFolder.state(backup, book("alba"))).isEqualTo(FolderState.NEW_REPOSITORY);
    }

    @Test
    void aFolderWithTheBooksRepositoryUsesIt() throws Exception {

        Path backup = folder("Backup");
        Path made = RemoteFolder.createRepository(backup, book("alba"));

        assertThat(RemoteFolder.state(backup, book("alba"))).isEqualTo(FolderState.BOOK_REPOSITORY);
        assertThat(RemoteFolder.repository(backup, book("alba"))).isEqualTo(made);
        assertThat(RemoteFolder.createRepository(backup, book("alba"))).isEqualTo(made);
    }

    @Test
    void aBareRepositoryIsUsedAsItIs() throws Exception {

        Path bare = folder("anything.git");
        Git.init().setBare(true).setDirectory(bare.toFile()).call().close();

        assertThat(RemoteFolder.state(bare, book("alba"))).isEqualTo(FolderState.REPOSITORY);
        assertThat(RemoteFolder.repository(bare, book("alba"))).isEqualTo(bare);
    }

    @Test
    void aMadeRepositoryIsBareOnMain() throws Exception {

        Path made = RemoteFolder.createRepository(folder("Backup"), book("alba"));

        try (Git git = Git.open(made.toFile())) {
            assertThat(git.getRepository().isBare()).isTrue();
            assertThat(git.getRepository().getFullBranch()).isEqualTo("refs/heads/main");
        }
    }

    @Test
    void psichesFolderWithOtherFilesIsRefused() throws IOException {

        Path drive = folder("USB");
        Files.createDirectories(drive.resolve(RemoteFolder.BACKUPS));
        Files.writeString(drive.resolve(RemoteFolder.BACKUPS).resolve("holiday.jpg"), "");

        assertThat(RemoteFolder.state(drive, book("alba"))).isEqualTo(FolderState.HAS_FILES);
    }

    @Test
    void aFileNamedLikePsichesFolderIsRefused() throws IOException {

        Path drive = folder("USB");
        Files.writeString(drive.resolve(RemoteFolder.BACKUPS), "");

        assertThat(RemoteFolder.state(drive, book("alba"))).isEqualTo(FolderState.HAS_FILES);
    }

    @Test
    void aWorkingCopyIsRefused() throws Exception {

        Path copy = folder("copy");
        Git.init().setDirectory(copy.toFile()).call().close();

        assertThat(RemoteFolder.state(copy, book("alba"))).isEqualTo(FolderState.WORKING_REPOSITORY);
        assertThat(RemoteFolder.state(copy.resolve(".git"), book("alba"))).isEqualTo(FolderState.WORKING_REPOSITORY);
    }

    @Test
    void theBooksOwnFolderOrOneInsideItIsRefused() throws IOException {

        Path book = book("alba");
        Path inside = Files.createDirectories(book.resolve("backup"));

        assertThat(RemoteFolder.state(book, book)).isEqualTo(FolderState.INSIDE_BOOK);
        assertThat(RemoteFolder.state(inside, book)).isEqualTo(FolderState.INSIDE_BOOK);
    }

    @Test
    void aMissingFolderIsMissing() throws IOException {

        assertThat(RemoteFolder.state(tempDir.resolve("unplugged"), book("alba"))).isEqualTo(FolderState.MISSING);
    }

    @Test
    void aFolderOnTheBooksDriveIsOnTheSamePartition() throws IOException {

        assertThat(RemoteFolder.samePartition(folder("Backup"), book("alba"))).isTrue();
    }

    private Path folder(String name) throws IOException {

        return Files.createDirectories(tempDir.resolve(name));
    }

    private Path book(String name) throws IOException {

        return Files.createDirectories(tempDir.resolve("books").resolve(name));
    }
}
