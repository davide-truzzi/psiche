package it.infog.psiche.ui.common;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseEvent;

import java.util.function.DoubleConsumer;

/**
 * Two parts split by a divider the author drags, the first keeping its size in pixels, as the author last dragged
 * it, while the window or the pane around them changes size; the second takes the rest.
 */
public final class KeptSizeSplit {

    private final SplitPane split;
    private final Node first;
    private final boolean horizontal;
    private final DoubleConsumer saveSize;
    // The first part's width, side by side, or height, one above the other, in pixels, as last placed or dragged.
    private double size;

    private KeptSizeSplit(Orientation orientation, Node first, Node second, double size, DoubleConsumer saveSize) {

        this.split = new SplitPane(first, second);
        this.first = first;
        this.horizontal = orientation == Orientation.HORIZONTAL;
        this.size = size;
        this.saveSize = saveSize;
        split.setOrientation(orientation);
        SplitPane.setResizableWithParent(first, false);
    }

    /**
     * The split pane, {@code first} beside or above {@code second}, as {@code orientation} lays them.
     *
     * @param size     the first part's width or height, in pixels, as the author last dragged it
     * @param saveSize remembers it, each time the author drags it
     */
    public static SplitPane of(Orientation orientation, Node first, Node second, double size,
                               DoubleConsumer saveSize) {

        KeptSizeSplit kept = new KeptSizeSplit(orientation, first, second, size, saveSize);
        kept.placeOnceSized();
        kept.saveOnRelease();

        return kept.split;
    }

    /** Places the divider once the split first has a size, as a share of it; from then on the first part keeps it. */
    private void placeOnceSized() {

        ObservableValue<Number> total = horizontal ? split.widthProperty() : split.heightProperty();
        total.addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number old, Number now) {

                if (now.doubleValue() <= 0)
                    return;

                split.setDividerPositions(Math.clamp(size / now.doubleValue(), 0.1, 0.9));
                total.removeListener(this);
            }
        });
    }

    /** Remembers the first part's size each time the author lets the divider go somewhere new. */
    private void saveOnRelease() {

        // Caught on its way down: the divider consumes the release it was dragged with.
        split.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            // Laid out first, so the size measured is the one the divider was let go at, not the one the last layout
            // left.
            split.layout();
            double now = horizontal ? first.getBoundsInParent().getWidth() : first.getBoundsInParent().getHeight();
            if (Math.round(now) != Math.round(size)) {
                size = now;
                saveSize.accept(now);
            }
        });
    }
}
