package it.infog.psiche.markdown.model;

/**
 * A footnote's label where it is written: a reference, {@code [^moon]}, or the opening of its note, {@code [^moon]:}.
 *
 * @param label the label, as it is written
 * @param range where the mark is in the text, its brackets, caret and colon included
 */
public record FootnoteMark(String label,
                           TextRange range) {
}
