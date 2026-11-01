package it.infog.psiche.ui.notes;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import static it.infog.psiche.ui.common.Controls.button;

/** The bar in the board's corner: New note, then − the zoom +, and Fit. */
final class BoardBar extends HBox {

    /** @param newNote adds a note in the middle of the part of the board in view */
    BoardBar(BoardViewport viewport, Runnable newNote) {

        Button create = button("New note", newNote);

        Region gap = new Region();
        gap.getStyleClass().add("notes-zoom-gap");

        Button out = button("−", viewport::zoomOut);
        out.setAccessibleText("Zoom out");

        Button in = button("+", viewport::zoomIn);
        in.setAccessibleText("Zoom in");

        Button fit = button("Fit", viewport::fit);

        Label zoom = new Label();
        zoom.textProperty().bind(viewport.zoomTextProperty());
        zoom.getStyleClass().add("notes-zoom-level");

        getChildren().addAll(create, gap, out, zoom, in, fit);
        getStyleClass().add("notes-zoom-bar");
        setAlignment(Pos.CENTER_RIGHT);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }
}
