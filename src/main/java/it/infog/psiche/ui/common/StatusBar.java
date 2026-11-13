package it.infog.psiche.ui.common;

import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.markdown.model.TextCount;
import javafx.animation.FadeTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableDoubleValue;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.hSpacer;
import static it.infog.psiche.ui.common.Controls.setShown;

/**
 * The open chapter's characters, words and pages on the left, lined up with the editor's text, then how many times
 * the word the author is at comes back in it, and on the right the latest message, the chapter's title and its save
 * state. Right-clicking the pages chooses how they are counted.
 */
public final class StatusBar extends HBox {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass DIRTY = PseudoClass.getPseudoClass("dirty");
    private static final Duration MESSAGE_FADE_IN = Duration.millis(260);
    // A longer selection is cut, so the count stays beside the pages.
    private static final int REPEATED_WORD_LENGTH = 24;
    // A long task's progress, as blocks: one for each tenth done.
    private static final int PROGRESS_BLOCKS = 10;
    private static final String DONE_BLOCK = "▮";
    private static final String LEFT_BLOCK = "▯";

    private final Label message = new Label();
    // A file the message names, after it: clicking shows it in the system's file manager.
    private final Label fileLink = new Label();
    private final HBox messageBox = new HBox(message, fileLink);
    private final Label counts = new Label();
    private final Label pages = new Label();
    private final Label repetitions = new Label();
    private final Label chapterState = new Label();
    private final ReadOnlyObjectWrapper<PageMeasure> pageMeasure = new ReadOnlyObjectWrapper<>(PageMeasure.STANDARD);

    private Consumer<PageMeasure> onPageMeasureChosen = measure -> {};
    private TextCount count;

    public StatusBar() {

        message.getStyleClass().add("status");
        fileLink.getStyleClass().addAll("status", "status-link");
        // A long path loses its start, so the file's name stays in sight.
        fileLink.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        fileLink.setTooltip(new Tooltip("Show in its folder"));
        fileLink.setOnMouseClicked(event -> FileManager.reveal(Path.of(fileLink.getText())));
        setShown(fileLink, false);
        messageBox.getStyleClass().add("status-message");
        counts.getStyleClass().add("chapter-counts");
        pages.getStyleClass().addAll("chapter-counts", "page-count");
        repetitions.getStyleClass().add("chapter-counts");
        // Short, so a long message is the one cut short, not the counts.
        for (Label label : List.of(counts, pages, repetitions))
            label.setMinWidth(Region.USE_PREF_SIZE);
        chapterState.getStyleClass().add("chapter-state");

        pages.setContextMenu(pageMeasureMenu());
        pages.setTooltip(new Tooltip("Right-click to choose how pages are counted"));
        showCount(null);
        showRepetitions("", 0);

        getChildren().addAll(counts, pages, repetitions, hSpacer(), messageBox, chapterState);
        getStyleClass().add("status-bar");
    }

    public void info(String text) {

        showMessage(text, false);
    }

    public void error(String text) {

        showMessage(text, true);
    }

    /** Shows {@code text} followed by {@code file}, which the author clicks to see it in its folder. */
    public void info(String text, Path file) {

        showMessage(text, false);
        fileLink.setText(file.toAbsolutePath().toString());
        setShown(fileLink, true);
    }

    /**
     * Shows how far a long task has come, as "Exporting Lorem ▮▮▮▮▯▯▯▯▯▯ 42 %": {@code task} says what it does, and
     * {@code percent} is done of it. Shown at once, without the fade of a message, as it changes often.
     */
    public void progress(String task, int percent) {

        int done = Math.clamp(percent, 0, 100) * PROGRESS_BLOCKS / 100;
        message.setText("%s  %s%s  %d %%".formatted(task, DONE_BLOCK.repeat(done),
                LEFT_BLOCK.repeat(PROGRESS_BLOCKS - done), percent));
        message.pseudoClassStateChanged(ERROR, false);
        setShown(fileLink, false);
    }

    public void showChapter(String title, boolean dirty) {

        chapterState.setText(title + (dirty ? "  ·  unsaved" : "  ·  saved"));
        chapterState.pseudoClassStateChanged(DIRTY, dirty);
    }

    /** Clears the open chapter's title, save state and counts. */
    public void clearChapter() {

        chapterState.setText("");
        showCount(null);
        showRepetitions("", 0);
    }

    /** @param count the open chapter's characters and words; {@code null} hides the counts */
    public void showCount(TextCount count) {

        this.count = count;
        boolean shown = count != null;
        for (Label label : new Label[] {counts, pages}) {
            setShown(label, shown);
        }
        if (!shown)
            return;

        counts.setText(String.format(Locale.ENGLISH, "%,d characters  ·  %,d words", count.characters(), count.words()));
        pages.setText(pageMeasure.get().describe(count.characters(), count.words()));
    }

    /**
     * Shows that {@code word}, the one the author is at, is {@code times} times in the open chapter; fewer than two
     * hides the count, as the word does not come back.
     */
    public void showRepetitions(String word, int times) {

        boolean shown = times > 1;
        setShown(repetitions, shown);
        if (!shown)
            return;

        String shownWord = word.length() > REPEATED_WORD_LENGTH ? word.substring(0, REPEATED_WORD_LENGTH) + "…" : word;
        repetitions.setText(String.format(Locale.ENGLISH, "“%s” %,d times", shownWord, times));
    }

    /** Counts pages by {@code measure} from now on, which the menus mark. */
    public void setPageMeasure(PageMeasure measure) {

        pageMeasure.set(measure);
        showCount(count);
    }

    /** How pages are counted, for other counts to count them alike. */
    public ReadOnlyObjectProperty<PageMeasure> pageMeasureProperty() {

        return pageMeasure.getReadOnlyProperty();
    }

    /** Keeps the counts starting at {@code margin}, a position in scene coordinates such as the editor's text margin. */
    public void alignCountsTo(ObservableDoubleValue margin) {

        Runnable align = () -> HBox.setMargin(counts, new Insets(0, 0, 0,
                Math.max(0, margin.get() - localToScene(0, 0).getX() - getPadding().getLeft())));
        margin.addListener((obs, old, value) -> align.run());
        localToSceneTransformProperty().addListener((obs, old, transform) -> align.run());
        paddingProperty().addListener((obs, old, padding) -> align.run());
        align.run();
    }

    /** Runs {@code action} when the author picks a page measure from the menu. */
    public void setOnPageMeasureChosen(Consumer<PageMeasure> action) {

        onPageMeasureChosen = action;
    }

    /**
     * A new menu to choose how pages are counted, as right-clicking the pages shows, for other counts to offer too;
     * the measure in use is marked each time it opens.
     */
    public ContextMenu pageMeasureMenu() {

        ContextMenu menu = new ContextMenu();
        ToggleGroup measures = new ToggleGroup();
        for (PageMeasure measure : PageMeasure.values()) {
            RadioMenuItem item = new RadioMenuItem(measure.label());
            item.setUserData(measure);
            item.setToggleGroup(measures);
            item.setOnAction(event -> onPageMeasureChosen.accept(measure));
            menu.getItems().add(item);
        }
        menu.setOnShowing(event -> measures.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == pageMeasure.get())
                .findFirst()
                .ifPresent(measures::selectToggle));

        return menu;
    }

    private void showMessage(String text, boolean isError) {

        message.setText(text);
        message.pseudoClassStateChanged(ERROR, isError);
        setShown(fileLink, false);

        FadeTransition fade = new FadeTransition(MESSAGE_FADE_IN, messageBox);
        fade.setFromValue(0.2);
        fade.setToValue(1);
        fade.play();
    }
}
