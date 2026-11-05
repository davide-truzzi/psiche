package it.infog.psiche.ui.notes;

import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.ui.common.Icons;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.EnumMap;
import java.util.Map;

/**
 * Above the board, across its width, the two boards the Notes tab shows: the open chapter's, after a page, and the
 * whole book's, after a book, each half named after them. The board on screen reads in paper with a brass line under
 * it, the other in graphite; a half whose board holds a note tagged Duplicate on Import gets a red-pencil dot, so the
 * tab's red title says where to look. One half is always chosen: Tab reaches the switch, the arrow keys change it.
 * <p>
 * While a note is dragged, the other board's half turns into a target, "Drop to move to Lorem", in dashed brass,
 * brighter while the pointer is over it: a note dropped there moves to that board.
 */
final class BoardSwitch extends HBox {

    private static final String NO_CHAPTER = "No chapter";
    private static final String TARGET = "notes-switch-target";
    private static final PseudoClass OVER = PseudoClass.getPseudoClass("over");

    private final ToggleGroup group = new ToggleGroup();
    private final Map<NotesBoard, ToggleButton> halves = new EnumMap<>(NotesBoard.class);
    private final Map<NotesBoard, Label> names = new EnumMap<>(NotesBoard.class);
    // Each half's own name, which a drop target's words stand in for while a note is dragged.
    private final Map<NotesBoard, String> titles = new EnumMap<>(NotesBoard.class);
    private final ReadOnlyObjectWrapper<NotesBoard> selected = new ReadOnlyObjectWrapper<>(NotesBoard.CHAPTER);

    // The half offered as a drop target while a note is dragged; null otherwise.
    private NotesBoard target;

    /** @param duplicates tells, for each board, whether a note on it carries the Duplicate on Import tag */
    BoardSwitch(BoardCounts duplicates) {

        getStyleClass().add("notes-switch");
        getChildren().addAll(half(NotesBoard.CHAPTER, "page", duplicates.duplicates(NotesBoard.CHAPTER)),
                half(NotesBoard.BOOK, "book", duplicates.duplicates(NotesBoard.BOOK)));

        // A click on the chosen half would leave none chosen.
        group.selectedToggleProperty().addListener((obs, old, toggle) -> {
            if (toggle == null)
                group.selectToggle(old);
            else
                selected.set((NotesBoard) toggle.getUserData());
        });
        group.selectToggle(halves.get(NotesBoard.CHAPTER));
        setChapter(null);
    }

    /** The board chosen. */
    ReadOnlyObjectProperty<NotesBoard> selectedProperty() {

        return selected.getReadOnlyProperty();
    }

    /** Shows {@code board} as the one chosen. */
    void select(NotesBoard board) {

        group.selectToggle(halves.get(board));
    }

    /** Names the left half after {@code chapter}, the open chapter; greys it out while none is open. */
    void setChapter(String chapter) {

        name(NotesBoard.CHAPTER, chapter == null ? NO_CHAPTER : chapter);
        halves.get(NotesBoard.CHAPTER).setDisable(chapter == null);
    }

    /** Names the right half after {@code title}, the open book's. */
    void setBook(String title) {

        name(NotesBoard.BOOK, title == null ? "" : title);
    }

    /**
     * Offers the half of {@code board} as the target of a note dragged with the pointer at a point of the scene, lit
     * while the pointer is over it; nothing for a greyed-out half.
     */
    void dragOver(NotesBoard board, double sceneX, double sceneY) {

        ToggleButton half = halves.get(board);
        if (half.isDisabled())
            return;

        if (target != board) {
            endDrag();
            target = board;
            half.getStyleClass().add(TARGET);
            names.get(board).setText("Drop to move to " + titles.get(board));
        }
        half.pseudoClassStateChanged(OVER, isOver(half, sceneX, sceneY));
    }

    /**
     * Ends a drag that let go of the note at a point of the scene, the target back to its name.
     *
     * @return true if the note was let go over the half of {@code board}, offered as the target
     */
    boolean dropAt(NotesBoard board, double sceneX, double sceneY) {

        boolean over = target == board && isOver(halves.get(board), sceneX, sceneY);
        endDrag();
        return over;
    }

    private void name(NotesBoard board, String title) {

        titles.put(board, title);
        if (board != target)
            names.get(board).setText(title);
    }

    /** Puts the half offered as a target, if any, back as it was. */
    private void endDrag() {

        if (target == null)
            return;

        ToggleButton half = halves.get(target);
        half.getStyleClass().remove(TARGET);
        half.pseudoClassStateChanged(OVER, false);
        names.get(target).setText(titles.get(target));
        target = null;
    }

    private static boolean isOver(Node node, double sceneX, double sceneY) {

        return node.localToScene(node.getBoundsInLocal()).contains(sceneX, sceneY);
    }

    /** A half: its icon, its name and the dot, as one toggle that fills half the width. */
    private ToggleButton half(NotesBoard board, String icon, ObservableBooleanValue duplicates) {

        SVGPath drawing = Icons.shape(icon);
        drawing.getStyleClass().add("notes-switch-icon");

        Label name = new Label();
        name.getStyleClass().add("notes-switch-name");
        names.put(board, name);

        Region dot = new Region();
        dot.getStyleClass().add("notes-switch-dot");
        dot.visibleProperty().bind(duplicates);
        dot.managedProperty().bind(duplicates);

        ToggleButton half = new ToggleButton();
        half.setGraphic(new HBox(drawing, name, dot));
        half.getGraphic().getStyleClass().add("notes-switch-content");
        half.getStyleClass().add("notes-switch-half");
        half.setUserData(board);
        half.setToggleGroup(group);
        // Halves of the same width, whatever their names say, a drop target's words included.
        half.setMinWidth(0);
        half.setPrefWidth(0);
        half.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(half, Priority.ALWAYS);
        halves.put(board, half);

        return half;
    }
}
