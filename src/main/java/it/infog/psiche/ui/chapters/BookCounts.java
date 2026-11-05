package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.markdown.TextCounter;
import it.infog.psiche.markdown.model.TextCount;
import it.infog.psiche.ui.common.Icons;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.setShown;

/**
 * The whole book's characters, words and pages, under the chapter list, one to a line, counted as the status bar
 * counts the open chapter. They count the chapters as saved, on a thread of their own, when the book opens, when a
 * chapter is saved, and when one is added, removed or renamed, so typing never recounts the book. Clicking
 * "Book counts…" collapses them to that line, or opens them again. Used on the FX thread.
 */
final class BookCounts {

    private static final Logger log = LoggerFactory.getLogger(BookCounts.class);
    private static final PseudoClass COLLAPSED = PseudoClass.getPseudoClass("collapsed");

    private final ExecutorService counter =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("book-counts").daemon().factory());
    private final Label characters = new Label();
    private final Label words = new Label();
    private final Label pages = new Label();
    private final VBox numbers = new VBox(characters, words, pages);
    private final VBox box;
    private final ObservableValue<PageMeasure> measure;
    private final Consumer<Boolean> remember;
    // None until the first count of a book comes back.
    private TextCount count;
    // Counting again starts a new round; a slower count of an earlier round is dropped when it comes back.
    private int round;

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

    /** Counts {@code chapters} of {@code book} again, as saved; a chapter that cannot be read is left out. */
    void count(BookFolder book, List<Chapter> chapters) {

        int counting = ++round;
        counter.execute(() -> {
            int allCharacters = 0;
            int allWords = 0;
            for (Chapter chapter : chapters) {
                try {
                    TextCount chapterCount = TextCounter.count(book.read(chapter));
                    allCharacters += chapterCount.characters();
                    allWords += chapterCount.words();
                } catch (UncheckedIOException e) {
                    log.warn("Cannot count {}", chapter.title(), e);
                }
            }

            TextCount counted = new TextCount(allCharacters, allWords);
            Platform.runLater(() -> {
                if (counting != round)
                    return;
                count = counted;
                show();
            });
        });
    }

    /** Hides the counts, as no book is open, and drops a count still on its way. */
    void clear() {

        round++;
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
