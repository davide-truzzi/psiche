package it.infog.psiche.git;

import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.RemoteKind;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A book folder with a Git repository, for the tests of {@link BookRepository} and {@link BookHistory}: files are
 * written, moved and committed as an author, or another program, would. For the remote's tests, bare repositories
 * stand for a host or a drive, and other copies of the book for other devices.
 */
abstract class GitBookTest {

    static final String BRANCH = "main";
    // Long enough that changing one line keeps it well above Git's 60% similarity threshold for renames.
    static final String DEPARTURE = """
            It began at dawn, when the salt carts were already loaded.
            The mules were stamping in the cold.
            Nobody spoke.
            The gate opened with a groan.
            By noon the road had turned white.
            The drivers did not look back.
            """;

    @TempDir
    Path tempDir;

    Path bookRoot;
    Git git;
    BookRepository repository;

    @BeforeEach
    void initBookRepository() throws Exception {
        bookRoot = Files.createDirectories(tempDir.resolve("book"));
        git = Git.init().setDirectory(bookRoot.toFile()).setInitialBranch(BRANCH).call();
        repository = BookRepository.open(bookRoot);
    }

    @AfterEach
    void close() {
        repository.close();
        git.close();
    }

    void move(String from, String to) throws IOException {
        Files.move(bookRoot.resolve(from), bookRoot.resolve(to));
    }

    void write(String relativePath, String content) throws IOException {
        Path file = bookRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    RevCommit commitAll(String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        return git.commit().setMessage(message).call();
    }

    /** An empty bare repository on main, {@code name} in the temporary folder, as a host or a drive keeps one. */
    Path bare(String name) throws Exception {

        Path folder = tempDir.resolve(name);
        Git.init().setBare(true).setDirectory(folder.toFile()).setInitialBranch(BRANCH).call().close();
        return folder;
    }

    /** {@code folder} as the book's remote, as the author picks it. */
    static RemoteAddress folder(Path folder) {

        return new RemoteAddress(RemoteKind.FOLDER, folder.toString());
    }

    /**
     * Writes {@code file} with {@code text} in {@code device}, another copy of the book or a host's own, commits it,
     * and pushes it to main on {@code remote}, a remote's name or a repository's folder.
     */
    static RevCommit commitAndPush(Git device, String file, String text, String remote) throws Exception {

        Path written = device.getRepository().getWorkTree().toPath().resolve(file);
        Files.createDirectories(written.getParent());
        Files.writeString(written, text);
        device.add().addFilepattern(".").call();
        RevCommit commit = device.commit().setMessage("From the other device").call();
        device.push().setRemote(remote).setRefSpecs(new RefSpec("HEAD:refs/heads/" + BRANCH)).call();

        return commit;
    }
}
