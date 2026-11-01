package it.infog.psiche.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.git.model.FileRevision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BookRepositoryTest {

    private static final String BRANCH = "main";
    // Long enough that changing one line keeps it well above Git's 60% similarity threshold for renames.
    private static final String DEPARTURE = """
            It began at dawn, when the salt carts were already loaded.
            The mules were stamping in the cold.
            Nobody spoke.
            The gate opened with a groan.
            By noon the road had turned white.
            The drivers did not look back.
            """;

    @TempDir
    Path tempDir;

    private Path bookRoot;
    private Git git;
    private BookRepository repository;

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
    void historyListsCommitsThatChangedTheFileNewestFirstWithLinesAddedAndRemoved() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\nNobody spoke.\nThe carts were loaded.\n");
        commitAll("First draft");
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("Start chapter two");
        write("chapters/01-departure.md",
                "It began at dawn.\nEveryone spoke at once.\nThe carts were loaded.\nThe road was white.\n");
        commitAll("Rework the opening");

        List<FileRevision> history = repository.history(bookRoot.resolve("chapters/01-departure.md"));

        assertThat(history)
                .extracting(FileRevision::message, FileRevision::linesAdded, FileRevision::linesRemoved)
                .containsExactly(tuple("Rework the opening", 2, 1), tuple("First draft", 3, 0));
    }

    @Test
    void historyIdentifiesEachCommitByShortIdAndTime() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        FileRevision revision = repository.history(bookRoot.resolve("chapters/01-departure.md")).getFirst();

        assertThat(revision.shortId()).isEqualTo(commit.abbreviate(7).name());
        assertThat(revision.time()).isEqualTo(Instant.ofEpochSecond(commit.getCommitTime()));
    }

    @Test
    void historyIsEmptyBeforeTheFirstCommit() throws IOException {
        write("chapters/01-departure.md", "It began at dawn.\n");

        assertThat(repository.history(bookRoot.resolve("chapters/01-departure.md"))).isEmpty();
    }

    @Test
    void historyIsEmptyForAFileNeverCommitted() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("chapters/02-crossing.md", "The sea was calm.\n");

        assertThat(repository.history(bookRoot.resolve("chapters/02-crossing.md"))).isEmpty();
    }

    @Test
    void historyIdentifiesTheFullCommitToReadTheFileFrom() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        FileRevision revision = repository.history(bookRoot.resolve("chapters/01-departure.md")).getFirst();

        assertThat(revision.commitId()).isEqualTo(commit.getName());
    }

    @Test
    void contentOfReadsTheFileAsEachCommitRecordedIt() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("chapters/01-departure.md", "It began at dusk. Perché?\n");
        commitAll("Move it to the evening");
        write("chapters/01-departure.md", "Not committed yet.\n");

        List<FileRevision> history = repository.history(bookRoot.resolve("chapters/01-departure.md"));

        assertThat(history).extracting(repository::contentOf).containsExactly(
                Optional.of("It began at dusk. Perché?\n"), Optional.of("It began at dawn.\n"));
    }

    @Test
    void contentOfIsEmptyForACommitWithoutTheFile() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        assertThat(repository.contentOf(revision(commit.getName(), "chapters/02-crossing.md"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-commit", "0123456789012345678901234567890123456789"})
    void contentOfRejectsAnIdThatIsNotACommit(String commitId) throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");

        assertThatThrownBy(() -> repository.contentOf(revision(commitId, "chapters/01-departure.md")))
                .isInstanceOf(GitCommandException.class)
                .hasMessage(commitId + " is not a commit of this book");
    }

    @Test
    void historyFollowsTheChapterThroughARename() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        move("chapters/01-departure.md", "chapters/02-departure.md");
        write("chapters/02-departure.md", DEPARTURE.replace("Nobody spoke.", "Nobody said a word."));
        commitAll("Make it chapter two");
        write("chapters/02-departure.md", DEPARTURE.replace("Nobody spoke.", "Nobody said a word.") + "The end.\n");
        commitAll("Add an ending");

        List<FileRevision> history = repository.history(bookRoot.resolve("chapters/02-departure.md"));

        assertThat(history)
                .extracting(FileRevision::message, FileRevision::path, FileRevision::renamedFrom,
                        FileRevision::linesAdded, FileRevision::linesRemoved)
                .containsExactly(
                        tuple("Add an ending", "chapters/02-departure.md", null, 1, 0),
                        tuple("Make it chapter two", "chapters/02-departure.md", "chapters/01-departure.md", 1, 1),
                        tuple("First draft", "chapters/01-departure.md", null, 6, 0));
    }

    @Test
    void aRenameWithoutEditsChangesNoLines() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        move("chapters/01-departure.md", "chapters/02-departure.md");
        commitAll("Make it chapter two");

        FileRevision rename = repository.history(bookRoot.resolve("chapters/02-departure.md")).getFirst();

        assertThat(rename.renamedFrom()).isEqualTo("chapters/01-departure.md");
        assertThat(rename.linesAdded()).isZero();
        assertThat(rename.linesRemoved()).isZero();
    }

    @Test
    void contentOfReadsAVersionFromBeforeARenameUnderItsOldName() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        move("chapters/01-departure.md", "chapters/02-departure.md");
        write("chapters/02-departure.md", "Rewritten from scratch.\n" + DEPARTURE);
        commitAll("Make it chapter two");

        FileRevision firstDraft = repository.history(bookRoot.resolve("chapters/02-departure.md")).getLast();

        assertThat(repository.contentOf(firstDraft)).contains(DEPARTURE);
    }

    @Test
    void aChapterAddedWhileAnUnrelatedOneIsDeletedIsNotARename() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-departure.md"));
        write("chapters/01-the-storm.md", "Thunder rolled over the salt flats all night.\n");
        commitAll("Replace the opening");

        List<FileRevision> history = repository.history(bookRoot.resolve("chapters/01-the-storm.md"));

        assertThat(history)
                .extracting(FileRevision::message, FileRevision::renamedFrom, FileRevision::linesAdded)
                .containsExactly(tuple("Replace the opening", null, 1));
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
        assertThat(repository.history(bookRoot.resolve("chapters/01-the-storm.md")))
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
        assertThat(repository.deletedChapters(bookRoot.resolve("chapters")))
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

        assertThat(repository.ignore("notes/", "Keep chapter notes out of Git")).isTrue();
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

    @Test
    void deletedChaptersListsAChapterADeletionCommitRemovedWithItsLastVersion() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        RevCommit lastVersion = commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        RevCommit deletion = commitAll("Delete chapter \"02-crossing\"");

        List<DeletedChapter> deleted = repository.deletedChapters(bookRoot.resolve("chapters"));

        assertThat(deleted)
                .extracting(DeletedChapter::path, DeletedChapter::lastCommitId, DeletedChapter::deletedInId)
                .containsExactly(tuple("chapters/02-crossing.md", lastVersion.getName(),
                        deletion.abbreviate(7).name()));
        assertThat(repository.contentOf(deleted.getFirst())).isEqualTo("The sea was calm.\n");
    }

    @Test
    void deletedChaptersAreNewestFirstAndLeaveOutRenamesAndOtherFiles() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        write("chapters/03-harbour.md", "Gulls everywhere.\n");
        write("chapters/notes.txt", "Not a chapter.\n");
        write("media/map.md", "Not a chapter either.\n");
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        commitAll("Delete chapter \"02-crossing\"");
        move("chapters/01-departure.md", "chapters/01-the-departure.md");
        commitAll("Rename chapter \"01-departure\" to \"01-the-departure\"");
        Files.delete(bookRoot.resolve("chapters/03-harbour.md"));
        Files.delete(bookRoot.resolve("chapters/notes.txt"));
        Files.delete(bookRoot.resolve("media/map.md"));
        commitAll("Delete the harbour and the notes");

        assertThat(repository.deletedChapters(bookRoot.resolve("chapters")))
                .extracting(DeletedChapter::path)
                .containsExactly("chapters/03-harbour.md", "chapters/02-crossing.md");
    }

    @Test
    void aRestoredChapterLeavesTheDeletedOnesAndAChapterDeletedTwiceAppearsOnce() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-departure.md"));
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        commitAll("Delete both");
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was rough.\n");
        commitAll("Restore both");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        commitAll("Delete the crossing again");

        List<DeletedChapter> deleted = repository.deletedChapters(bookRoot.resolve("chapters"));

        assertThat(deleted).extracting(DeletedChapter::path).containsExactly("chapters/02-crossing.md");
        assertThat(repository.contentOf(deleted.getFirst())).isEqualTo("The sea was rough.\n");
    }

    @Test
    void deletedChaptersOfARepositoryWithoutCommitsIsEmpty() {
        assertThat(repository.deletedChapters(bookRoot.resolve("chapters"))).isEmpty();
    }

    @Test
    void historyOfARestoredChapterGoesBackPastItsDeletion() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-departure.md"));
        commitAll("Delete chapter \"01-departure\"");
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("Restore chapter \"01-departure\"");

        assertThat(repository.history(bookRoot.resolve("chapters/01-departure.md")))
                .extracting(FileRevision::message)
                .containsExactly("Restore chapter \"01-departure\"", "Delete chapter \"01-departure\"", "First draft");
    }

    @Test
    void aNewChapterUnderADeletedChaptersNameInheritsItsHistory() throws Exception {
        write("chapters/01-lorem.md", DEPARTURE);
        commitAll("Old lorem");
        Files.delete(bookRoot.resolve("chapters/01-lorem.md"));
        commitAll("Delete chapter \"01-lorem\"");
        write("chapters/01-lorem.md", "");
        commitAll("New empty lorem");

        // Git keeps history by path, so the old chapter's commits are the new one's too.
        assertThat(repository.history(bookRoot.resolve("chapters/01-lorem.md")))
                .extracting(FileRevision::message)
                .containsExactly("New empty lorem", "Delete chapter \"01-lorem\"", "Old lorem");
    }

    @Test
    void renamingOntoANameUsedBeforeKeepsOnlyTheRenamedChaptersHistory() throws Exception {
        write("chapters/01-lorem.md", DEPARTURE);
        write("chapters/02-other.md", "Thunder rolled over the salt flats all night.\nThe end.\n");
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-lorem.md"));
        commitAll("Delete chapter \"01-lorem\"");
        move("chapters/02-other.md", "chapters/01-lorem.md");
        commitAll("Rename chapter \"02-other\" to \"01-lorem\"");

        assertThat(repository.history(bookRoot.resolve("chapters/01-lorem.md")))
                .extracting(FileRevision::message, FileRevision::path)
                .containsExactly(tuple("Rename chapter \"02-other\" to \"01-lorem\"", "chapters/01-lorem.md"),
                        tuple("First draft", "chapters/02-other.md"));
    }

    @Test
    void hasHistoryForANameAnyCommitUsedEvenIfDeletedOrRenamedAway() throws Exception {
        write("chapters/01-lorem.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-lorem.md"));
        move("chapters/02-crossing.md", "chapters/02-the-crossing.md");
        commitAll("Delete lorem, rename the crossing");
        write("chapters/03-saved-only.md", "Not committed yet.\n");

        assertThat(repository.hasHistory(bookRoot.resolve("chapters/01-lorem.md"))).isTrue();
        assertThat(repository.hasHistory(bookRoot.resolve("chapters/02-crossing.md"))).isTrue();
        assertThat(repository.hasHistory(bookRoot.resolve("chapters/02-the-crossing.md"))).isTrue();
        assertThat(repository.hasHistory(bookRoot.resolve("chapters/03-saved-only.md"))).isFalse();
        assertThat(repository.hasHistory(bookRoot.resolve("chapters/04-never.md"))).isFalse();
    }

    @Test
    void noNameHasHistoryInARepositoryWithoutCommits() {
        assertThat(repository.hasHistory(bookRoot.resolve("chapters/01-lorem.md"))).isFalse();
    }

    @Test
    void historyRejectsAFileOutsideTheBook() {
        Path outside = tempDir.resolve("notes.md");

        assertThatThrownBy(() -> repository.history(outside))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(outside + " is not a file in ");
    }

    private Git bareOrigin() throws Exception {
        Path originDir = tempDir.resolve("origin.git");
        Git origin = Git.init().setBare(true).setDirectory(originDir.toFile()).setInitialBranch(BRANCH).call();
        git.remoteAdd().setName(BookRepository.REMOTE).setUri(new URIish(originDir.toUri().toString())).call();
        return origin;
    }

    private void move(String from, String to) throws IOException {
        Files.move(bookRoot.resolve(from), bookRoot.resolve(to));
    }

    /** A revision of the file at {@code path} in {@code commitId}, as history() would list it. */
    private static FileRevision revision(String commitId, String path) {
        return new FileRevision(commitId, commitId.substring(0, 7), path, null, "A commit", Instant.EPOCH, 0, 0);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = bookRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private RevCommit commitAll(String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        return git.commit().setMessage(message).call();
    }
}
