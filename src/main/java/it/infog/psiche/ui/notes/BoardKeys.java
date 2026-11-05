package it.infog.psiche.ui.notes;

import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The Notes tab's keys: Ctrl/⌘ with + − 0 zoom, with V pastes into the selected note or on the board; Delete moves the
 * selected note to Detached notes; Escape leaves it. In a note's text, the keys are the text's, but Escape leaves it.
 */
final class BoardKeys {

    private final NoteBoard board;
    private final BoardViewport viewport;

    BoardKeys(NoteBoard board, BoardViewport viewport) {

        this.board = board;
        this.viewport = viewport;
    }

    /** Acts on {@code event} if it is one of the board's keys, and consumes it then. */
    void pressed(KeyEvent event) {

        KeyCode code = event.getCode();

        if (event.getTarget() instanceof TextInputControl) {
            if (code == KeyCode.ESCAPE) {
                viewport.requestFocus();
                event.consume();
            }
            return;
        }

        if ((event.isShortcutDown() && shortcutKey(code)) || boardKey(code))
            event.consume();
    }

    /** Ctrl/⌘ with {@code code}: true if it is one of the board's. */
    private boolean shortcutKey(KeyCode code) {

        switch (code) {
            case V -> board.paste(viewport.centre());
            case PLUS, EQUALS, ADD -> viewport.zoomIn();
            case MINUS, SUBTRACT -> viewport.zoomOut();
            case DIGIT0, NUMPAD0 -> viewport.actualSize();
            default -> {
                return false;
            }
        }

        return true;
    }

    /** {@code code} alone, or with any modifier: true if it is one of the board's. */
    private boolean boardKey(KeyCode code) {

        switch (code) {
            case DELETE, BACK_SPACE -> {
                return board.deleteSelected();
            }
            case ESCAPE -> board.select(null);
            default -> {
                return false;
            }
        }

        return true;
    }
}
