package it.infog.psiche.notes.model;

/**
 * The part of a chapter's board the author last looked at, so the board opens there again.
 *
 * @param zoom how large the board is shown: 1 at its own size
 * @param x    the board point at the left edge of the view, in board pixels
 * @param y    the board point at the top edge of the view, in board pixels
 */
public record BoardView(double zoom,
                        double x,
                        double y) {

    /** How a board is first shown: at its own size, from its top-left corner. */
    public static BoardView start() {

        return new BoardView(1, 0, 0);
    }
}
