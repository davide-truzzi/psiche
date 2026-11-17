package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.SceneKeys;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

/**
 * Alt held alone anywhere in a node's window, as the author asks to see the line numbers: held from the moment Alt
 * goes down with no other key, until it is let go, another key is pressed, or the window loses the focus. Alt in a
 * shortcut never counts, nor AltGr, which Windows sends as Ctrl+Alt. The keys go on as they would anyway.
 */
final class AltHold {

    private final ReadOnlyBooleanWrapper held = new ReadOnlyBooleanWrapper();
    // Held here, as a listener alone does not keep a mapped value alive.
    private final ObservableValue<Boolean> windowFocused;

    AltHold(Node node) {

        SceneKeys.filter(node, KeyEvent.KEY_PRESSED, event -> held.set(event.getCode() == KeyCode.ALT
                && !event.isControlDown() && !event.isShiftDown() && !event.isMetaDown()));
        SceneKeys.filter(node, KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.ALT)
                held.set(false);
        });
        windowFocused = node.sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(Window::focusedProperty)
                .orElse(false);
        windowFocused.addListener((obs, old, focused) -> {
            if (!focused)
                held.set(false);
        });
    }

    /** True while Alt is held alone. */
    ReadOnlyBooleanProperty heldProperty() { return held.getReadOnlyProperty(); }
    boolean isHeld() { return held.get(); }
}
