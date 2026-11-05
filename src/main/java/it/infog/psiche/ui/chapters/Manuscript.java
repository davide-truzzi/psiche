package it.infog.psiche.ui.chapters;

import it.infog.psiche.spelling.SpellChecker;
import it.infog.psiche.spelling.model.Word;
import it.infog.psiche.ui.common.Logo;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.util.UndoUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static it.infog.psiche.ui.preview.PreviewPane.FOLLOW_POINT;

/**
 * The page the author writes on: the open chapter's text, wrapped, in the manuscript's font, over the welcome and its
 * logo until a book opens. It knows where the author is in the text, the caret while writing and the place in view
 * while scrolling to read, and where the text starts, so the status bar can line up with it. Its text is a RichTextFX
 * {@link StyleClassedTextArea}, which only it and the parts it is made of know: the {@link PlainTextArea}, the
 * {@link TextMarks} and the {@link Repetitions}.
 */
final class Manuscript {

    // Over writer.css's size for the manuscript, which is the default one.
    private static final String FONT_SIZE_STYLE = "-fx-font-size: %dpx;";

    private final StyleClassedTextArea area = new PlainTextArea();
    // The page, with its scroll bar, around the text, which draws only the paragraphs in view.
    private final VirtualizedScrollPane<StyleClassedTextArea> scrolled = new VirtualizedScrollPane<>(area);
    private final TextMarks marks = new TextMarks(area);
    private final Repetitions repetitions;
    // The logo behind the welcome, and the page that stacks the text over it, and the marks' dots over the text.
    private final StackPane welcomeLogo = new StackPane();
    private final StackPane page = new StackPane(welcomeLogo, scrolled);
    private final Label welcome;
    private final ReadOnlyDoubleWrapper textMargin = new ReadOnlyDoubleWrapper();
    private final ReadOnlyIntegerWrapper currentPosition = new ReadOnlyIntegerWrapper();
    // Where the right-click menu was last asked for, in the text: under the pointer, or at the caret from the keyboard.
    private int menuIndex;
    // True once the author scrolls the manuscript, until the caret next moves.
    private boolean reading;

    /**
     * @param welcome            shown on the empty page while no book is open; see {@link #hideWelcome}
     * @param repetitionsCounted told the word the author is at and how many times it is in the text, while it comes
     *                           back; an empty word and 0 when it does not
     */
    Manuscript(String welcome, BiConsumer<String, Integer> repetitionsCounted) {

        area.getStyleClass().add("manuscript");
        scrolled.getStyleClass().add("manuscript-scroll");
        page.getStyleClass().add("manuscript-page");
        // The dots in the margin, over the text.
        page.getChildren().addAll(marks.layers());
        area.setWrapText(true);
        area.setEditable(false);
        // Undo takes back what the author typed, not the comments' colour and the mistakes' lines that follow it.
        area.setUndoManager(UndoUtils.plainTextUndoManager(area));
        // Kept by the area it listens to.
        new CaretBlink(area);
        repetitions = new Repetitions(area, repetitionsCounted);
        // Each change says where it was, so the mistakes found already can move with the text.
        area.plainTextChanges().subscribe(change -> {
            marks.follow(change);
            repetitions.textChanged();
        });
        area.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> menuIndex = event.isKeyboardTrigger()
                ? area.getCaretPosition()
                : area.hit(event.getX(), event.getY()).getInsertionIndex());
        this.welcome = welcome(welcome);
        showWelcome();

        //the welcome's logo, sized to the page and never in the way of the page's own size or the mouse
        welcomeLogo.getChildren().add(Logo.lockup(welcomeLogo, 0.6));
        welcomeLogo.getStyleClass().add("welcome-logo");
        welcomeLogo.setMinSize(0, 0);
        welcomeLogo.setPrefSize(0, 0);
        welcomeLogo.setMouseTransparent(true);

        //where the text starts: the text's left edge, inside its padding
        Runnable measure = () -> textMargin.set(area.localToScene(0, 0).getX() + area.getPadding().getLeft());
        area.localToSceneTransformProperty().addListener((obs, old, transform) -> measure.run());
        area.paddingProperty().addListener((obs, old, padding) -> measure.run());

        //the current position: the caret's while writing, the one in view while scrolling to read
        area.caretPositionProperty().addListener((obs, old, caret) -> {
            reading = false;
            currentPosition.set(positionAtCaret());
        });
        area.textProperty().addListener((obs, old, text) -> {
            if (!reading)
                currentPosition.set(positionAtCaret());
        });
        // On the page rather than the text, so the wheel over the scroll bar counts too.
        scrolled.addEventFilter(ScrollEvent.SCROLL, event -> reading = true);
        scrolled.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (onScrollBar(event))
                reading = true;
        });
        // The scroll also moves while the text changes, before the view is laid out again, so look once it is.
        area.estimatedScrollYProperty().addListener((obs, old, top) -> {
            if (reading)
                Platform.runLater(() -> {
                    if (reading)
                        currentPosition.set(positionInView());
                });
        });
    }

    Node node() {

        return page;
    }

    // The text

    ObservableValue<String> textProperty() {

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

        // A new text is read from its start, not from where the old one was scrolled to.
        reading = false;
        area.replaceText(text);
        area.getUndoManager().forgetHistory();
        marks.checkSpellingNow();
    }

    /** Replaces the whole text with {@code text}, as one change a single Undo reverts, and starts at the top. */
    void replaceAll(String text) {

        reading = false;
        area.replaceText(0, area.getLength(), text);

        // Typing at the top would otherwise merge into the same undo change.
        area.getUndoManager().preventMerge();
        showStart();
    }

    /** Lets the author edit the text, from its start. */
    void startEditing() {

        area.setEditable(true);
        showStart();
    }

    /** Lets the author edit the text, or only read it, as while the book is being exported. */
    void setEditable(boolean editable) {

        area.setEditable(editable);
    }

    /** Empties the page and makes it read-only. */
    void clear() {

        setText("");
        area.setEditable(false);
    }

    /** Shows the welcome and its logo on the empty page, as when the app starts, once the book closes. */
    void showWelcome() {

        area.setPlaceholder(welcome, Pos.TOP_LEFT);
        // The placeholder hides while the text has the focus, so the welcome takes no click nor Tab until it goes.
        area.setMouseTransparent(true);
        area.setFocusTraversable(false);
        // The text kept the focus it had while a chapter was open, which would hide the welcome.
        if (area.isFocused())
            page.requestFocus();
        if (!page.getChildren().contains(welcomeLogo))
            page.getChildren().addFirst(welcomeLogo);
    }

    /**
     * Removes the welcome and its logo while a book is open. The welcome is the text's placeholder, which shows
     * whenever the text is empty, so it would otherwise fill every empty or new chapter as if it were the chapter's
     * text.
     */
    void hideWelcome() {

        area.setPlaceholder(null);
        area.setMouseTransparent(false);
        area.setFocusTraversable(true);
        page.getChildren().remove(welcomeLogo);
    }

    // How it looks, and where the author is

    /**
     * Checks the text's spelling with {@code checker} from now on, {@code ownWords} being right too, and the words that
     * look like names too if {@code strict}; with none, the text is not checked and shows no mistakes.
     */
    void checkSpelling(SpellChecker checker, Set<String> ownWords, boolean strict) {

        marks.checkSpelling(checker, ownWords, strict);
    }

    /** Shows the other places of the word the author is at, as Preferences asks, or none. */
    void highlightRepetitions(boolean on) {

        repetitions.setOn(on);
    }

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
     * Gives the text {@code menu} on a right-click, and runs {@code shortcuts} ahead of the text's own key handling.
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

    /** The spelling mistake the right-click menu was opened on, with the word as it is written; none if it was not. */
    Optional<Word> menuMistake() {

        return marks.mistakeAt(menuIndex).map(range -> new Word(area.getText(range.start(), range.end()), range));
    }

    /** Up to five words the author may have meant by {@code word}, the likeliest first. */
    List<String> suggestions(String word) {

        return marks.suggestions(word);
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

        return area.isUndoAvailable();
    }

    boolean canRedo() {

        return area.isRedoAvailable();
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

    /** The welcome, wrapped to the page's width inside its padding, in the placeholder's colour. */
    private Label welcome(String text) {

        Label welcome = new Label(text);
        welcome.getStyleClass().add("manuscript-welcome");
        welcome.setWrapText(true);
        welcome.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> area.getWidth() - area.getPadding().getLeft() - area.getPadding().getRight(),
                area.widthProperty(), area.paddingProperty()));
        return welcome;
    }

    /** Puts the caret at the start, with the first line at the top of the view, and the focus. */
    private void showStart() {

        area.moveTo(0);
        // Following the caret would scroll by a fraction of a line, blurring the text and moving it each time.
        area.showParagraphAtTop(0);
        area.requestFocus();
    }

    private int positionAtCaret() {

        // The caret can briefly lie past the end while the text is being replaced.
        return Math.min(area.getCaretPosition(), area.getLength());
    }

    /** The character a third of the way down the view, where the preview puts the position it follows. */
    private int positionInView() {

        int index = area.hit(area.getPadding().getLeft(), area.getHeight() * FOLLOW_POINT).getInsertionIndex();
        return Math.min(index, area.getLength());
    }

    private static boolean onScrollBar(MouseEvent event) {

        for (Node node = event.getPickResult().getIntersectedNode(); node != null; node = node.getParent())
            if (node instanceof ScrollBar)
                return true;
        return false;
    }
}
