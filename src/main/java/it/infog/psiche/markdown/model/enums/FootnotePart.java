package it.infog.psiche.markdown.model.enums;

/** The parts of a footnote written in a chapter. */
public enum FootnotePart {

    /** A reference to a note, {@code [^moon]}. */
    REFERENCE,

    /** A note, from its opening {@code [^moon]:} to its last paragraph's end. */
    NOTE,

    /** A reference to no note, which stays as it is written. */
    MISSING_REFERENCE
}
