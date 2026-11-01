package it.infog.psiche.notes.model.enums;

/** Why notes are left without a chapter, which Deleted notes tells the author. */
public enum OrphanCause {

    /** Their chapter was deleted in Psiche, and they went with it. */
    CHAPTER_DELETED("deleted with the chapter"),
    /** The author deleted them from the board. */
    NOTE_DELETED("deleted on its own"),
    /** Their chapter was renamed or deleted outside Psiche, or they were imported under a name it no longer has. */
    CHAPTER_GONE("renamed or deleted outside Psiche");

    private final String label;

    OrphanCause(String label) {

        this.label = label;
    }

    /** How Deleted notes tells the cause, as "deleted with the chapter". */
    public String label() {

        return label;
    }
}
