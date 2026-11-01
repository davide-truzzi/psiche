package it.infog.psiche.ui.common;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public final class Animations {

    private static final Duration REVEAL_DURATION = Duration.millis(480);
    private static final Duration REVEAL_STAGGER = Duration.millis(110);
    private static final double REVEAL_RISE = 18;

    private Animations() {
    }

    /** Fades the nodes in while they rise into place, one after the other in the given order. */
    public static void reveal(Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];

            FadeTransition fade = new FadeTransition(REVEAL_DURATION, node);
            fade.setFromValue(0);
            fade.setToValue(1);

            TranslateTransition rise = new TranslateTransition(REVEAL_DURATION, node);
            rise.setFromY(REVEAL_RISE);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition transition = new ParallelTransition(fade, rise);
            transition.setDelay(REVEAL_STAGGER.multiply(i));
            node.setOpacity(0);
            transition.play();
        }
    }
}
