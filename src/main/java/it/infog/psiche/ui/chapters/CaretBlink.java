package it.infog.psiche.ui.chapters;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Blinks the manuscript's caret, shown half a second and hidden half a second, as RichTextFX would, but without its
 * animation: a running JavaFX animation draws the window sixty times a second however seldom it changes, so a caret
 * blinking that way keeps a laptop's processor busy for as long as the editor has the focus. A timer of its own shows
 * and hides the caret instead, and the window is drawn only then. It blinks only while the editor has the focus in the
 * window the author is using, and shows at once when the caret moves or the text changes, as in any editor. Like
 * GTK and GNOME, it stops blinking ten seconds after the author last typed or moved the caret, and stays shown: each
 * blink has the window drawn anew, which costs a battery more than it seems.
 * <p>
 * writer.css turns RichTextFX's own blinking off, with a blink rate of zero over the one its stylesheet sets.
 */
final class CaretBlink {

    private static final long HALF_PERIOD_MILLIS = 500;
    // Ten seconds of blinking, shown and hidden, after the author last typed or moved the caret.
    private static final int BLINKS = 20;

    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("caret").daemon().factory());
    private final CaretNode caret;
    private final ObservableValue<Boolean> active;
    private ScheduledFuture<?> blinking;
    private boolean shown = true;
    private int blinksLeft;
    // Counts the restarts, so a blink the timer had already handed over before one is not run after it.
    private long round;

    CaretBlink(StyleClassedTextArea area) {

        caret = area.getCaretSelectionBind().getUnderlyingCaret();

        // The editor has the focus, in the window that has it.
        ObservableValue<Boolean> windowFocused = area.sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(Window::focusedProperty)
                .orElse(false);
        active = area.focusedProperty().flatMap(focused -> focused ? windowFocused : area.focusedProperty());

        active.addListener((obs, was, now) -> restart());
        area.caretPositionProperty().addListener((obs, old, position) -> restart());
        area.textProperty().addListener((obs, old, text) -> restart());
        restart();
    }

    /** Shows the caret at once, and blinks it from there while the editor is in use. */
    private void restart() {

        if (blinking != null)
            blinking.cancel(false);
        show(true);
        blinksLeft = BLINKS;
        long thisRound = ++round;
        blinking = Boolean.TRUE.equals(active.getValue())
                ? timer.scheduleAtFixedRate(() -> Platform.runLater(() -> blink(thisRound)), HALF_PERIOD_MILLIS,
                        HALF_PERIOD_MILLIS, TimeUnit.MILLISECONDS)
                : null;
    }

    /** Shows the hidden caret, or hides the shown one; ten seconds on, it stays shown, and the timer stops. */
    private void blink(long ofRound) {

        if (ofRound != round)
            return;

        if (--blinksLeft > 0 || !shown) {
            show(!shown);
            return;
        }
        blinking.cancel(false);
        blinking = null;
    }

    /** Shows the caret where RichTextFX would, with the focus, or hides it. */
    private void show(boolean visible) {

        shown = visible;
        caret.setShowCaret(visible ? Caret.CaretVisibility.AUTO : Caret.CaretVisibility.OFF);
    }
}
