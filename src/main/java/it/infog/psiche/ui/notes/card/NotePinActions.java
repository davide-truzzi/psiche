package it.infog.psiche.ui.notes.card;

/**
 * What a card's note can do with its pin, on a chapter's board: pinned to the paragraph of the editor's caret, gone to,
 * or unpinned.
 */
public interface NotePinActions {

    /** True if the caret is on a paragraph that can hold a pin: one that is not blank. */
    boolean canPinHere();

    boolean isPinned(NoteCard card);

    /** Pins the note to the caret's paragraph, asking first if another note is pinned there. */
    void pinHere(NoteCard card);

    /** Shows the note's paragraph in the editor, with the caret on it. */
    void goTo(NoteCard card);

    void unpin(NoteCard card);
}
