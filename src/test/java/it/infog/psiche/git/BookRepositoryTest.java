package it.infog.psiche.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.git.model.FileRevision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BookRepositoryTest extends GitBookTest {

    @Test
    void refusesFolderWithoutRepository() throws IOException {
        Path plainFolder = Files.createDirectories(tempDir.resolve("plain"));

        assertThatThrownBy(() -> BookRepository.open(plainFolder))
                .isInstanceOf(GitCommandException.class)
                .hasMessageStartingWith(plainFolder + " is not a Git repository");
    }

    @Test
    void initCreatesRepositoryThatCanCommitTheBook() throws Exception {
        Path newBook = Files.createDirectories(tempDir.resolve("new-book"));
        Files.writeString(newBook.resolve("01-departure.md"), "It began at dawn.");

        BookRepository.init(newBook);

        assertThat(newBook.resolve(".git")).isDirectory();
        try (BookRepository opened = BookRepository.open(newBook); Git newGit = Git.open(newBook.toFile())) {
            assertThat(newGit.getRepository().getBranch()).isEqualTo("main");
            assertThat(opened.commit("First draft").message()).isEqualTo("First draft");
        }
    }

    @Test
    void commitRecordsNewModifiedAndDeletedFilesWithoutStagingFirst() throws Exception {
        write("chapters/01-departure.md", "first draft");
        write("chapters/02-crossing.md", "cut this");
        commitAll("initial");
        write("chapters/01-departure.md", "second draft");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        write("media/map.png", "png");

        repository.commit("Revise departure");

        assertThat(git.status().call().isClean()).isTrue();
        RevCommit head = git.log().setMaxCount(1).call().iterator().next();
        try (var walk = new TreeWalk(git.getRepository())) {
            walk.addTree(head.getTree());
            walk.setRecursive(true);
            List<String> files = new ArrayList<>();
            while (walk.next()) {
                files.add(walk.getPathString());
            }
            assertThat(files).containsExactly("chapters/01-departure.md", "media/map.png");
        }
    }

    @Test
    void commitStripsTheMessage() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.");

        Commit commit = repository.commit("  Draft chapter one\n");

        RevCommit head = git.log().setMaxCount(1).call().iterator().next();
        assertThat(head.getFullMessage()).isEqualTo("Draft chapter one");
        assertThat(commit).isEqualTo(new Commit(head.abbreviate(7).name(), "Draft chapter one"));
        assertThat(git.status().call().isClean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void commitRejectsBlankMessageWithoutStaging(String message) throws Exception {
        write("chapters/01-departure.md", "It began at dawn.");

        assertThatThrownBy(() -> repository.commit(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Commit message must not be blank");
        assertThat(git.status().call().getAdded()).isEmpty();
    }

    @Test
    void commitWithoutChangesSinceLastCommitFails() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.");
        commitAll("initial");

        assertThatThrownBy(() -> repository.commit("No changes"))
                .isInstanceOf(GitCommandException.class)
                .hasMessage("Nothing to commit: no changes since the last commit");
    }

    @Test
    void pushUpdatesTheSameBranchOnOrigin() throws Exception {
        try (Git origin = bareOrigin()) {
            write("chapters/01-departure.md", "It began at dawn.");
            ObjectId head = commitAll("Draft chapter one").getId();

            String pushed = repository.push();

            assertThat(pushed).isEqualTo(BRANCH);
            assertThat(origin.getRepository().resolve("refs/heads/" + BRANCH)).isEqualTo(head);
        }
    }

    @Test
    void pushWithoutOriginFails() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.");
        commitAll("initial");

        assertThatThrownBy(() -> repository.push())
                .isInstanceOf(GitCommandException.class)
                .hasMessage("No 'origin' remote is configured for this book");
    }

    @Test
    void pushRejectedByOriginFails() throws Exception {
        try (Git origin = bareOrigin();
             Git coAuthor = Git.cloneRepository()
                     .setURI(origin.getRepository().getDirectory().toURI().toString())
                     .setDirectory(tempDir.resolve("co-author").toFile())
                     .call()) {
            Files.writeString(tempDir.resolve("co-author/preface.md"), "Their preface");
            coAuthor.add().addFilepattern(".").call();
            coAuthor.commit().setMessage("Add preface").call();
            coAuthor.push().setRefSpecs(new RefSpec("HEAD:refs/heads/" + BRANCH)).call();
            write("chapters/01-departure.md", "Unrelated history");
            commitAll("Draft chapter one");

            assertThatThrownBy(() -> repository.push())
                    .isInstanceOf(GitCommandException.class)
                    .hasMessageStartingWith("Push of refs/heads/main was rejected (REJECTED_NONFASTFORWARD)");
        }
    }

    @Test
    void uncommittedFilesAreTheChangedAndTheNewOnes() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        write("chapters/03-arrival.md", "Land.\n");
        commitAll("First draft");
        assertThat(repository.uncommittedFiles()).isEmpty();

        write("chapters/01-departure.md", DEPARTURE + "The end.\n");
        write("chapters/04-home.md", "Home at last.\n");
        Files.delete(bookRoot.resolve("chapters/03-arrival.md"));

        assertThat(repository.uncommittedFiles()).containsExactlyInAnyOrder(
                bookRoot.resolve("chapters/01-departure.md").toAbsolutePath().normalize(),
                bookRoot.resolve("chapters/04-home.md").toAbsolutePath().normalize());
    }

    @Test
    void everyFileIsUncommittedBeforeTheFirstCommit() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);

        assertThat(repository.uncommittedFiles())
                .containsExactly(bookRoot.resolve("chapters/01-departure.md").toAbsolutePath().normalize());
    }

    @Test
    void uncommittedChangesAreChangesToTrackedFilesOnly() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        assertThat(repository.hasUncommittedChanges()).isFalse();

        write("chapters/01-departure.md~", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        assertThat(repository.hasUncommittedChanges()).isFalse();

        write("chapters/01-departure.md", DEPARTURE + "The end.\n");
        assertThat(repository.hasUncommittedChanges()).isTrue();
        commitAll("Add an ending");

        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        assertThat(repository.hasUncommittedChanges()).isTrue();
    }

    @Test
    void isCommittedOnlyForAFileTheLastCommitHas() throws Exception {
        assertThat(repository.isCommitted(bookRoot.resolve("chapters/01-departure.md"))).isFalse();

        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("First draft");
        write("chapters/03-landfall.md", "Land at last.\n");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        commitAll("Drop the crossing");

        assertThat(repository.isCommitted(bookRoot.resolve("chapters/01-departure.md"))).isTrue();
        assertThat(repository.isCommitted(bookRoot.resolve("chapters/02-crossing.md"))).isFalse();
        write("chapters/04-home.md", "Home.\n");
        assertThat(repository.isCommitted(bookRoot.resolve("chapters/04-home.md"))).isFalse();
    }

    @Test
    void commitOnlyRecordsARenameAndLeavesUntrackedFilesOut() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        write("chapters/01-departure.md~", "An editor's backup.\n");
        write("chapters/02-crossing.md", "Not committed yet.\n");

        move("chapters/01-departure.md", "chapters/01-the-storm.md");
        Commit commit = repository.commitOnly("Rename chapter",
                List.of(bookRoot.resolve("chapters/01-departure.md"), bookRoot.resolve("chapters/01-the-storm.md")));

        assertThat(commit.message()).isEqualTo("Rename chapter");
        assertThat(repository.history().revisions(bookRoot.resolve("chapters/01-the-storm.md")))
                .extracting(FileRevision::message, FileRevision::renamedFrom)
                .containsExactly(tuple("Rename chapter", "chapters/01-departure.md"), tuple("First draft", null));
        assertThat(git.status().call().getUntracked())
                .containsExactlyInAnyOrder("chapters/01-departure.md~", "chapters/02-crossing.md");
        assertThat(repository.hasUncommittedChanges()).isFalse();
    }

    @Test
    void commitOnlyRecordsADeletionAndARestoredFile() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("First draft");
        write("notes.txt", "Not part of the book.\n");

        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        repository.commitOnly("Delete chapter", List.of(bookRoot.resolve("chapters/02-crossing.md")));
        assertThat(repository.history().deletedChapters(bookRoot.resolve("chapters")))
                .extracting(DeletedChapter::path).containsExactly("chapters/02-crossing.md");

        write("chapters/02-crossing.md", "The sea was calm.\n");
        repository.commitOnly("Restore chapter", List.of(bookRoot.resolve("chapters/02-crossing.md")));
        assertThat(repository.isCommitted(bookRoot.resolve("chapters/02-crossing.md"))).isTrue();
        assertThat(git.status().call().getUntracked()).containsExactly("notes.txt");
    }

    @Test
    void commitOnlyWithoutChangesToTheFilesFails() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        write("notes.txt", "Not part of the book.\n");

        assertThatThrownBy(() -> repository.commitOnly("Nothing", List.of(bookRoot.resolve("chapters/01-departure.md"))))
                .isInstanceOf(GitCommandException.class);
    }

    @Test
    void ignoringAPatternCommitsGitignoreAloneAndOnlyOnce() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        write("chapters/01-departure.md", DEPARTURE + "A new line, not committed.\n");

        assertThat(repository.ignores("notes/")).isFalse();
        assertThat(repository.ignore("notes/", "Keep chapter notes out of Git")).isTrue();
        assertThat(repository.ignores("notes/")).isTrue();
        assertThat(repository.ignore("notes/", "Keep chapter notes out of Git")).isFalse();

        assertThat(bookRoot.resolve(".gitignore")).hasContent("notes/");
        assertThat(git.log().call()).extracting(RevCommit::getShortMessage)
                .containsExactly("Keep chapter notes out of Git", "First draft");
        assertThat(git.status().call().getModified()).containsExactly("chapters/01-departure.md");
    }

    @Test
    void anIgnoredPatternGoesOnALineOfItsOwn() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write(".gitignore", "*.bak");
        commitAll("First draft");

        repository.ignore("notes/", "Keep chapter notes out of Git");

        assertThat(Files.readString(bookRoot.resolve(".gitignore"))).isEqualTo("*.bak\nnotes/\n");
    }

    @Test
    void aCommitLeavesIgnoredNotesOut() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        repository.ignore("notes/", "Keep chapter notes out of Git");
        write("notes/01-departure/20261101-093000-a1b2/note.md", "Marco lies about the letter.\n");
        write("chapters/01-departure.md", DEPARTURE + "The bells rang.\n");

        repository.commit("Bells");

        try (var walk = new TreeWalk(git.getRepository())) {
            walk.addTree(git.log().setMaxCount(1).call().iterator().next().getTree());
            walk.setRecursive(true);
            List<String> files = new ArrayList<>();
            while (walk.next())
                files.add(walk.getPathString());
            assertThat(files).containsExactly(".gitignore", "chapters/01-departure.md");
        }
        assertThat(repository.hasUncommittedChanges()).isFalse();
    }

    @Test
    void aBookWithoutCommitsCanIgnoreAPattern() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);

        assertThat(repository.ignore("notes/", "Keep chapter notes out of Git")).isTrue();

        assertThat(git.log().call()).extracting(RevCommit::getShortMessage)
                .containsExactly("Keep chapter notes out of Git");
        assertThat(git.status().call().getUntracked()).containsExactly("chapters/01-departure.md");
    }


    private Git bareOrigin() throws Exception {
        Path originDir = tempDir.resolve("origin.git");
        Git origin = Git.init().setBare(true).setDirectory(originDir.toFile()).setInitialBranch(BRANCH).call();
        git.remoteAdd().setName(BookRepository.REMOTE).setUri(new URIish(originDir.toUri().toString())).call();
        return origin;
    }
}
