package it.infog.psiche.export.history.model;

/**
 * A changed passage of a chapter, with its context: a range of the paragraphs of a
 * {@link it.infog.psiche.git.model.Comparison}.
 *
 * @param from  the first paragraph shown, an unchanged one before the change if there is one
 * @param to    the paragraph after the last one shown
 * @param words the words written and removed in the passage, to find the largest ones
 */
public record Passage(int from,
                      int to,
                      int words) {
}
