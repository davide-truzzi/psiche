package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevisionFilesTest extends GitBookTest {

    @Test
    void writesTheFoldersAsTheCommitLeftThemNotAsTheyAreNow() throws Exception {
        write("chapters/01-dawn.md", "It began at dawn.\n");
        write("chapters/02-noon.md", "Noon.\n");
        write("media/book.properties", "title=Salt\n");
        RevCommit then = commitAll("First draft");
        write("chapters/01-dawn.md", "It began at dusk.\n");
        Files.delete(bookRoot.resolve("chapters/02-noon.md"));
        write("chapters/03-night.md", "Night.\n");
        commitAll("Rework");

        Path copy = writeFolders(then);

        assertThat(files(copy)).containsExactlyInAnyOrder("chapters/01-dawn.md", "chapters/02-noon.md",
                "media/book.properties");
        assertThat(copy.resolve("chapters/01-dawn.md")).hasContent("It began at dawn.");
    }

    @Test
    void imagesAreWrittenByteForByte() throws Exception {
        byte[] image = new byte[256];
        for (int i = 0; i < image.length; i++)
            image[i] = (byte) i;
        Files.createDirectories(bookRoot.resolve("chapters/01-dawn"));
        Files.write(bookRoot.resolve("chapters/01-dawn/map.png"), image);
        write("chapters/01-dawn.md", "![Map](01-dawn/map.png)\n");
        RevCommit commit = commitAll("Add a map");

        Path copy = writeFolders(commit);

        assertThat(copy.resolve("chapters/01-dawn/map.png")).hasBinaryContent(image);
    }

    @Test
    void aFolderTheCommitHadNoFileInIsMadeEmpty() throws Exception {
        write("chapters/01-dawn.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        Path copy = writeFolders(commit);

        assertThat(copy.resolve("media")).isEmptyDirectory();
    }

    @Test
    void otherFilesOfTheBookAreLeftOut() throws Exception {
        write("chapters/01-dawn.md", "It began at dawn.\n");
        write("notes/01-dawn/idea.md", "An idea.\n");
        write(".gitignore", "psiche.properties\n");
        RevCommit commit = commitAll("First draft");

        assertThat(files(writeFolders(commit))).containsExactly("chapters/01-dawn.md");
    }

    @Test
    void refusesAFolderThatIsNotEmpty() throws Exception {
        write("chapters/01-dawn.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");
        Path target = Files.createDirectories(tempDir.resolve("copy"));
        Files.writeString(target.resolve("left.txt"), "Something left here.");

        assertThatThrownBy(() -> repository.history().writeFolders(commit.getName(), folders(), target))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(target.resolve("left.txt")).hasContent("Something left here.");
    }

    @Test
    void refusesAnIdThatIsNotACommit() throws Exception {
        write("chapters/01-dawn.md", "It began at dawn.\n");
        commitAll("First draft");
        String unknown = "0123456789012345678901234567890123456789";

        assertThatThrownBy(() -> repository.history().writeFolders(unknown, folders(), tempDir.resolve("copy")))
                .isInstanceOf(GitCommandException.class)
                .hasMessage(unknown + " is not a commit of this book");
    }

    private Path writeFolders(RevCommit commit) {
        Path target = tempDir.resolve("copy");
        repository.history().writeFolders(commit.getName(), folders(), target);
        return target;
    }

    private List<Path> folders() {
        return List.of(bookRoot.resolve("chapters"), bookRoot.resolve("media"));
    }

    /** The files under {@code folder}, as paths relative to it with forward slashes. */
    private static List<String> files(Path folder) throws Exception {
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> folder.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
    }
}
