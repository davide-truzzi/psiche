package it.infog.psiche.ui.common;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

/**
 * Keys the whole window hears, wherever the focus is in it: a filter on the scene a node is shown in, which follows
 * the node into another scene, and leaves the one it was in.
 */
public final class SceneKeys {

    private SceneKeys() {
    }

    /** Sends {@code type} key events anywhere in {@code node}'s window to {@code filter}, before the focus does. */
    public static void filter(Node node, EventType<KeyEvent> type, EventHandler<KeyEvent> filter) {

        node.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null)
                old.removeEventFilter(type, filter);
            if (scene != null)
                scene.addEventFilter(type, filter);
        });
        Scene scene = node.getScene();
        if (scene != null)
            scene.addEventFilter(type, filter);
    }
}
