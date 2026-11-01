package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.NoteLayout;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * The part of a chapter's board of {@value #BOARD_WIDTH} × {@value #BOARD_HEIGHT} pixels in view. The author pans by
 * dragging an empty spot or with the wheel, Shift + wheel sideways, and zooms around the pointer with Ctrl/⌘ + wheel or
 * a pinch, or by steps, from the whole board in view up to 200 %. The board never leaves the view, and is centred along
 * a side it does not fill. A second after the view settles, it is handed on to be saved.
 */
final class BoardViewport extends Pane {

    static final double BOARD_WIDTH = NoteLayout.BOARD_WIDTH;
    static final double BOARD_HEIGHT = NoteLayout.BOARD_HEIGHT;

    private static final double MAX_ZOOM = 2;
    private static final double ZOOM_STEP = 1.25;
    private static final double WHEEL_ZOOM_STEP = 1.1;
    private static final Duration SETTLE_DELAY = Duration.seconds(1);

    private final Pane board;
    private final Translate pan = new Translate();
    private final Scale zoom = new Scale(1, 1, 0, 0);
    private final ReadOnlyStringWrapper zoomText = new ReadOnlyStringWrapper("100%");
    private final PauseTransition settleDelay = new PauseTransition(SETTLE_DELAY);
    private final Consumer<BoardView> settled;
    private final Runnable emptySpotPressed;
    // Where the board opens, kept until the viewport has a size: a tab behind another may have none yet.
    private BoardView pending;
    // Where a pan began: the pointer, in the scene, and the board's offset then.
    private Point2D panStart;
    private Point2D panOrigin;

    /**
     * @param board            the board, sized and moved here
     * @param settled          receives the view a second after it stops changing, to keep it
     * @param emptySpotPressed runs when the author presses an empty spot of the board, as a pan begins
     */
    BoardViewport(Pane board, Consumer<BoardView> settled, Runnable emptySpotPressed) {

        this.board = board;
        this.settled = settled;
        this.emptySpotPressed = emptySpotPressed;

        //board
        board.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);
        board.getTransforms().addAll(pan, zoom);
        board.setCursor(Cursor.OPEN_HAND);
        board.setOnMousePressed(this::startPan);
        board.setOnMouseDragged(this::pan);
        board.setOnMouseReleased(event -> endPan());

        //viewport, showing only what is in view
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        getChildren().add(board);
        getStyleClass().add("notes-viewport");

        // Zooming is caught before the notes see it: a note's text would take the wheel for its own scrolling.
        addEventFilter(ScrollEvent.SCROLL, this::wheelZoom);
        addEventFilter(ZoomEvent.ZOOM, event -> {
            zoomAt(zoom.getX() * event.getZoomFactor(), event.getX(), event.getY());
            event.consume();
        });
        setOnScroll(this::scroll);
        widthProperty().addListener((obs, old, width) -> resized());
        heightProperty().addListener((obs, old, height) -> resized());

        settleDelay.setOnFinished(event -> settled.accept(view()));
    }

    /** The zoom as a percentage, "100%", for the corner bar. */
    ReadOnlyStringProperty zoomTextProperty() {

        return zoomText.getReadOnlyProperty();
    }

    /** Shows the board as {@code view} says, once the viewport has a size to show it in. */
    void open(BoardView view) {

        pending = view;
        applyPending();
    }

    /** Back to the board's top-left corner at its own size, with no view waiting. */
    void reset() {

        settleDelay.stop();
        pending = null;
        setView(1, 0, 0);
    }

    /** Hands on the view now if it was about to be, so nothing is lost. */
    void settle() {

        if (settleDelay.getStatus() != PauseTransition.Status.RUNNING)
            return;

        settleDelay.stop();
        settled.accept(view());
    }

    /** The board point in the middle of the view. */
    Point2D centre() {

        return new Point2D((getWidth() / 2 - pan.getX()) / zoom.getX(), (getHeight() / 2 - pan.getY()) / zoom.getX());
    }

    void zoomIn() {

        zoomAt(zoom.getX() * ZOOM_STEP, getWidth() / 2, getHeight() / 2);
    }

    void zoomOut() {

        zoomAt(zoom.getX() / ZOOM_STEP, getWidth() / 2, getHeight() / 2);
    }

    /** The board at its own size, around the middle of the view. */
    void actualSize() {

        zoomAt(1, getWidth() / 2, getHeight() / 2);
    }

    /** The whole board in view. */
    void fit() {

        double level = fitZoom();
        setView(level, (getWidth() - BOARD_WIDTH * level) / 2, (getHeight() - BOARD_HEIGHT * level) / 2);
        settleDelay.playFromStart();
    }

    // Panning

    private void startPan(MouseEvent event) {

        if (event.getTarget() != board || event.getButton() != MouseButton.PRIMARY)
            return;

        emptySpotPressed.run();
        requestFocus();
        panStart = new Point2D(event.getSceneX(), event.getSceneY());
        panOrigin = new Point2D(pan.getX(), pan.getY());
        board.setCursor(Cursor.CLOSED_HAND);
    }

    private void pan(MouseEvent event) {

        if (panStart == null)
            return;

        setView(zoom.getX(), panOrigin.getX() + event.getSceneX() - panStart.getX(),
                panOrigin.getY() + event.getSceneY() - panStart.getY());
    }

    private void endPan() {

        if (panStart == null)
            return;

        panStart = null;
        board.setCursor(Cursor.OPEN_HAND);
        settleDelay.playFromStart();
    }

    /** The wheel scrolls the board, with Shift sideways; a note's long text scrolls first, under the pointer. */
    private void scroll(ScrollEvent event) {

        double dx = event.getDeltaX();
        double dy = event.getDeltaY();
        if (event.isShiftDown() && dx == 0) {
            dx = dy;
            dy = 0;
        }
        setView(zoom.getX(), pan.getX() + dx, pan.getY() + dy);
        settleDelay.playFromStart();
        event.consume();
    }

    // Zooming

    /**
     * With Ctrl or ⌘, the wheel zooms around the pointer, a step for each notch. An event that moves nothing, as some
     * wheels and touchpads send, is left alone, so it is never taken for a step back.
     */
    private void wheelZoom(ScrollEvent event) {

        if (!event.isShortcutDown())
            return;

        if (event.getDeltaY() > 0)
            zoomAt(zoom.getX() * WHEEL_ZOOM_STEP, event.getX(), event.getY());
        else if (event.getDeltaY() < 0)
            zoomAt(zoom.getX() / WHEEL_ZOOM_STEP, event.getX(), event.getY());
        event.consume();
    }

    /** Zooms to {@code level}, keeping the board point under {@code x}, {@code y} of the viewport where it is. */
    private void zoomAt(double level, double x, double y) {

        double newZoom = Math.clamp(level, fitZoom(), MAX_ZOOM);
        double factor = newZoom / zoom.getX();
        setView(newZoom, x - (x - pan.getX()) * factor, y - (y - pan.getY()) * factor);
        settleDelay.playFromStart();
    }

    private double fitZoom() {

        if (getWidth() <= 0 || getHeight() <= 0)
            return 1;

        return Math.min(MAX_ZOOM, Math.min(getWidth() / BOARD_WIDTH, getHeight() / BOARD_HEIGHT));
    }

    // The view

    /**
     * Shows the board at {@code level}, offset by {@code x}, {@code y} in the viewport, kept so the board never leaves
     * the view, and centred along a side it does not fill.
     */
    private void setView(double level, double x, double y) {

        zoom.setX(level);
        zoom.setY(level);
        pan.setX(keepInView(x, getWidth(), BOARD_WIDTH * level));
        pan.setY(keepInView(y, getHeight(), BOARD_HEIGHT * level));
        zoomText.set(Math.round(level * 100) + "%");
    }

    private static double keepInView(double offset, double view, double extent) {

        if (extent <= view)
            return (view - extent) / 2;

        return Math.clamp(offset, view - extent, 0);
    }

    /** Keeps the view in bounds as the viewport changes size, and shows a board waiting for a size to open in. */
    private void resized() {

        if (pending != null)
            applyPending();
        else
            setView(zoom.getX(), pan.getX(), pan.getY());
    }

    private void applyPending() {

        if (pending == null || getWidth() <= 0 || getHeight() <= 0)
            return;

        double level = Math.clamp(pending.zoom(), fitZoom(), MAX_ZOOM);
        setView(level, -pending.x() * level, -pending.y() * level);
        pending = null;
    }

    private BoardView view() {

        double level = zoom.getX();
        return new BoardView(level, -pan.getX() / level, -pan.getY() / level);
    }
}
