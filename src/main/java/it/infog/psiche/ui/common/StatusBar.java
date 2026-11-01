package it.infog.psiche.ui.common;

import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.markdown.model.TextCount;
import javafx.animation.FadeTransition;
import javafx.beans.value.ObservableDoubleValue;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.util.Locale;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.hSpacer;

/**
 * The open chapter's characters, words and pages on the left, lined up with the editor's text, and on the right the
 * latest message, the chapter's title and its save state. Right-clicking the pages chooses how they are counted.
 */
public final class StatusBar extends HBox {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass DIRTY = PseudoClass.getPseudoClass("dirty");
    private static final Duration MESSAGE_FADE_IN = Duration.millis(260);

    private final Label message = new Label();
    private final Label counts = new Label();
    private final Label pages = new Label();
    private final Label chapterState = new Label();
    private final ToggleGroup measures = new ToggleGroup();

    private Consumer<PageMeasure> onPageMeasureChosen = measure -> {};
    private PageMeasure pageMeasure = PageMeasure.STANDARD;
    private TextCount count;

    public StatusBar() {

        message.getStyleClass().add("status");
        counts.getStyleClass().add("chapter-counts");
        pages.getStyleClass().addAll("chapter-counts", "page-count");
        chapterState.getStyleClass().add("chapter-state");

        pages.setContextMenu(pageMeasureMenu());
        pages.setTooltip(new Tooltip("Right-click to choose how pages are counted"));
        showCount(null);

        getChildren().addAll(counts, pages, hSpacer(), message, chapterState);
        getStyleClass().add("status-bar");
    }

    public void info(String text) {

        showMessage(text, false);
    }

    public void error(String text) {

        showMessage(text, true);
    }

    public void showChapter(String title, boolean dirty) {

        chapterState.setText(title + (dirty ? "  ·  unsaved" : "  ·  saved"));
        chapterState.pseudoClassStateChanged(DIRTY, dirty);
    }

    /** Clears the open chapter's title, save state and counts. */
    public void clearChapter() {

        chapterState.setText("");
        showCount(null);
    }

    /** @param count the open chapter's characters and words; {@code null} hides the counts */
    public void showCount(TextCount count) {

        this.count = count;
        boolean shown = count != null;
        for (Label label : new Label[] {counts, pages}) {
            label.setVisible(shown);
            label.setManaged(shown);
        }
        if (!shown)
            return;

        counts.setText(String.format(Locale.ENGLISH, "%,d characters  ·  %,d words", count.characters(), count.words()));
        pages.setText(pageMeasure.describe(count.characters(), count.words()));
    }

    /** Counts pages by {@code measure} from now on, and marks it in the menu. */
    public void setPageMeasure(PageMeasure measure) {

        pageMeasure = measure;
        measures.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == measure)
                .findFirst()
                .ifPresent(measures::selectToggle);
        showCount(count);
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

    private ContextMenu pageMeasureMenu() {

        ContextMenu menu = new ContextMenu();
        for (PageMeasure measure : PageMeasure.values()) {
            RadioMenuItem item = new RadioMenuItem(measure.label());
            item.setUserData(measure);
            item.setToggleGroup(measures);
            item.setOnAction(event -> onPageMeasureChosen.accept(measure));
            menu.getItems().add(item);
        }

        return menu;
    }

    private void showMessage(String text, boolean isError) {

        message.setText(text);
        message.pseudoClassStateChanged(ERROR, isError);

        FadeTransition fade = new FadeTransition(MESSAGE_FADE_IN, message);
        fade.setFromValue(0.2);
        fade.setToValue(1);
        fade.play();
    }
}
