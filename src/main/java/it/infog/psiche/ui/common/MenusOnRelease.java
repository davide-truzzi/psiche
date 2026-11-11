package it.infog.psiche.ui.common;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Opens a right-click menu once the button is released, not as it is pressed, as Linux and macOS ask for it. JavaFX
 * starts a gesture on each press of the mouse and ends it on the release in the same window; a menu opened on the
 * press takes the release, and the gesture is left open. The next press then holds no gesture of its own: dragging a
 * scroll bar's thumb straight after choosing a menu item drags whatever lies under the pointer instead, selecting the
 * text or moving the split between the editor and the tabs. Waiting for the release lets the window end the gesture
 * first. A menu asked for from the keyboard, or on the release already, as on Windows, opens at once.
 */
public final class MenusOnRelease {

    private final Scene scene;
    // The menu asked for while the right button was down, which opens once it is released; null with none.
    private ContextMenuEvent waiting;
    private boolean rightButtonDown;
    // True while the waiting menu is asked for again, which then opens.
    private boolean replaying;

    private MenusOnRelease(Scene scene) {

        this.scene = scene;
    }

    /** Makes every right-click menu in {@code scene} open on the button's release. */
    public static void install(Scene scene) {

        MenusOnRelease menus = new MenusOnRelease(scene);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, menus::pressed);
        scene.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, menus::requested);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, menus::released);
    }

    private void pressed(MouseEvent event) {

        if (event.getButton() == MouseButton.SECONDARY)
            rightButtonDown = true;
    }

    private void requested(ContextMenuEvent event) {

        if (replaying || event.isKeyboardTrigger() || !rightButtonDown)
            return;

        waiting = event;
        event.consume();
    }

    private void released(MouseEvent event) {

        if (event.getButton() != MouseButton.SECONDARY)
            return;

        rightButtonDown = false;
        if (waiting == null)
            return;

        // The menu opens where it was asked for, once the window is done with the release and its gesture.
        ContextMenuEvent menu = waiting;
        waiting = null;
        Platform.runLater(() -> {
            replaying = true;
            try {
                Event.fireEvent(menu.getTarget(), menu.copyFor(scene, menu.getTarget()));
            } finally {
                replaying = false;
            }
        });
    }
}
