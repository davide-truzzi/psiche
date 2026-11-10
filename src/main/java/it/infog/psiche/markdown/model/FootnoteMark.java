package it.infog.psiche.markdown.model;

/**
 * A footnote's label where it is written: a reference, {@code [^moon]}, or the opening of its note, {@code [^moon]:}.
 *
 * @param label the label, as it is written
 * @param range where the mark is in the text, its brackets, caret and colon included
 * @param block what Default mode keeps whole with it: the reference itself, or the whole note, from its opening to
 *              the end of its last paragraph
 */
public record FootnoteMark(String label,
                           TextRange range,
                           TextRange block) {

    /** A mark that is a block of its own, as a reference. */
    public FootnoteMark(String label, TextRange range) {

        this(label, range, range);
    }
}
