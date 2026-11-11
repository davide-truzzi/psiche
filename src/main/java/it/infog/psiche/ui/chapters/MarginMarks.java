package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.ui.common.Icons;
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

import static it.infog.psiche.ui.preview.PreviewPane.FOLLOW_POINT;

/**
 * Dots in the manuscript's margin where parts of its text are, of one kind, such as its comments or its spelling
 * mistakes in the left margin, or its pinned notes in the right one: one on each line in view where such a part
 * starts, or where one that started above the view first shows, following it as the text scrolls; and one in the top
 * and the bottom margins while parts lie beyond the view that way, which a click scrolls to the next one. Each kind has
 * its own place across the margin, so dots of two kinds on one line sit side by side. A kind may draw an icon instead
 * of a dot, and give the marks on the lines a tooltip and an action of their own. A layer over the text, which only
 * the edge dots, and such marks, take the mouse from.
 */
final class MarginMarks {

    /** The margin the marks are in. */
    enum Side {
        LEFT,
        RIGHT
    }

    /** What the mark on a line tells and does, given the part it marks, by its place among the parts. */
    interface LineActions {

        String tip(int part);

        void clicked(int part);
    }

    /** A mark on a line: where its middle is, in the layer, and the first part it marks there. */
    private record Line(double centre,
                        int part) {
    }

    private static final String EDGE = "margin-edge";
    private static final String CLICKABLE = "margin-mark-clickable";
    private static final String ABOVE = "%s above";
    private static final String BELOW = "%s below";

    private final StyleClassedTextArea area;
    private final Side side;
    private final String markStyle;
    // The drawing of an icon in ui/icons, or null for a dot.
    private final String icon;
    private final double offset;
    private final String one;
    private final String many;
    private final Pane layer = new Pane();
    private final List<Region> lineMarks = new ArrayList<>();
    // The first part each mark on a line shows, and its tooltip if it has one, in the order of lineMarks.
    private final List<Integer> lineParts = new ArrayList<>();
    private final List<Tooltip> lineTips = new ArrayList<>();
    private final StackPane up;
    private final StackPane down;
    private final Tooltip upTip = new Tooltip();
    private final Tooltip downTip = new Tooltip();
    // Places the dots after each layout of the window that needs it, once the text's lines are where they show.
    private final Runnable afterLayout = this::placeIfNeeded;
    private List<TextRange> parts = List.of();
    // None while the marks on the lines are dots the mouse goes through.
    private LineActions actions;
    // True from a change in what is in view until the dots are placed again.
    private boolean outdated;
    // True from a click on an edge dot until the part it went to is a third of the way down.
    private boolean lowering;
    // Where the first paragraph in view was when the dots were last placed: the text can move without telling.
    private Bounds shownFrom;
    // True from placing the dots after a change until they are placed once more, on the next pulse.
    private boolean settling;

    /** Dots in the left margin; see the other constructor. */
    MarginMarks(StyleClassedTextArea area, String markStyle, double offset, String one, String many) {

        this(area, Side.LEFT, markStyle, null, offset, one, many);
    }

    /**
     * @param markStyle the style class of the marks, which gives them their colour
     * @param icon      the name of the drawing in ui/icons the marks show, or null for dots
     * @param offset    how far right of the margin's middle the marks sit, in pixels
     * @param one       one such part, as "comment", for the edge marks' tooltips
     * @param many      several, as "comments"
     */
    MarginMarks(StyleClassedTextArea area, Side side, String markStyle, String icon, double offset, String one,
                String many) {

        this.area = area;
        this.side = side;
        this.markStyle = markStyle;
        this.icon = icon;
        this.offset = offset;
        this.one = one;
        this.many = many;
        layer.getStyleClass().add("margin-marks");
        // Clicks go through the layer to the text, but for the edge dots.
        layer.setPickOnBounds(false);
        up = edge(-1);
        down = edge(1);
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

    /** Gives the marks on the lines a tooltip and an action, from {@code actions}, instead of letting the mouse by. */
    void setLineActions(LineActions actions) {

        this.actions = actions;
    }

    /** Shows {@code parts}, where the text has them now, in order. */
    void setParts(List<TextRange> parts) {

        this.parts = parts;
        place();
    }

    /** Places the dots after the next layout: the text's lines are where they show only then. */
    private void place() {

        outdated = true;
        Platform.requestNextPulse();
    }

    private void placeIfNeeded() {

        //the part an edge dot went to, now that the caret is on it, a third of the way down
        if (lowering) {
            lowering = false;
            area.getCaretBounds()
                    .map(area::screenToLocal)
                    .ifPresent(caret -> area.scrollYBy(caret.getMinY() - area.getHeight() * FOLLOW_POINT));
            place();
            return;
        }

        Bounds firstParagraph = firstParagraph();
        if (!outdated && !settling && Objects.equals(firstParagraph, shownFrom))
            return;

        // RichTextFX may still move a paragraph in this pulse, after the dots are placed: they are placed once more.
        settling = !settling;
        if (settling)
            Platform.requestNextPulse();
        outdated = false;
        shownFrom = firstParagraph;

        Insets padding = area.getPadding();
        double top = padding.getTop();
        double bottom = area.getHeight() - padding.getBottom();
        if (parts.isEmpty() || bottom <= top) {
            showLines(List.of());
            showEdges(0, 0);
            return;
        }

        //each part above, below, or in view on the line it first shows on; a line is looked up once, however many
        //parts it holds, as the parts come in order
        TextRange shown = shown();
        int above = 0;
        int below = 0;
        List<Line> lines = new ArrayList<>();
        int lineEnd = -1;
        for (int index = 0; index < parts.size(); index++) {
            TextRange part = parts.get(index);
            int first = Math.max(part.start(), shown.start());
            if (part.end() <= shown.start())
                above++;
            else if (part.start() > shown.end())
                below++;
            else if (first >= lineEnd) {
                Optional<Bounds> line = lineBounds(first);
                if (line.isEmpty())
                    continue;
                double centre = layer.screenToLocal(line.get()).getCenterY();
                if (centre >= top && centre <= bottom)
                    lines.add(new Line(centre, index));
                lineEnd = lineEnd(line.get());
            }
        }

        showLines(lines);
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

    /** Where character {@code index} is on the screen, which gives its line's height; empty if not laid out. */
    private Optional<Bounds> lineBounds(int index) {

        int end = Math.min(index + 1, area.getLength());
        return area.getCharacterBoundsOnScreen(index, end);
    }

    /**
     * Where the line of {@code character}, a character's bounds on the screen, ends in the text: no word that starts
     * there or after is on it.
     */
    private int lineEnd(Bounds character) {

        double y = area.screenToLocal(character).getCenterY();
        return area.hit(area.getWidth() - area.getPadding().getRight() - 1, y).getInsertionIndex();
    }

    /** One mark in the margin on each of {@code lines}, reusing the marks already there. */
    private void showLines(List<Line> lines) {

        while (lineMarks.size() < lines.size())
            addLineMark();

        double x = marginCentre();
        for (int i = 0; i < lineMarks.size(); i++) {
            Region mark = lineMarks.get(i);
            mark.setVisible(i < lines.size());
            if (i >= lines.size())
                continue;

            centreAt(mark, x, lines.get(i).centre());
            lineParts.set(i, lines.get(i).part());
            if (actions != null)
                lineTips.get(i).setText(actions.tip(lines.get(i).part()));
        }
    }

    /** One more mark for a line: a dot the mouse goes through, or one with a tooltip and an action. */
    private void addLineMark() {

        Region mark = mark();
        int place = lineMarks.size();
        lineMarks.add(mark);
        lineParts.add(-1);
        layer.getChildren().add(mark);
        if (actions == null) {
            mark.setMouseTransparent(true);
            lineTips.add(null);
            return;
        }

        Tooltip tip = new Tooltip();
        Tooltip.install(mark, tip);
        lineTips.add(tip);
        mark.getStyleClass().add(CLICKABLE);
        mark.setOnMouseClicked(event -> actions.clicked(lineParts.get(place)));
    }

    /** The edge dots, each shown while parts lie beyond the view its way, and saying how many. */
    private void showEdges(int above, int below) {

        Insets padding = area.getPadding();
        Bounds text = layer.sceneToLocal(area.localToScene(area.getLayoutBounds()));
        double x = marginCentre();

        up.setVisible(above > 0);
        upTip.setText(ABOVE.formatted(count(above)));
        centreAt(up, x, text.getMinY() + padding.getTop() / 2);

        down.setVisible(below > 0);
        downTip.setText(BELOW.formatted(count(below)));
        centreAt(down, x, text.getMaxY() - padding.getBottom() / 2);
    }

    /**
     * Scrolls to the nearest part beyond the view in {@code direction}, -1 up or 1 down, with the caret on it, a third
     * of the way down the view, where the preview puts the place it follows.
     */
    private void goTo(int direction) {

        TextRange shown = shown();
        Optional<TextRange> next = direction < 0
                ? parts.stream().filter(part -> part.end() <= shown.start()).reduce((first, second) -> second)
                : parts.stream().filter(part -> part.start() > shown.end()).findFirst();
        next.ifPresent(part -> {
            area.moveTo(part.start());
            area.requestFollowCaret();
            area.requestFocus();
            // Following the caret brings it just inside the edge; once it is there, it goes a third of the way down.
            lowering = true;
            Platform.requestNextPulse();
        });
    }

    /** Where the marks sit across their margin, its middle and this kind's offset, in the layer's coordinates. */
    private double marginCentre() {

        double left = layer.sceneToLocal(area.localToScene(0, 0)).getX();
        return switch (side) {
            case LEFT -> left + area.getPadding().getLeft() / 2 + offset;
            case RIGHT -> left + area.getWidth() - area.getPadding().getRight() / 2 + offset;
        };
    }

    private static void centreAt(Region node, double x, double y) {

        node.autosize();
        node.relocate(x - node.getWidth() / 2, y - node.getHeight() / 2);
    }

    private Region mark() {

        Region mark = new Region();
        mark.getStyleClass().add(markStyle);
        if (icon != null)
            mark.setShape(Icons.shape(icon));
        return mark;
    }

    /** A dot with room around it for the pointer, which scrolls to the next part in {@code direction}. */
    private StackPane edge(int direction) {

        StackPane edge = new StackPane(mark());
        edge.getStyleClass().add(EDGE);
        edge.setVisible(false);
        edge.setOnMouseClicked(event -> goTo(direction));
        return edge;
    }

    /** "1 comment", or "3 comments". */
    private String count(int count) {

        return count == 1 ? "1 " + one : count + " " + many;
    }
}
