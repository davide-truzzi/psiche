package it.infog.psiche.ui.notes.card;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * Moving and resizing a note with the pointer, whatever the board's zoom, the whole note always kept on the board.
 * Once a drag that changed something ends, the note's new place or size is handed on to be kept. While the note is
 * moved, its board hears where the pointer is, and a note dropped on the other board's half of the switch goes there.
 */
final class CardDrag {

    private final NoteCard card;
    private final NoteCard.Board board;
    // Where the pointer took hold of the card, from its top-left corner, or its size when the resize began.
    private Point2D grip = Point2D.ZERO;
    private boolean moved;

    /** @param board told when a drag that moved or resized the card ends, and where a moved card is dragged */
    CardDrag(NoteCard card, NoteCard.Board board) {

        this.card = card;
        this.board = board;
    }

    /** Lets the author move the card by dragging {@code handle}: its strip, or the band above it. */
    void moveBy(Region handle) {

        handle.setOnMousePressed(this::startMove);
        handle.setOnMouseDragged(this::move);
        handle.setOnMouseReleased(this::endMove);
    }

    /** Lets the author resize the card by dragging {@code corner}. */
    void resizeBy(Region corner) {

        corner.setOnMousePressed(this::startResize);
        corner.setOnMouseDragged(this::resize);
        corner.setOnMouseReleased(event -> end());
    }

    private void startMove(MouseEvent event) {

        if (event.getButton() != MouseButton.PRIMARY)
            return;

        grip = boardPoint(event).subtract(card.getLayoutX(), card.getLayoutY());
        moved = false;
        // The card takes the focus, so the board's keys, such as Delete, apply to it.
        card.requestFocus();
        event.consume();
    }

    private void move(MouseEvent event) {

        Point2D corner = boardPoint(event).subtract(grip);
        card.relocate(Math.clamp(corner.getX(), 0, boardPane().getWidth() - card.getWidth()),
                Math.clamp(corner.getY(), 0, boardPane().getHeight() - card.getHeight()));
        moved = true;
        board.moves().dragged(card, event.getSceneX(), event.getSceneY());
        event.consume();
    }

    /** Ends a move: a note dropped on the other board goes there, with the place it had before the drag. */
    private void endMove(MouseEvent event) {

        if (moved && board.moves().dropped(card, event.getSceneX(), event.getSceneY())) {
            moved = false;
            return;
        }

        end();
    }

    private void startResize(MouseEvent event) {

        grip = new Point2D(card.getPrefWidth(), card.getPrefHeight()).subtract(boardPoint(event));
        moved = false;
        event.consume();
    }

    /** Follows the pointer, between the note's smallest and largest size, and never past the board's edge. */
    private void resize(MouseEvent event) {

        Point2D size = boardPoint(event).add(grip);
        // A note kept too near the edge by an older version still gets its smallest size.
        double maxWidth = Math.max(NoteCard.MIN_WIDTH,
                Math.min(NoteCard.MAX_WIDTH, boardPane().getWidth() - card.getLayoutX()));
        double maxHeight = Math.max(NoteCard.MIN_HEIGHT,
                Math.min(NoteCard.MAX_HEIGHT, boardPane().getHeight() - card.getLayoutY()));
        card.setPrefSize(Math.clamp(size.getX(), NoteCard.MIN_WIDTH, maxWidth),
                Math.clamp(size.getY(), NoteCard.MIN_HEIGHT, maxHeight));
        moved = true;
        event.consume();
    }

    private void end() {

        if (moved)
            board.layoutChanged(card);
        moved = false;
    }

    /** Where the pointer is on the board, whatever the board's zoom. */
    private Point2D boardPoint(MouseEvent event) {

        return boardPane().sceneToLocal(event.getSceneX(), event.getSceneY());
    }

    private Region boardPane() {

        return (Region) card.getParent();
    }
}
