package it.infog.psiche.ui.common;

import javafx.application.Platform;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.TextInputControlSkin;

/**
 * Keeps a text field's caret from blinking on unseen once the field has lost the focus. JavaFX restarts the blink
 * whenever the caret moves, with the focus or without, and moves it itself as the focus leaves, to clear the
 * selection: the blink then runs until the field next has the focus, and as any JavaFX animation, it draws the window
 * sixty times a second all the while.
 */
public final class FieldCaret {

    private FieldCaret() {
    }

    /** Stops {@code field}'s caret blinking each time the focus leaves it. */
    public static void stopsWithFocus(TextInputControl field) {

        field.focusedProperty().addListener((obs, old, focused) -> {
            // After JavaFX has cleared the selection, which it does as the focus leaves.
            if (!focused)
                Platform.runLater(() -> stop(field));
        });
    }

    private static void stop(TextInputControl field) {

        if (!field.isFocused() && field.getSkin() instanceof TextInputControlSkin<?> skin)
            skin.setCaretAnimating(false);
    }
}
