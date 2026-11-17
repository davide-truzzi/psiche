package it.infog.psiche.export.model.enums;

/** What is wrong with an image or a link of a chapter, which the export would leave out. */
public enum ReferenceProblem {

    /** An image whose file is not there. */
    MISSING_IMAGE,
    /** An image whose file is there, but of a type the export cannot show. */
    UNUSABLE_IMAGE,
    /** A link to a chapter or a heading that is not in the book, or to a web address that is not well formed. */
    BROKEN_LINK
}
