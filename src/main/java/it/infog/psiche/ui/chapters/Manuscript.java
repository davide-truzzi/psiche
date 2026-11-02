package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.Logo;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.util.Map;

/**
 * The page the author writes on: the open chapter's text, wrapped, in the manuscript's font, over the welcome and its
 * logo until a book opens. It knows where the author is in the text, the caret while writing and the place in view
 * while scrolling to read, and where the text starts, so the status bar can line up with it. It is the only part of
 * the editor that knows the text is a {@link TextArea}.
 */
final class Manuscript {

    // Over writer.css's size for the manuscript, which is the default one.
    private static final String FONT_SIZE_STYLE = "-fx-font-size: %dpx;";

    private final TextArea area = new TextArea();
    // The logo behind the welcome, and the page that stacks the text over it.
    private final StackPane welcomeLogo = new StackPane();
    private final StackPane page = new StackPane(welcomeLogo, area);
    private final ReadOnlyDoubleWrapper textMargin = new ReadOnlyDoubleWrapper();
    private final ReadOnlyIntegerWrapper currentPosition = new ReadOnlyIntegerWrapper();
    // True once the author scrolls the manuscript, until the caret next moves.
    private boolean reading;

    /** @param welcome shown on the empty page from the start until a book opens; see {@link #hideWelcome} */
    Manuscript(String welcome) {

        area.getStyleClass().add("manuscript");
        area.setWrapText(true);
        area.setEditable(false);
        area.setPromptText(welcome);

        //the welcome's logo, sized to the page and never in the way of the page's own size or the mouse
        welcomeLogo.getChildren().add(Logo.lockup(welcomeLogo, 0.6));
        welcomeLogo.getStyleClass().add("welcome-logo");
        welcomeLogo.setMinSize(0, 0);
        welcomeLogo.setPrefSize(0, 0);
        welcomeLogo.setMouseTransparent(true);

        // The skin adds its nodes after the skin property changes, so look for them once it has.
        area.skinProperty().addListener((obs, old, skin) -> Platform.runLater(() -> {
            fitContentToText();
            trackTextMargin();
        }));

        //the current position: the caret's while writing, the one in view while scrolling to read
        area.caretPositionProperty().addListener((obs, old, caret) -> {
            reading = false;
            currentPosition.set(positionAtCaret());
        });
        area.textProperty().addListener((obs, old, text) -> {
            if (!reading)
                currentPosition.set(positionAtCaret());
        });
        area.addEventFilter(ScrollEvent.SCROLL, event -> reading = true);
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (onScrollBar(event))
                reading = true;
        });
        area.scrollTopProperty().addListener((obs, old, top) -> {
            if (reading)
                currentPosition.set(positionInView());
        });
    }

    Node node() {

        return page;
    }

    // The text

    ReadOnlyStringProperty textProperty() {

        return area.textProperty();
    }

    String text() {

        return area.getText();
    }

    int length() {

        return area.getLength();
    }

    /** Shows {@code text} in place of the text shown, which Undo cannot bring back. */
    void setText(String text) {

        area.setText(text);
    }

    /** Replaces the whole text with {@code text}, as one change a single Undo reverts, and starts at the top. */
    void replaceAll(String text) {

        // Unlike setText, which clears the undo history, replaceText records one change that a single Undo reverts.
        area.replaceText(0, area.getLength(), text);

        // Typing at the end of the replaced text would merge into the same undo change; start at the top instead.
        area.positionCaret(0);
        area.requestFocus();
    }

    /** Lets the author edit the text, from its start. */
    void startEditing() {

        area.setEditable(true);
        area.positionCaret(0);
        area.requestFocus();
    }

    /** Empties the page and makes it read-only. */
    void clear() {

        area.clear();
        area.setEditable(false);
    }

    /**
     * Removes the welcome and its logo for good, once a book is open. The welcome is the text area's prompt, which
     * shows whenever the text is empty, so it would otherwise fill every empty or new chapter as if it were the
     * chapter's text.
     */
    void hideWelcome() {

        area.setPromptText(null);
        page.getChildren().remove(welcomeLogo);
    }

    // How it looks, and where the author is

    /** Shows the text {@code fontSize} pixels high, its line spacing growing and shrinking with it. */
    void setFontSize(int fontSize) {

        area.setStyle(FONT_SIZE_STYLE.formatted(fontSize));
    }

    /** Where the text starts, in scene coordinates, so other parts of the window can line up with it. */
    ReadOnlyDoubleProperty textMarginProperty() {

        return textMargin.getReadOnlyProperty();
    }

    /**
     * The zero-based character of the text the author is at: the caret's while writing, and once they scroll to read,
     * the one a third of the way down the view, until the caret moves again.
     */
    ReadOnlyIntegerProperty currentPositionProperty() {

        return currentPosition.getReadOnlyProperty();
    }

    /** True once the author scrolls the manuscript to read, until the caret next moves. */
    boolean isReading() {

        return reading;
    }

    // Editing, for the formatting menu

    /**
     * Gives the text {@code menu} on a right-click, in place of the text area's own, and runs {@code shortcuts} ahead
     * of the text area's own key handling.
     */
    void setMenu(ContextMenu menu, Map<KeyCombination, Runnable> shortcuts) {

        area.setContextMenu(menu);
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> shortcuts.entrySet().stream()
                .filter(shortcut -> shortcut.getKey().match(event))
                .findFirst()
                .ifPresent(shortcut -> {
                    shortcut.getValue().run();
                    event.consume();
                }));
    }

    boolean isEditable() {

        return area.isEditable();
    }

    IndexRange selection() {

        return area.getSelection();
    }

    String selectedText() {

        return area.getSelectedText();
    }

    /** Replaces the text from {@code start} to {@code end} with {@code replacement}, as one change Undo reverts. */
    void replace(int start, int end, String replacement) {

        area.replaceText(start, end, replacement);
    }

    void select(int start, int end) {

        area.selectRange(start, end);
    }

    boolean canUndo() {

        return area.isUndoable();
    }

    boolean canRedo() {

        return area.isRedoable();
    }

    void undo() {

        area.undo();
    }

    void redo() {

        area.redo();
    }

    void cut() {

        area.cut();
    }

    void copy() {

        area.copy();
    }

    void paste() {

        area.paste();
    }

    void deleteSelection() {

        area.deleteText(area.getSelection());
    }

    void selectAll() {

        area.selectAll();
    }

    /**
     * Keeps the scrollable content at least as tall as the text it draws. The text area sizes its content without the
     * line spacing writer.css gives the text, so the end of a long chapter could not be scrolled to.
     */
    private void fitContentToText() {

        if (!(area.lookup(".content") instanceof Region content))
            return;

        // The prompt is a .text node of its own, directly in the content; the chapter's text sits in a group.
        content.lookupAll(".text").stream()
                .filter(node -> node instanceof Text && node.getParent() != content)
                .findFirst()
                .ifPresent(text -> {
                    Runnable fit = () -> content.setMinHeight(text.getLayoutBounds().getHeight()
                            + content.getPadding().getTop() + content.getPadding().getBottom());
                    text.layoutBoundsProperty().addListener((obs, old, bounds) -> fit.run());
                    content.paddingProperty().addListener((obs, old, padding) -> fit.run());
                    fit.run();
                });
    }

    /** Keeps {@link #textMarginProperty()} at the left edge of the text, inside the content's padding. */
    private void trackTextMargin() {

        if (!(area.lookup(".content") instanceof Region content))
            return;

        Runnable measure = () -> textMargin.set(content.localToScene(0, 0).getX() + content.getPadding().getLeft());
        area.localToSceneTransformProperty().addListener((obs, old, transform) -> measure.run());
        content.paddingProperty().addListener((obs, old, padding) -> measure.run());
        measure.run();
    }

    private int positionAtCaret() {

        // The caret can briefly lie past the end while the text is being replaced.
        return Math.min(area.getCaretPosition(), area.getLength());
    }

    /** The character a third of the way down the view, where the preview puts the position it follows. */
    private int positionInView() {

        if (!(area.getSkin() instanceof TextAreaSkin skin))
            return currentPosition.get();

        // The skin hit-tests in the coordinates of the scrolled content, so the view starts at the scroll offset.
        int index = skin.getIndex(0, area.getScrollTop() + area.getHeight() / 3).getInsertionIndex();
        return Math.min(index, area.getLength());
    }

    private static boolean onScrollBar(MouseEvent event) {

        for (Node node = event.getPickResult().getIntersectedNode(); node != null; node = node.getParent())
            if (node instanceof ScrollBar)
                return true;
        return false;
    }
}
