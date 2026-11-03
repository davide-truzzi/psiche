package it.infog.psiche.ui.common;

import it.infog.psiche.spelling.Dictionaries;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A green tick right after a language field while Psiche has a spelling dictionary for the language chosen, the one
 * that checks the books written in it; nothing for a language without one, though the tick's room stays, so the field
 * never changes width.
 */
public final class DictionaryMark {

    private static final String TIP = "Spelling dictionary available";

    private DictionaryMark() {
    }

    /** {@code choice}, as wide as it can be, with the tick after it, following the language chosen. */
    public static HBox after(LanguageChoice choice) {

        Region tick = new Region();
        tick.setShape(Icons.shape("check"));
        tick.getStyleClass().add("dictionary-mark");
        Tooltip.install(tick, new Tooltip(TIP));

        Runnable follow = () -> tick.setVisible(Dictionaries.forBook(choice.getValue().toLanguageTag()).isPresent());
        choice.valueProperty().addListener((obs, old, language) -> follow.run());
        follow.run();

        HBox row = new HBox(choice, tick);
        row.getStyleClass().add("language-row");
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(choice, Priority.ALWAYS);
        return row;
    }
}
