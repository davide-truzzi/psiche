package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import static it.infog.psiche.ui.preview.PreviewPane.FOLLOW_POINT;

/**
 * Brass dots in the manuscript's left margin where its comments are: one on each line in view where a comment starts,
 * or where one that started above the view first shows, following it as the text scrolls; and one in the top and the
 * bottom margins while comments lie beyond the view that way, which a click scrolls to the next one. A layer over the
 * text, which only the edge dots take the mouse from.
 */
final class CommentMarks {

    private static final String MARK = "comment-mark";
    private static final String EDGE = "comment-edge";
    private static final String ABOVE = "%s above";
    private static final String BELOW = "%s below";

    private final StyleClassedTextArea area;
    private final Pane layer = new Pane();
    private final List<Region> lineMarks = new ArrayList<>();
    private final StackPane up = edge(-1);
    private final StackPane down = edge(1);
    private final Tooltip upTip = new Tooltip();
    private final Tooltip downTip = new Tooltip();
    // Places the dots after each layout of the window that needs it, once the text's lines are where they show.
    private final Runnable afterLayout = this::placeIfNeeded;
    private List<TextRange> comments = List.of();
    // True from a change in what is in view until the dots are placed again.
    private boolean outdated;
    // True from a click on an edge dot until the comment it went to is a third of the way down.
    private boolean lowering;
    // Where the first line in view was when the dots were last placed: the text can move without telling.
    private Bounds shownFrom;

    CommentMarks(StyleClassedTextArea area) {

        this.area = area;
        layer.getStyleClass().add("comment-marks");
        // Clicks go through the layer to the text, but for the edge dots.
        layer.setPickOnBounds(false);
        layer.getChildren().addAll(up, down);
        Tooltip.install(up, upTip);
        Tooltip.install(down, downTip);

        layer.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null)
                old.removePostLayoutPulseListener(afterLayout);
            if (scene != null)
                scene.addPostLayoutPulseListener(afterLayout);
        });

        // Scrolling, typing, resizing and a new font size all change what is in view.
        area.viewportDirtyEvents().subscribe(event -> place());
        layer.layoutBoundsProperty().addListener((obs, old, bounds) -> place());
        place();
    }

    /** The layer, to stack over the text, the same size as it. */
    Node node() {

        return layer;
    }

    /** Shows {@code comments}, where the text has them now, in order. */
    void setComments(List<TextRange> comments) {

        this.comments = comments;
        place();
    }

    /** Places the dots after the next layout: the text's lines are where they show only then. */
    private void place() {

        outdated = true;
        Platform.requestNextPulse();
    }

    private void placeIfNeeded() {

        //the comment an edge dot went to, now that the caret is on it, a third of the way down
        if (lowering) {
            lowering = false;
            area.getCaretBounds()
                    .map(area::screenToLocal)
                    .ifPresent(caret -> area.scrollYBy(caret.getMinY() - area.getHeight() * FOLLOW_POINT));
            place();
            return;
        }

        Bounds firstLine = firstLine();
        if (!outdated && Objects.equals(firstLine, shownFrom))
            return;

        outdated = false;
        shownFrom = firstLine;

        Insets padding = area.getPadding();
        double top = padding.getTop();
        double bottom = area.getHeight() - padding.getBottom();
        if (comments.isEmpty() || bottom <= top) {
            showLines(List.of());
            showEdges(List.of(), List.of());
            return;
        }

        //each comment above, below, or in view on the line it first shows on
        TextRange shown = shown();
        List<TextRange> above = new ArrayList<>();
        List<TextRange> below = new ArrayList<>();
        TreeSet<Double> lines = new TreeSet<>();
        for (TextRange comment : comments) {
            if (comment.end() <= shown.start())
                above.add(comment);
            else if (comment.start() > shown.end())
                below.add(comment);
            else
                lineCentre(Math.max(comment.start(), shown.start()))
                        .filter(y -> y >= top && y <= bottom)
                        .ifPresent(lines::add);
        }

        showLines(new ArrayList<>(lines));
        showEdges(above, below);
    }

    /** The characters in view: from the start of the first line shown to the end of the last. */
    private TextRange shown() {

        Insets padding = area.getPadding();
        int first = area.hit(padding.getLeft(), padding.getTop()).getInsertionIndex();
        int last = area.hit(area.getWidth() - padding.getRight(), area.getHeight() - padding.getBottom())
                .getInsertionIndex();
        return new TextRange(first, last);
    }

    /** Where the first line in view is on the screen; null while nothing is laid out. */
    private Bounds firstLine() {

        if (area.getLength() == 0)
            return null;

        int first = shown().start();
        return area.getCharacterBoundsOnScreen(first, Math.min(first + 1, area.getLength())).orElse(null);
    }

    /** The middle of the line character {@code index} is on, in the layer's coordinates; empty if not laid out. */
    private Optional<Double> lineCentre(int index) {

        int end = Math.min(index + 1, area.getLength());
        return area.getCharacterBoundsOnScreen(index, end)
                .map(layer::screenToLocal)
                .map(bounds -> (bounds.getMinY() + bounds.getMaxY()) / 2);
    }

    /** One dot in the left margin at each of {@code centres}, reusing the dots already there. */
    private void showLines(List<Double> centres) {

        while (lineMarks.size() < centres.size()) {
            Region mark = mark();
            mark.setMouseTransparent(true);
            lineMarks.add(mark);
            layer.getChildren().add(mark);
        }

        double x = marginCentre();
        for (int i = 0; i < lineMarks.size(); i++) {
            Region mark = lineMarks.get(i);
            mark.setVisible(i < centres.size());
            if (i < centres.size())
                centreAt(mark, x, centres.get(i));
        }
    }

    /** The edge dots, each shown while comments lie beyond the view its way, and saying how many. */
    private void showEdges(List<TextRange> above, List<TextRange> below) {

        Insets padding = area.getPadding();
        Bounds text = layer.sceneToLocal(area.localToScene(area.getLayoutBounds()));
        double x = marginCentre();

        up.setVisible(!above.isEmpty());
        upTip.setText(ABOVE.formatted(comments(above.size())));
        centreAt(up, x, text.getMinY() + padding.getTop() / 2);

        down.setVisible(!below.isEmpty());
        downTip.setText(BELOW.formatted(comments(below.size())));
        centreAt(down, x, text.getMaxY() - padding.getBottom() / 2);
    }

    /**
     * Scrolls to the nearest comment beyond the view in {@code direction}, -1 up or 1 down, with the caret on it, a
     * third of the way down the view, where the preview puts the place it follows.
     */
    private void goTo(int direction) {

        TextRange shown = shown();
        Optional<TextRange> next = direction < 0
                ? comments.stream().filter(comment -> comment.end() <= shown.start()).reduce((first, second) -> second)
                : comments.stream().filter(comment -> comment.start() > shown.end()).findFirst();
        next.ifPresent(comment -> {
            area.moveTo(comment.start());
            area.requestFollowCaret();
            area.requestFocus();
            // Following the caret brings it just inside the edge; once it is there, it goes a third of the way down.
            lowering = true;
            Platform.requestNextPulse();
        });
    }

    /** The middle of the left margin, in the layer's coordinates. */
    private double marginCentre() {

        return layer.sceneToLocal(area.localToScene(0, 0)).getX() + area.getPadding().getLeft() / 2;
    }

    private static void centreAt(Region node, double x, double y) {

        node.autosize();
        node.relocate(x - node.getWidth() / 2, y - node.getHeight() / 2);
    }

    private static Region mark() {

        Region mark = new Region();
        mark.getStyleClass().add(MARK);
        return mark;
    }

    /** A dot with room around it for the pointer, which scrolls to the next comment in {@code direction}. */
    private StackPane edge(int direction) {

        StackPane edge = new StackPane(mark());
        edge.getStyleClass().add(EDGE);
        edge.setVisible(false);
        edge.setOnMouseClicked(event -> goTo(direction));
        return edge;
    }

    /** "1 comment", or "3 comments". */
    private static String comments(int count) {

        return count == 1 ? "1 comment" : count + " comments";
    }
}
