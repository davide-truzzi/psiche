package it.infog.psiche.ui.notes;

import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.ui.common.Icons;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableBooleanValue;
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
 */
final class BoardSwitch extends HBox {

    private static final String NO_CHAPTER = "No chapter";

    private final ToggleGroup group = new ToggleGroup();
    private final Map<NotesBoard, ToggleButton> halves = new EnumMap<>(NotesBoard.class);
    private final Map<NotesBoard, Label> names = new EnumMap<>(NotesBoard.class);
    private final ReadOnlyObjectWrapper<NotesBoard> selected = new ReadOnlyObjectWrapper<>(NotesBoard.CHAPTER);

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

        names.get(NotesBoard.CHAPTER).setText(chapter == null ? NO_CHAPTER : chapter);
        halves.get(NotesBoard.CHAPTER).setDisable(chapter == null);
    }

    /** Names the right half after {@code title}, the open book's. */
    void setBook(String title) {

        names.get(NotesBoard.BOOK).setText(title == null ? "" : title);
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
        half.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(half, Priority.ALWAYS);
        halves.put(board, half);

        return half;
    }
}
