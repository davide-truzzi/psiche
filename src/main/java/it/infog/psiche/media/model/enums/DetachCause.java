package it.infog.psiche.media.model.enums;

/** Why images are left without a chapter, which Detached images tells the author. */
public enum DetachCause {

    /** Their chapter was deleted in Psiche, and they went with it. */
    CHAPTER_DELETED("deleted with the chapter"),
    /** The author detached them from their chapter, as the text no longer showed them. */
    DETACHED("detached on its own"),
    /** The book has no chapter by their chapter's name: it was renamed or deleted outside Psiche. */
    CHAPTER_GONE("no such chapter in this book");

    private final String label;

    DetachCause(String label) {

        this.label = label;
    }

    /** How Detached images tells the cause, as "deleted with the chapter". */
    public String label() {

        return label;
    }
}
