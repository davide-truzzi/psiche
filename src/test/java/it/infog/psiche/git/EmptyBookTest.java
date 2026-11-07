package it.infog.psiche.git;

import it.infog.psiche.git.model.enums.RemoteState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An empty book, just created on a second device, connected to a repository that holds the book from the first: it
 * takes that book in its place when the titles match.
 */
class EmptyBookTest extends GitBookTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String PROPERTIES = "media/book.properties";
    private static final String FIRST_DEVICE = "title=Lorem\nidentifier=urn:uuid:first-device\n";

    private Path hosted;
    private ObjectId firstDevicesHead;

    @BeforeEach
    void writeTheBookOnTheFirstDevice() throws Exception {

        hosted = bare("hosted.git");
        Path first = tempDir.resolve("first-device");
        try (Git device = Git.init().setDirectory(first.toFile()).setInitialBranch(BRANCH).call()) {
            Files.createDirectories(first.resolve("chapters"));
            Files.createDirectories(first.resolve("media"));
            Files.writeString(first.resolve("chapters/01-departure.md"), DEPARTURE);
            Files.writeString(first.resolve(PROPERTIES), FIRST_DEVICE);
            Files.writeString(first.resolve("media/style.css"), "body { margin: 0; }\n");
            device.add().addFilepattern(".").call();
            firstDevicesHead = device.commit().setMessage("First draft").call().getId();
            device.push().setRemote(hosted.toString()).setRefSpecs(new RefSpec("HEAD:refs/heads/" + BRANCH)).call();
        }

        // As Psiche creates a book: Git's own file committed, the properties and the stylesheet not yet.
        write(".gitignore", "psiche.properties\n");
        commitAll("Keep Psiche's own file out of Git");
        write(PROPERTIES, "title=lorem \nidentifier=urn:uuid:second-device\n");
        write("media/style.css", "body { margin: 1em; }\n");
        Files.createDirectories(bookRoot.resolve("chapters"));
    }

    @Test
    void anEmptyBookOfTheSameTitleCanTakeTheRepositorysBook() {

        assertThat(test(Optional.of(" Lorem"))).isEqualTo(RemoteState.HOLDS_BOOK);
    }

    @Test
    void anEmptyBookOfAnotherTitleIsToldSo() {

        assertThat(test(Optional.of("Ipsum"))).isEqualTo(RemoteState.OTHER_TITLE);
    }

    @Test
    void aBookWithChaptersStillFindsTheRepositoryUnrelated() {

        assertThat(test(Optional.empty())).isEqualTo(RemoteState.UNRELATED);
    }

    @Test
    void takingTheBookOverwritesTheEmptyOneSilently() throws Exception {

        repository.remote().connect(folder(hosted));

        String identifier = repository.adopt(Optional.empty());

        assertThat(identifier).isEqualTo("urn:uuid:first-device");
        assertThat(git.getRepository().resolve("HEAD")).isEqualTo(firstDevicesHead);
        assertThat(bookRoot.resolve("chapters/01-departure.md")).hasContent(DEPARTURE.strip());
        assertThat(bookRoot.resolve(PROPERTIES)).hasContent(FIRST_DEVICE.strip());
        assertThat(bookRoot.resolve("media/style.css")).hasContent("body { margin: 0; }");
        assertThat(bookRoot.resolve(".gitignore")).doesNotExist();
        assertThat(git.status().call().isClean()).isTrue();
    }

    private RemoteState test(Optional<String> emptyBookTitle) {

        return repository.remote().test(folder(hosted), Optional.empty(), emptyBookTitle, TIMEOUT);
    }
}
