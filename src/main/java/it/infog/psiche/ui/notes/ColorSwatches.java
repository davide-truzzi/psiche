package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.model.enums.NoteColor;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** A row of swatches under a note's toolbar, one per colour, the note's own ringed. Picking one closes the row. */
final class ColorSwatches extends HBox {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

    private final Map<NoteColor, Button> swatches = new EnumMap<>(NoteColor.class);

    /** @param picked receives the colour picked */
    ColorSwatches(Consumer<NoteColor> picked) {

        for (NoteColor color : NoteColor.values()) {
            Button swatch = new Button();
            swatch.getStyleClass().addAll("note-swatch", "note-" + color.key());
            swatch.setTooltip(new Tooltip(color.label()));
            swatch.setOnAction(event -> {
                picked.accept(color);
                show(false);
            });
            swatches.put(color, swatch);
        }

        getChildren().setAll(swatches.values());
        getStyleClass().add("note-swatches");
        show(false);
    }

    /** Rings the swatch of {@code color}, the note's own. */
    void setCurrent(NoteColor color) {

        swatches.forEach((each, swatch) -> swatch.pseudoClassStateChanged(CURRENT, each == color));
    }

    void show(boolean shown) {

        setVisible(shown);
        setManaged(shown);
    }

    void toggle() {

        show(!isVisible());
    }
}
