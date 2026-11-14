package it.infog.psiche.export.history;

import it.infog.psiche.export.history.model.CountedRevision;
import it.infog.psiche.export.history.model.HistorySection;
import it.infog.psiche.git.TrackedChanges;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits the history into sections, so it reads shorter: a run of small revisions made one after another on the same
 * day becomes one section, and every other revision has a section of its own. A large revision, a merge or a new day
 * ends a run; a run of one is shown as a revision of its own.
 */
final class RevisionGroups {

    // A revision that wrote and removed at most this many words, in all its chapters, is small.
    static final int SMALL = 100;

    private RevisionGroups() {
    }

    /**
     * @param revisions the book's revisions, oldest first
     * @param zone      the time zone the days are told apart in
     * @return the sections, oldest first, the revisions numbered from 1
     */
    static List<HistorySection> of(List<BookRevision> revisions, ZoneId zone) {

        List<HistorySection> sections = new ArrayList<>();
        List<CountedRevision> run = new ArrayList<>();
        int number = 0;
        for (BookRevision revision : revisions) {
            CountedRevision counted = count(++number, revision);
            boolean small = !revision.merge() && counted.wordsChanged() <= SMALL;
            if (!run.isEmpty() && !(small && day(run.getLast(), zone).equals(day(counted, zone))))
                end(run, sections);

            if (small)
                run.add(counted);
            else
                sections.add(new HistorySection(List.of(counted), revision.changes()));
        }

        end(run, sections);
        return sections;
    }

    /** {@code revision} with the words it wrote and removed. */
    static CountedRevision count(int number, BookRevision revision) {

        int added = 0;
        int removed = 0;
        for (ChapterChange change : revision.changes()) {
            Comparison comparison = TrackedChanges.compare(change.before(), change.after());
            added += comparison.wordsAdded();
            removed += comparison.wordsRemoved();
        }

        return new CountedRevision(number, revision, added, removed);
    }

    /**
     * Each chapter as the run of revisions changed it: its text before the first and after the last, under the name
     * it had before the run, if renamed. A chapter written and deleted within the run, or changed and changed back, is
     * left out.
     */
    static List<ChapterChange> combine(List<CountedRevision> run) {

        // By the chapter's name after the latest revision read so far, so the next revision's change finds it.
        Map<String, ChapterChange> chapters = new LinkedHashMap<>();
        for (CountedRevision counted : run) {
            for (ChapterChange change : counted.revision().changes()) {
                String earlierName = change.kind() == ChapterChangeKind.RENAMED ? change.fromPath() : change.path();
                ChapterChange earlier = chapters.remove(earlierName);
                ChapterChange combined = earlier == null ? change : then(earlier, change);
                if (combined != null)
                    chapters.put(combined.path(), combined);
            }
        }

        return chapters.values().stream()
                .filter(change -> change.kind() != ChapterChangeKind.CHANGED || !change.before().equals(change.after()))
                .sorted(Comparator.comparing(ChapterChange::path))
                .toList();
    }

    /** Ends the run: a section of its own for a single revision, one for all of them otherwise. */
    private static void end(List<CountedRevision> run, List<HistorySection> sections) {

        if (run.isEmpty())
            return;

        List<ChapterChange> changes = run.size() == 1 ? run.getFirst().revision().changes() : combine(run);
        sections.add(new HistorySection(run, changes));
        run.clear();
    }

    private static LocalDate day(CountedRevision revision, ZoneId zone) {

        return LocalDate.ofInstant(revision.revision().time(), zone);
    }

    /**
     * A chapter changed earlier in the run, then by {@code later}, as one change.
     *
     * @return {@code null} if the chapter was new in the run and is deleted again
     */
    private static ChapterChange then(ChapterChange earlier, ChapterChange later) {

        ChapterChangeKind kind = earlier.kind();
        boolean newInRun = kind == ChapterChangeKind.ADDED || kind == ChapterChangeKind.COPIED;

        return switch (later.kind()) {
            case DELETED -> newInRun ? null : new ChapterChange(namedBefore(earlier), null, ChapterChangeKind.DELETED,
                    earlier.before(), "");
            // Deleted earlier in the run and written again under the same name.
            case ADDED, COPIED -> new ChapterChange(later.path(), null, ChapterChangeKind.CHANGED, earlier.before(),
                    later.after());
            case CHANGED, RENAMED -> {
                String from = newInRun ? earlier.fromPath() : namedBefore(earlier);
                boolean renamed = !newInRun && !from.equals(later.path());
                ChapterChangeKind combined = newInRun ? kind
                        : renamed ? ChapterChangeKind.RENAMED : ChapterChangeKind.CHANGED;
                yield new ChapterChange(later.path(), renamed || newInRun ? from : null, combined, earlier.before(),
                        later.after());
            }
        };
    }

    /** The chapter's name before the run: the one it was renamed from, if it was. */
    private static String namedBefore(ChapterChange change) {

        return change.fromPath() != null ? change.fromPath() : change.path();
    }
}
