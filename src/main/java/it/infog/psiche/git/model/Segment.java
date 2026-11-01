package it.infog.psiche.git.model;

/**
 * A run of text within a paragraph, and how it changed between a version of a chapter and the chapter now.
 *
 * @param text   the words, spaces and punctuation of the run, without line breaks
 * @param change whether the run is in both texts, was written since the version, or was removed since then
 */
public record Segment(String text,
                      Change change) {

    public enum Change {
        UNCHANGED,
        ADDED,
        REMOVED
    }
}
