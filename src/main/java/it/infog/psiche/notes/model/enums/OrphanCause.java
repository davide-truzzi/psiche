package it.infog.psiche.notes.model.enums;

/** Why notes are left without a chapter, which Detached notes tells the author. */
public enum OrphanCause {

    /** Their chapter was deleted in Psiche, and they went with it. */
    CHAPTER_DELETED("deleted with the chapter"),
    /** The author deleted them from the board. */
    NOTE_DELETED("deleted on its own"),
    /**
     * The book has no chapter by their chapter's name: it was renamed or deleted outside Psiche, or they were imported
     * from another book.
     */
    CHAPTER_GONE("no such chapter in this book");

    private final String label;

    OrphanCause(String label) {

        this.label = label;
    }

    /** How Detached notes tells the cause, as "deleted with the chapter". */
    public String label() {

        return label;
    }
}
