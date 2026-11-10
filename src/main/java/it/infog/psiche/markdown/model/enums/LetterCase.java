package it.infog.psiche.markdown.model.enums;

/** Whether a search tells capitals from small letters. */
public enum LetterCase {

    /** "rome" finds "Rome" and "ROME" too. */
    IGNORED,

    /** "Rome" finds "Rome" only. */
    MATCHED
}
