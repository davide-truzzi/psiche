package it.infog.psiche.export.model.enums;

/** What a book export does with chapters that have no text yet. */
public enum EmptyChapters {

    /** Leaves them out of the book. */
    SKIP,
    /** Gives each a page of its own with its file name as heading, to show where it goes in an unfinished book. */
    PLACEHOLDER
}
