package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.SceneKeys;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.skin.TextInputControlSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The Go to line bar, along the bottom of the editor like the find bar and drawn as it is, hidden until the author
 * asks for it with Alt+L anywhere in the window (Option+L on macOS), or Go to line… in the text's menu: the line to go
 * to, the caret's to start with and all selected, how many lines the chapter has, and a cross that closes it, as Esc
 * does. The lines are the line numbers', a paragraph each, as in the chapter's file, and the numbers show while the bar
 * does. Enter takes the caret to the start of the line, and closes the bar; a line the chapter does not have turns the
 * field's border red, as text found nowhere does. The bar closes too once the focus leaves it.
 */
final class GoToLineBar {

    static final KeyCombination GO_TO_LINE = new KeyCodeCombination(KeyCode.L, KeyCombination.ALT_DOWN);
    private static final KeyCombination CLOSE = new KeyCodeCombination(KeyCode.ESCAPE);
    // The find field's red border, while the line is not in the chapter.
    private static final PseudoClass NO_SUCH_LINE = PseudoClass.getPseudoClass("no-matches");
    private static final Pattern DIGITS = Pattern.compile("\\d*");
    // More digits than any chapter has lines would not fit an int.
    private static final int MAX_DIGITS = 9;

    private final Manuscript manuscript;
    private final TextField field = new TextField();
    private final Label count = new Label();
    private final HBox bar;

    GoToLineBar(Manuscript manuscript) {

        this.manuscript = manuscript;

        //the line, and how many the chapter has
        field.getStyleClass().addAll("find-field", "go-to-field");
        field.setTextFormatter(new TextFormatter<>(GoToLineBar::digitsOnly));
        field.textProperty().addListener((obs, old, text) -> checkLine());
        field.focusedProperty().addListener((obs, old, focused) -> {
            if (focused)
                return;

            hide();
            // After JavaFX has reset the field's selection, which it does as the focus leaves.
            Platform.runLater(this::stopCaretBlink);
        });
        count.getStyleClass().add("find-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        Label label = new Label("Go to line");
        label.getStyleClass().add("find-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        bar = new HBox(label, field, count, space, FindBar.iconButton("remove", "Close (Esc)", this::close));
        bar.getStyleClass().addAll("find-bar", "find-row");
        bar.addEventFilter(KeyEvent.KEY_PRESSED, this::barKey);
        show(false);

        // Anywhere in the window, as Alt alone shows the numbers.
        SceneKeys.filter(bar, KeyEvent.KEY_PRESSED, event -> {
            if (GO_TO_LINE.match(event)) {
                open();
                event.consume();
            }
        });
    }

    Node node() { return bar; }

    /** Alt+L and Go to line…: opens the bar on the caret's line, the focus in its field. Only with a chapter open. */
    void open() {

        if (!manuscript.isEditable())
            return;

        count.setText("of " + manuscript.lineCount());
        field.setText(String.valueOf(manuscript.caretLine()));
        show(true);
        field.requestFocus();
        field.selectAll();
    }

    /** Hides the bar without moving the focus, as when the book closes. */
    void hide() {

        show(false);
    }

    /** The cross and Esc: hides the bar and gives the text the focus back, the caret where it was. */
    private void close() {

        hide();
        manuscript.focus();
    }

    /** Enter: takes the caret to the start of the line in the field, if the chapter has it, and closes the bar. */
    private void go() {

        line().ifPresent(line -> {
            hide();
            manuscript.reveal(manuscript.lineStart(line));
        });
    }

    /** Esc and Enter, anywhere in the bar. */
    private void barKey(KeyEvent event) {

        if (CLOSE.match(event))
            close();
        else if (event.getCode() == KeyCode.ENTER)
            go();
        else
            return;

        event.consume();
    }

    /** Turns the field's border red while it holds a line the chapter does not have; an empty field is fine. */
    private void checkLine() {

        field.pseudoClassStateChanged(NO_SUCH_LINE, !field.getText().isEmpty() && line().isEmpty());
    }

    /** The line in the field, if the chapter has it. */
    private OptionalInt line() {

        String text = field.getText();
        if (text.isEmpty() || text.length() > MAX_DIGITS)
            return OptionalInt.empty();

        int line = Integer.parseInt(text);
        return line >= 1 && line <= manuscript.lineCount() ? OptionalInt.of(line) : OptionalInt.empty();
    }

    /** Lets only digits into the field: Option+L types a letter on macOS, after the key that opened the bar. */
    private static TextFormatter.Change digitsOnly(TextFormatter.Change change) {

        return DIGITS.matcher(change.getControlNewText()).matches() ? change : null;
    }

    /**
     * Stops the field's caret blinking once the focus has left it. JavaFX restarts the blink whenever the caret moves,
     * with the focus or without, and it moves as the focus leaves: the blink then runs on unseen, and draws the window
     * sixty times a second until the field next has the focus.
     */
    private void stopCaretBlink() {

        if (!field.isFocused() && field.getSkin() instanceof TextInputControlSkin<?> skin)
            skin.setCaretAnimating(false);
    }

    private void show(boolean shown) {

        bar.setVisible(shown);
        bar.setManaged(shown);
        manuscript.askLineNumbers(shown);
    }
}
