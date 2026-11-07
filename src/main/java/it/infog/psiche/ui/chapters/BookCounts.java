package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.markdown.model.TextCount;
import it.infog.psiche.ui.common.Icons;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.setShown;

/**
 * The whole book's characters, words and pages, under the chapter list, one to a line, counted as the status bar
 * counts the open chapter, from the chapters as saved, which {@link BookReading} reads. Clicking "Book counts…"
 * collapses them to that line, or opens them again. Used on the FX thread.
 */
final class BookCounts {

    private static final PseudoClass COLLAPSED = PseudoClass.getPseudoClass("collapsed");

    private final Label characters = new Label();
    private final Label words = new Label();
    private final Label pages = new Label();
    private final VBox numbers = new VBox(characters, words, pages);
    private final VBox box;
    private final ObservableValue<PageMeasure> measure;
    private final Consumer<Boolean> remember;
    // None until the book is first read.
    private TextCount count;

    /**
     * @param measure  how pages are counted, as the status bar counts them
     * @param pageMenu the menu to choose how pages are counted, shown on a right-click
     * @param shown    true to start open, false collapsed
     * @param remember told whether they are open each time the author opens or collapses them
     */
    BookCounts(ObservableValue<PageMeasure> measure, ContextMenu pageMenu, boolean shown, Consumer<Boolean> remember) {

        this.measure = measure;
        this.remember = remember;

        //the heading, which opens and collapses the numbers
        Region arrow = new Region();
        arrow.setShape(Icons.shape("list-arrow"));
        arrow.getStyleClass().add("book-counts-arrow");
        HBox heading = new HBox(arrow, new Label("Book counts…"));
        heading.getStyleClass().add("book-counts-heading");
        heading.setOnMouseClicked(event -> chooseShown(!numbers.isVisible()));
        Tooltip.install(heading, new Tooltip("The chapters as saved. Click to show or hide"));

        numbers.getStyleClass().add("book-counts-numbers");
        box = new VBox(heading, numbers);
        box.getStyleClass().add("book-counts");
        numbers.setOnContextMenuRequested(event -> pageMenu.show(numbers, event.getScreenX(), event.getScreenY()));
        measure.addListener((obs, old, chosen) -> show());

        showNumbers(shown);
        show();
    }

    /** The counts, to place under the chapter list. */
    Node node() {

        return box;
    }

    /** Shows {@code counted}, the whole book's characters and words, as saved. */
    void show(TextCount counted) {

        count = counted;
        show();
    }

    /** Hides the counts, as no book is open. */
    void clear() {

        count = null;
        show();
    }

    private void chooseShown(boolean shown) {

        showNumbers(shown);
        remember.accept(shown);
    }

    private void showNumbers(boolean shown) {

        setShown(numbers, shown);
        box.pseudoClassStateChanged(COLLAPSED, !shown);
    }

    private void show() {

        boolean shown = count != null;
        setShown(box, shown);
        if (!shown)
            return;

        characters.setText(String.format(Locale.ENGLISH, "%,d characters", count.characters()));
        words.setText(String.format(Locale.ENGLISH, "%,d words", count.words()));
        pages.setText(measure.getValue().describe(count.characters(), count.words()));
    }
}
