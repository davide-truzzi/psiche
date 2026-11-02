package it.infog.psiche.markdown.model;

/** The text from character {@code start} up to, but not including, character {@code end}. */
public record TextRange(int start,
                        int end) {
}
