package it.infog.psiche.ui.notes.card;

import java.util.Optional;

/**
 * Where a card's note can go, as its board moves it: to another chapter's board, picked from a list, or straight to the
 * other board the Notes tab shows, the book's from a chapter's, the open chapter's from the book's; that one also by
 * dragging the note onto its half of the switch above the board.
 */
public interface NoteMoveActions {

    /** True if the book has a chapter besides the board's, which a note can move to. */
    boolean hasOtherChapters();

    /** Asks the author for another chapter, and moves the note to its board. */
    void moveToChapter(NoteCard card);

    /** The name of the other board, the book's title or the open chapter's; empty while there is none. */
    Optional<String> otherBoard();

    /** Moves the note to the other board. */
    void moveToOtherBoard(NoteCard card);

    /** Tells that the note is dragged, the pointer at a point of the scene, so the other board can offer itself. */
    void dragged(NoteCard card, double sceneX, double sceneY);

    /**
     * Tells that a drag of the note ended with the pointer at a point of the scene: if over the other board's half of
     * the switch, the note moves there.
     *
     * @return true if the note left the board
     */
    boolean dropped(NoteCard card, double sceneX, double sceneY);
}
