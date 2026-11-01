package it.infog.psiche.git.model;

import java.util.List;

/**
 * A version of a chapter compared with the chapter now, as tracked changes.
 *
 * @param paragraphs the chapter now, with the text removed since the version kept in place and marked
 * @param changed    paragraphs that exist in both texts but were edited
 * @param added      paragraphs written since the version
 * @param removed    paragraphs removed since the version
 */
public record Comparison(List<Paragraph> paragraphs,
                         int changed,
                         int added,
                         int removed) {

    public Comparison {
        paragraphs = List.copyOf(paragraphs);
    }

    /** True if the two texts are the same. */
    public boolean identical() {
        return paragraphs.stream().noneMatch(Paragraph::changed);
    }
}
