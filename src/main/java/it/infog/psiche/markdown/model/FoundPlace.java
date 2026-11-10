package it.infog.psiche.markdown.model;

/**
 * Where a search goes next among the places it found.
 *
 * @param index   the place's index among them, in the order of the text
 * @param wrapped true if it went round the end of the text, going forward, or round its start, going back
 */
public record FoundPlace(int index,
                         boolean wrapped) {
}
