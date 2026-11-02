package it.infog.psiche.git;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.git.model.FileRevision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BookHistoryTest extends GitBookTest {

    @Test
    void historyListsCommitsThatChangedTheFileNewestFirstWithLinesAddedAndRemoved() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\nNobody spoke.\nThe carts were loaded.\n");
        commitAll("First draft");
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("Start chapter two");
        write("chapters/01-departure.md",
                "It began at dawn.\nEveryone spoke at once.\nThe carts were loaded.\nThe road was white.\n");
        commitAll("Rework the opening");

        List<FileRevision> history = repository.history().revisions(bookRoot.resolve("chapters/01-departure.md"));

        assertThat(history)
                .extracting(FileRevision::message, FileRevision::linesAdded, FileRevision::linesRemoved)
                .containsExactly(tuple("Rework the opening", 2, 1), tuple("First draft", 3, 0));
    }

    @Test
    void historyIdentifiesEachCommitByShortIdAndTime() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        FileRevision revision = repository.history().revisions(bookRoot.resolve("chapters/01-departure.md")).getFirst();

        assertThat(revision.shortId()).isEqualTo(commit.abbreviate(7).name());
        assertThat(revision.time()).isEqualTo(Instant.ofEpochSecond(commit.getCommitTime()));
    }

    @Test
    void historyIsEmptyBeforeTheFirstCommit() throws IOException {
        write("chapters/01-departure.md", "It began at dawn.\n");

        assertThat(repository.history().revisions(bookRoot.resolve("chapters/01-departure.md"))).isEmpty();
    }

    @Test
    void historyIsEmptyForAFileNeverCommitted() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("chapters/02-crossing.md", "The sea was calm.\n");

        assertThat(repository.history().revisions(bookRoot.resolve("chapters/02-crossing.md"))).isEmpty();
    }

    @Test
    void historyIdentifiesTheFullCommitToReadTheFileFrom() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        FileRevision revision = repository.history().revisions(bookRoot.resolve("chapters/01-departure.md")).getFirst();

        assertThat(revision.commitId()).isEqualTo(commit.getName());
    }

    @Test
    void contentOfReadsTheFileAsEachCommitRecordedIt() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("chapters/01-departure.md", "It began at dusk. Perché?\n");
        commitAll("Move it to the evening");
        write("chapters/01-departure.md", "Not committed yet.\n");

        List<FileRevision> history = repository.history().revisions(bookRoot.resolve("chapters/01-departure.md"));

        assertThat(history).extracting(repository.history()::contentOf).containsExactly(
                Optional.of("It began at dusk. Perché?\n"), Optional.of("It began at dawn.\n"));
    }

    @Test
    void contentOfIsEmptyForACommitWithoutTheFile() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft");

        assertThat(repository.history().contentOf(revision(commit.getName(), "chapters/02-crossing.md"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-commit", "0123456789012345678901234567890123456789"})
    void contentOfRejectsAnIdThatIsNotACommit(String commitId) throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");

        assertThatThrownBy(() -> repository.history().contentOf(revision(commitId, "chapters/01-departure.md")))
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

        List<FileRevision> history = repository.history().revisions(bookRoot.resolve("chapters/02-departure.md"));

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

        FileRevision rename = repository.history().revisions(bookRoot.resolve("chapters/02-departure.md")).getFirst();

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

        FileRevision firstDraft =
                repository.history().revisions(bookRoot.resolve("chapters/02-departure.md")).getLast();

        assertThat(repository.history().contentOf(firstDraft)).contains(DEPARTURE);
    }

    @Test
    void aChapterAddedWhileAnUnrelatedOneIsDeletedIsNotARename() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-departure.md"));
        write("chapters/01-the-storm.md", "Thunder rolled over the salt flats all night.\n");
        commitAll("Replace the opening");

        List<FileRevision> history = repository.history().revisions(bookRoot.resolve("chapters/01-the-storm.md"));

        assertThat(history)
                .extracting(FileRevision::message, FileRevision::renamedFrom, FileRevision::linesAdded)
                .containsExactly(tuple("Replace the opening", null, 1));
    }

    @Test
    void deletedChaptersListsAChapterADeletionCommitRemovedWithItsLastVersion() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        RevCommit lastVersion = commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        RevCommit deletion = commitAll("Delete chapter \"02-crossing\"");

        List<DeletedChapter> deleted = repository.history().deletedChapters(bookRoot.resolve("chapters"));

        assertThat(deleted)
                .extracting(DeletedChapter::path, DeletedChapter::lastCommitId, DeletedChapter::deletedInId)
                .containsExactly(tuple("chapters/02-crossing.md", lastVersion.getName(),
                        deletion.abbreviate(7).name()));
        assertThat(repository.history().contentOf(deleted.getFirst())).isEqualTo("The sea was calm.\n");
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

        assertThat(repository.history().deletedChapters(bookRoot.resolve("chapters")))
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

        List<DeletedChapter> deleted = repository.history().deletedChapters(bookRoot.resolve("chapters"));

        assertThat(deleted).extracting(DeletedChapter::path).containsExactly("chapters/02-crossing.md");
        assertThat(repository.history().contentOf(deleted.getFirst())).isEqualTo("The sea was rough.\n");
    }

    @Test
    void deletedChaptersOfARepositoryWithoutCommitsIsEmpty() {
        assertThat(repository.history().deletedChapters(bookRoot.resolve("chapters"))).isEmpty();
    }

    @Test
    void historyOfARestoredChapterGoesBackPastItsDeletion() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/01-departure.md"));
        commitAll("Delete chapter \"01-departure\"");
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("Restore chapter \"01-departure\"");

        assertThat(repository.history().revisions(bookRoot.resolve("chapters/01-departure.md")))
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
        assertThat(repository.history().revisions(bookRoot.resolve("chapters/01-lorem.md")))
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

        assertThat(repository.history().revisions(bookRoot.resolve("chapters/01-lorem.md")))
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

        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/01-lorem.md"))).isTrue();
        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/02-crossing.md"))).isTrue();
        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/02-the-crossing.md"))).isTrue();
        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/03-saved-only.md"))).isFalse();
        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/04-never.md"))).isFalse();
    }

    @Test
    void noNameHasHistoryInARepositoryWithoutCommits() {
        assertThat(repository.history().hasHistory(bookRoot.resolve("chapters/01-lorem.md"))).isFalse();
    }

    @Test
    void historyRejectsAFileOutsideTheBook() {
        Path outside = tempDir.resolve("notes.md");

        assertThatThrownBy(() -> repository.history().revisions(outside))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(outside + " is not a file in ");
    }

    /** A revision of the file at {@code path} in {@code commitId}, as history() would list it. */
    private static FileRevision revision(String commitId, String path) {
        return new FileRevision(commitId, commitId.substring(0, 7), path, null, "A commit", Instant.EPOCH, 0, 0);
    }
}
