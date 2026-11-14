package it.infog.psiche.export.history;

import it.infog.psiche.export.history.model.CountedRevision;
import it.infog.psiche.export.history.model.HistorySection;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RevisionGroupsTest {

    private static final String DAWN = "chapters/01-dawn.md";
    private static final String NOON = "chapters/02-noon.md";
    // More words than a small revision writes.
    private static final String LONG = "word ".repeat(RevisionGroups.SMALL + 1);
    private static final Instant MORNING = Instant.parse("2026-03-03T09:00:00Z");

    @Test
    void smallRevisionsOnTheSameDayShareASection() {
        List<HistorySection> sections = sections(
                revision(0, changed(DAWN, "It began.", "It began at dawn.")),
                revision(1, changed(DAWN, "It began at dawn.", "It began at dusk.")),
                revision(2, changed(NOON, "Noon.", "Noon came.")));

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().grouped()).isTrue();
        assertThat(sections.getFirst().revisions()).extracting(CountedRevision::number).containsExactly(1, 2, 3);
    }

    @Test
    void aLargeRevisionHasASectionOfItsOwnAndEndsTheRun() {
        List<HistorySection> sections = sections(
                revision(0, changed(DAWN, "One.", "One two.")),
                revision(1, changed(DAWN, "One two.", "One two three.")),
                revision(2, added(NOON, LONG)),
                revision(3, changed(NOON, LONG, LONG + "end.")));

        assertThat(numbers(sections)).containsExactly(List.of(1, 2), List.of(3), List.of(4));
        assertThat(sections).extracting(HistorySection::grouped).containsExactly(true, false, false);
    }

    @Test
    void aNewDayEndsTheRun() {
        List<HistorySection> sections = sections(
                revision(0, changed(DAWN, "One.", "One two.")),
                revision(1, changed(DAWN, "One two.", "One two three.")),
                revision(24, changed(DAWN, "One two three.", "One two three four.")),
                revision(25, changed(DAWN, "One two three four.", "One two three four five.")));

        assertThat(numbers(sections)).containsExactly(List.of(1, 2), List.of(3, 4));
    }

    @Test
    void aMergeHasASectionOfItsOwn() {
        BookRevision merge = new BookRevision("m".repeat(40), "mmmmmmm", "Merge", MORNING.plusSeconds(3600), true,
                List.of());

        List<HistorySection> sections = sections(
                revision(0, changed(DAWN, "One.", "One two.")),
                merge,
                revision(2, changed(DAWN, "One two.", "One two three.")));

        assertThat(numbers(sections)).containsExactly(List.of(1), List.of(2), List.of(3));
    }

    @Test
    void aRevisionCountsTheWordsItWroteAndRemovedInEveryChapter() {
        CountedRevision counted = RevisionGroups.count(7, revision(0,
                changed(DAWN, "It began at dawn.", "It began, slowly, at dusk."),
                added(NOON, "Noon came.")));

        assertThat(counted.number()).isEqualTo(7);
        assertThat(counted.wordsAdded()).isEqualTo(4);
        assertThat(counted.wordsRemoved()).isEqualTo(1);
    }

    @Test
    void aRunShowsEachChapterFromItsTextBeforeTheFirstRevisionToItsTextAfterTheLast() {
        List<ChapterChange> combined = combine(
                revision(0, changed(DAWN, "One.", "One two.")),
                revision(1, changed(DAWN, "One two.", "One two three.")));

        assertThat(combined).containsExactly(changed(DAWN, "One.", "One two three."));
    }

    @Test
    void aChapterRenamedInTheRunKeepsItsNameFromBeforeIt() {
        List<ChapterChange> combined = combine(
                revision(0, changed(DAWN, "One.", "One two.")),
                revision(1, new ChapterChange(NOON, DAWN, ChapterChangeKind.RENAMED, "One two.", "One two.")),
                revision(2, changed(NOON, "One two.", "One two three.")));

        assertThat(combined).containsExactly(
                new ChapterChange(NOON, DAWN, ChapterChangeKind.RENAMED, "One.", "One two three."));
    }

    @Test
    void aChapterRenamedBackIsOnlyChanged() {
        List<ChapterChange> combined = combine(
                revision(0, new ChapterChange(NOON, DAWN, ChapterChangeKind.RENAMED, "One.", "One.")),
                revision(1, new ChapterChange(DAWN, NOON, ChapterChangeKind.RENAMED, "One.", "One two.")));

        assertThat(combined).containsExactly(changed(DAWN, "One.", "One two."));
    }

    @Test
    void aChapterWrittenInTheRunIsNew() {
        List<ChapterChange> combined = combine(
                revision(0, added(NOON, "Noon.")),
                revision(1, changed(NOON, "Noon.", "Noon came.")));

        assertThat(combined).containsExactly(added(NOON, "Noon came."));
    }

    @Test
    void aChapterWrittenAndDeletedInTheRunIsLeftOut() {
        List<ChapterChange> combined = combine(
                revision(0, added(NOON, "Noon.")),
                revision(1, new ChapterChange(NOON, null, ChapterChangeKind.DELETED, "Noon.", "")));

        assertThat(combined).isEmpty();
    }

    @Test
    void aChapterChangedAndChangedBackIsLeftOut() {
        List<ChapterChange> combined = combine(
                revision(0, changed(DAWN, "One.", "One two.")),
                revision(1, changed(DAWN, "One two.", "One.")));

        assertThat(combined).isEmpty();
    }

    @Test
    void aChapterRenamedThenDeletedIsDeletedUnderItsNameFromBeforeTheRun() {
        List<ChapterChange> combined = combine(
                revision(0, new ChapterChange(NOON, DAWN, ChapterChangeKind.RENAMED, "One.", "One two.")),
                revision(1, new ChapterChange(NOON, null, ChapterChangeKind.DELETED, "One two.", "")));

        assertThat(combined).containsExactly(new ChapterChange(DAWN, null, ChapterChangeKind.DELETED, "One.", ""));
    }

    private static List<HistorySection> sections(BookRevision... revisions) {
        return RevisionGroups.of(List.of(revisions), ZoneOffset.UTC);
    }

    private static List<ChapterChange> combine(BookRevision... revisions) {
        List<CountedRevision> run = List.of(revisions).stream().map(revision -> RevisionGroups.count(1, revision))
                .toList();
        return RevisionGroups.combine(run);
    }

    private static List<List<Integer>> numbers(List<HistorySection> sections) {
        return sections.stream()
                .map(section -> section.revisions().stream().map(CountedRevision::number).toList())
                .toList();
    }

    /** A revision made {@code hours} after 09:00 on 3 March 2026. */
    private static BookRevision revision(int hours, ChapterChange... changes) {
        String id = "%040d".formatted(hours);
        return new BookRevision(id, id.substring(0, 7), "Revision at " + hours, MORNING.plusSeconds(hours * 3600L),
                false, List.of(changes));
    }

    private static ChapterChange changed(String path, String before, String after) {
        return new ChapterChange(path, null, ChapterChangeKind.CHANGED, before, after);
    }

    private static ChapterChange added(String path, String text) {
        return new ChapterChange(path, null, ChapterChangeKind.ADDED, "", text);
    }
}
