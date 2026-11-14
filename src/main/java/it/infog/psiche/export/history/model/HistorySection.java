package it.infog.psiche.export.history.model;

import it.infog.psiche.git.model.ChapterChange;

import java.util.List;

/**
 * One section of the writing history: a revision of its own, or a run of small ones shown together.
 *
 * @param revisions the revisions, oldest first; one for a revision of its own
 * @param changes   the chapters the section changed, in the order of their paths; for a run, each chapter's text
 *                  before the first revision and after the last
 */
public record HistorySection(List<CountedRevision> revisions,
                            List<ChapterChange> changes) {

    public HistorySection {
        if (revisions.isEmpty())
            throw new IllegalArgumentException("A section needs at least one revision");

        revisions = List.copyOf(revisions);
        changes = List.copyOf(changes);
    }

    /** True for a run of small revisions shown together. */
    public boolean grouped() {

        return revisions.size() > 1;
    }

    public CountedRevision first() {

        return revisions.getFirst();
    }

    public CountedRevision last() {

        return revisions.getLast();
    }

    /** The words the section's revisions wrote and removed, all together. */
    public int wordsChanged() {

        return revisions.stream().mapToInt(CountedRevision::wordsChanged).sum();
    }
}
