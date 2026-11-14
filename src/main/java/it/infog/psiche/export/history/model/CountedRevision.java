package it.infog.psiche.export.history.model;

import it.infog.psiche.git.model.BookRevision;

/**
 * A revision with its place in the history and the words it changed.
 *
 * @param number       its place in the history, from 1 for the first
 * @param wordsAdded   the words it wrote, in all the chapters it changed
 * @param wordsRemoved the words it removed, in all the chapters it changed
 */
public record CountedRevision(int number,
                              BookRevision revision,
                              int wordsAdded,
                              int wordsRemoved) {

    /** The words written and removed together, how much the revision changed. */
    public int wordsChanged() {

        return wordsAdded + wordsRemoved;
    }
}
