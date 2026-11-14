package it.infog.psiche.git;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BookRevisionsTest extends GitBookTest {

    private static final String LAPTOP = "laptop";

    @Test
    void aRepositoryWithoutCommitsHasNoRevisions() {
        assertThat(revisions()).isEmpty();
    }

    @Test
    void revisionsAreOldestFirstWithEachChapterBeforeAndAfter() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("chapters/01-departure.md", "It began at dusk.\n");
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("Start chapter two");

        List<BookRevision> revisions = revisions();

        assertThat(revisions).extracting(BookRevision::message).containsExactly("First draft", "Start chapter two");
        assertThat(revisions.getFirst().changes())
                .containsExactly(new ChapterChange("chapters/01-departure.md", null, ChapterChangeKind.ADDED, "",
                        "It began at dawn.\n"));
        assertThat(revisions.getLast().changes()).containsExactly(
                new ChapterChange("chapters/01-departure.md", null, ChapterChangeKind.CHANGED, "It began at dawn.\n",
                        "It began at dusk.\n"),
                new ChapterChange("chapters/02-crossing.md", null, ChapterChangeKind.ADDED, "",
                        "The sea was calm.\n"));
    }

    @Test
    void aRevisionIsIdentifiedByItsCommitWithTheWholeMessage() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        RevCommit commit = commitAll("First draft\n\nThe opening, written on the train.\n");

        BookRevision revision = revisions().getFirst();

        assertThat(revision.commitId()).isEqualTo(commit.getName());
        assertThat(revision.shortId()).isEqualTo(commit.abbreviate(7).name());
        assertThat(revision.time()).isEqualTo(Instant.ofEpochSecond(commit.getCommitTime()));
        assertThat(revision.message()).isEqualTo("First draft\n\nThe opening, written on the train.");
        assertThat(revision.merge()).isFalse();
    }

    @Test
    void commitsThatChangedNoChapterAreLeftOut() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        write("media/book.properties", "title=Salt\n");
        write(".gitignore", "notes/\n");
        commitAll("Name the book");
        write("chapters/01-departure/map.png", "not a chapter");
        commitAll("Add a map");

        assertThat(revisions()).extracting(BookRevision::message).containsExactly("First draft");
    }

    @Test
    void otherFilesInTheSameCommitAreLeftOutOfItsChanges() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        write("chapters/01-departure/map.png", "not a chapter");
        write("media/book.properties", "title=Salt\n");
        commitAll("First draft");

        assertThat(revisions().getFirst().changes()).extracting(ChapterChange::path)
                .containsExactly("chapters/01-departure.md");
    }

    @Test
    void aRenamedChapterKeepsItsOldNameAndItsEdits() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        move("chapters/01-departure.md", "chapters/02-departure.md");
        write("chapters/02-departure.md", DEPARTURE.replace("Nobody spoke.", "Nobody said a word."));
        commitAll("Make it chapter two");

        assertThat(revisions().getLast().changes()).containsExactly(new ChapterChange("chapters/02-departure.md",
                "chapters/01-departure.md", ChapterChangeKind.RENAMED, DEPARTURE,
                DEPARTURE.replace("Nobody spoke.", "Nobody said a word.")));
    }

    @Test
    void aDeletedChapterKeepsItsLastText() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("chapters/02-crossing.md", "The sea was calm.\n");
        commitAll("First draft");
        Files.delete(bookRoot.resolve("chapters/02-crossing.md"));
        commitAll("Drop the crossing");

        assertThat(revisions().getLast().changes()).containsExactly(
                new ChapterChange("chapters/02-crossing.md", null, ChapterChangeKind.DELETED, "The sea was calm.\n",
                        ""));
    }

    @Test
    void aChapterSplitInTwoIsRenamedOnceAndCopiedOnce() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        move("chapters/01-departure.md", "chapters/01-dawn.md");
        write("chapters/02-noon.md", DEPARTURE);
        commitAll("Split the departure");

        assertThat(revisions().getLast().changes())
                .extracting(ChapterChange::fromPath, ChapterChange::kind, ChapterChange::before)
                .containsExactlyInAnyOrder(
                        tuple("chapters/01-departure.md", ChapterChangeKind.RENAMED, DEPARTURE),
                        tuple("chapters/01-departure.md", ChapterChangeKind.COPIED, DEPARTURE));
    }

    @Test
    void aCleanMergeKeepsBothComputersCommitsOnceAndChangesNothingItself() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        onLaptop("chapters/02-crossing.md", "The sea was calm.\n", "Written on the laptop");
        write("chapters/03-arrival.md", "The port was empty.\n");
        commitAll("Written on the desktop");
        RevCommit merge = mergeLaptop();

        List<BookRevision> revisions = revisions();

        assertThat(revisions).extracting(BookRevision::message).containsExactlyInAnyOrder("First draft",
                "Written on the laptop", "Written on the desktop", "Merge the laptop");
        assertThat(revisions.getFirst().message()).isEqualTo("First draft");
        assertThat(revisions.getLast().commitId()).isEqualTo(merge.getName());
        assertThat(revisions.getLast().merge()).isTrue();
        assertThat(revisions.getLast().changes()).isEmpty();
    }

    @Test
    void aMergeKeepsTheChaptersTheAuthorResolvedByHand() throws Exception {
        write("chapters/01-departure.md", "It began at dawn.\n");
        commitAll("First draft");
        onLaptop("chapters/01-departure.md", "It began at noon.\n", "Noon, on the laptop");
        write("chapters/01-departure.md", "It began at dusk.\n");
        commitAll("Dusk, on the desktop");
        MergeResult result = git.merge().include(git.getRepository().resolve(LAPTOP)).call();
        assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);
        write("chapters/01-departure.md", "It began at sunset.\n");
        commitAll("Merge the laptop");

        BookRevision merge = revisions().getLast();

        assertThat(merge.merge()).isTrue();
        assertThat(merge.changes()).containsExactly(new ChapterChange("chapters/01-departure.md", null,
                ChapterChangeKind.CHANGED, "It began at dusk.\n", "It began at sunset.\n"));
    }

    @Test
    void revisionsRejectAFolderOutsideTheBook() {
        Path outside = tempDir.resolve("chapters");

        assertThatThrownBy(() -> repository.history().bookRevisions(outside))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<BookRevision> revisions() {
        return repository.history().bookRevisions(bookRoot.resolve("chapters"));
    }

    /** Commits {@code text} to {@code file} on the laptop's branch, as another computer would, and comes back. */
    private void onLaptop(String file, String text, String message) throws Exception {
        git.branchCreate().setName(LAPTOP).call();
        git.checkout().setName(LAPTOP).call();
        write(file, text);
        commitAll(message);
        git.checkout().setName(BRANCH).call();
    }

    private RevCommit mergeLaptop() throws Exception {
        MergeResult result = git.merge().include(git.getRepository().resolve(LAPTOP))
                .setMessage("Merge the laptop").call();
        assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.MERGED);
        return git.log().setMaxCount(1).call().iterator().next();
    }
}
