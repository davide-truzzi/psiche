package it.infog.psiche.ui.chapters;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Alt held alone in a node, as the author asks to see the line numbers: held from the moment Alt goes down with no
 * other key, until it is let go, another key is pressed, or the node loses the focus, as when the window does. Alt in
 * a shortcut never counts, nor AltGr, which Windows sends as Ctrl+Alt. The keys go on to the node as they would
 * anyway.
 */
final class AltHold {

    private final ReadOnlyBooleanWrapper held = new ReadOnlyBooleanWrapper();

    AltHold(Node node) {

        node.addEventFilter(KeyEvent.KEY_PRESSED, event -> held.set(event.getCode() == KeyCode.ALT
                && !event.isControlDown() && !event.isShiftDown() && !event.isMetaDown()));
        node.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.ALT)
                held.set(false);
        });
        node.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused)
                held.set(false);
        });
    }

    /** True while Alt is held alone. */
    ReadOnlyBooleanProperty heldProperty() { return held.getReadOnlyProperty(); }
    boolean isHeld() { return held.get(); }
}
