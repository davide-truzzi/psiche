package it.infog.psiche.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A book folder with a Git repository, for the tests of {@link BookRepository} and {@link BookHistory}: files are
 * written, moved and committed as an author, or another program, would.
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
}
