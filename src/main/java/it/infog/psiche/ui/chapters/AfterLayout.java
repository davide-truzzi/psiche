package it.infog.psiche.ui.chapters;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * When to place the marks a layer over the manuscript's text draws, as its margin dots or its line numbers: after the
 * layout of the window that follows a change in what is in view, as scrolling, typing, resizing and a new font size
 * make, or that follows a request, once the text's lines are where they show; and once more on the next pulse, as
 * RichTextFX may still move a paragraph after they are placed. Between changes it only checks, after each pulse, that
 * the first paragraph in view has not moved, as the text can without telling.
 */
final class AfterLayout {

    private final StyleClassedTextArea area;
    private final BooleanSupplier active;
    private final Runnable placement;
    private final Runnable afterPulse = this::placeIfNeeded;
    // True from a change in what is in view until the marks are placed again.
    private boolean outdated;
    // Where the first paragraph in view was when the marks were last placed.
    private Bounds shownFrom;
    // True from placing the marks after a change until they are placed once more, on the next pulse.
    private boolean settling;

    /**
     * @param layer     the layer the marks are on, over the text
     * @param active    false while the marks are hidden, when nothing is placed
     * @param placement places the marks where the text's lines are now
     */
    AfterLayout(StyleClassedTextArea area, Node layer, BooleanSupplier active, Runnable placement) {

        this.area = area;
        this.active = active;
        this.placement = placement;

        layer.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null)
                old.removePostLayoutPulseListener(afterPulse);
            if (scene != null)
                scene.addPostLayoutPulseListener(afterPulse);
        });

        area.viewportDirtyEvents().subscribe(event -> place());
        layer.layoutBoundsProperty().addListener((obs, old, bounds) -> place());
    }

    /** Places the marks after the next layout, while active: the text's lines are where they show only then. */
    void place() {

        if (!active.getAsBoolean())
            return;

        outdated = true;
        Platform.requestNextPulse();
    }

    private void placeIfNeeded() {

        if (!active.getAsBoolean())
            return;

        Bounds firstParagraph = firstParagraph();
        if (!outdated && !settling && Objects.equals(firstParagraph, shownFrom))
            return;

        settling = !settling;
        if (settling)
            Platform.requestNextPulse();
        outdated = false;
        shownFrom = firstParagraph;
        placement.run();
    }

    /**
     * Where the first paragraph in view is on the screen, by its own box; null while nothing is laid out. Asked after
     * every pulse, it must not ask where a character is: RichTextFX finds that by adding a shape to the paragraph and
     * taking it away, which changes the scene and so asks for another pulse, and then another, without end.
     */
    private Bounds firstParagraph() {

        if (area.getLength() == 0 || area.getVisibleParagraphs().isEmpty())
            return null;

        return area.getVisibleParagraphBoundsOnScreen(0);
    }
}
