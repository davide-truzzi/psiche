package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.LetterCase;
import it.infog.psiche.ui.common.Icons;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.List;
import java.util.Optional;

/**
 * The find bar, along the bottom of the editor, hidden until the author asks for it with Find… or Ctrl+F: the text to
 * look for, previous and next, the Aa switch that tells capitals apart, how many places the text has and which one the
 * author is at, and a cross that closes it, as Esc does. It opens with the selection, or the word at the caret, and
 * goes from place to place with Enter and Shift+Enter in its field, F3 and Shift+F3 in the text (⌘G and ⌘⇧G on macOS,
 * which work everywhere too); F3 with it closed opens it with the last text looked for. It stays open as another
 * chapter opens, and looks for the same text there. The search itself is the {@link TextSearch}'s.
 */
final class FindBar {

    static final KeyCombination FIND = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final List<KeyCombination> NEXT = List.of(new KeyCodeCombination(KeyCode.F3),
            new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    private static final List<KeyCombination> PREVIOUS = List.of(
            new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN),
            new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    private static final KeyCombination CLOSE = new KeyCodeCombination(KeyCode.ESCAPE);
    // The field's red border while the text is nowhere in the chapter.
    private static final PseudoClass NO_MATCHES = PseudoClass.getPseudoClass("no-matches");

    private final Manuscript manuscript;
    private final TextSearch search;
    private final FindField field = new FindField();
    private final Label count = new Label();
    private final ToggleButton matchCase = new ToggleButton("Aa");
    private final HBox bar;
    // True if the author last went back, so going round the chapter came from its start.
    private boolean backwards;

    FindBar(Manuscript manuscript) {

        this.manuscript = manuscript;
        search = manuscript.search();
        search.setOnChanged(this::showCount);

        //the text looked for
        Label label = new Label("Find");
        label.setMinWidth(Region.USE_PREF_SIZE);
        field.getStyleClass().add("find-field");
        field.textProperty().addListener((obs, old, text) -> find());
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::fieldKey);


        //previous, next, capitals and close
        Button previous = iconButton("previous", "Previous (Shift+F3)", this::previous);
        Button next = iconButton("next", "Next (F3)", this::next);
        matchCase.getStyleClass().add("find-case");
        matchCase.setTooltip(new Tooltip("Case sensitive"));
        matchCase.setFocusTraversable(false);
        matchCase.selectedProperty().addListener((obs, old, selected) -> find());
        Button close = iconButton("remove", "Close (Esc)", this::close);

        //where the author is among the places, after the buttons, so they stay put as it changes
        count.getStyleClass().add("find-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        bar = new HBox(label, field, previous, next, matchCase, count, space, close);
        bar.getStyleClass().add("find-bar");
        show(false);

        // Before the text sees them, as Esc and F3 mean nothing to it.
        manuscript.node().addEventFilter(KeyEvent.KEY_PRESSED, this::editorKey);
    }

    Node node() { return bar; }

    /**
     * Find… and Ctrl+F: opens the bar with the selection, or the word at the caret, or else the last text looked for,
     * the focus in its field; the search starts from the first of them. Only with a chapter open.
     */
    void open() {

        if (!manuscript.isEditable())
            return;

        String text = manuscript.text();
        TextRange selection = new TextRange(manuscript.selection().getStart(), manuscript.selection().getEnd());
        Optional<TextRange> start = selection.start() < selection.end()
                ? Optional.of(selection)
                : Occurrences.wordAt(text, manuscript.caret(), 1);
        openWith(start.map(range -> text.substring(range.start(), range.end())).orElse(field.sought()),
                start.map(TextRange::start).orElse(manuscript.caret()));
        field.requestFocus();
    }

    /** Hides the bar, its marks gone, without moving the focus, as when the book closes. */
    void hide() {

        search.close();
        show(false);
    }

    /** The cross and Esc: hides the bar and gives the text the focus back, the place gone to still selected. */
    private void close() {

        hide();
        manuscript.focus();
    }

    private void next() {

        backwards = false;
        search.next();
    }

    private void previous() {

        backwards = true;
        search.previous();
    }

    /** Opens the bar, if closed, with {@code wanted} in the field, and goes to its first place from {@code origin}. */
    private void openWith(String wanted, int origin) {

        show(true);
        search.open(origin);
        backwards = false;
        if (field.sought().equals(wanted))
            find();
        field.show(wanted);
    }

    /** Looks for the field's text from where the search started, as the author types it. */
    private void find() {

        if (!bar.isVisible())
            return;

        backwards = false;
        search.find(field.sought(), matchCase.isSelected() ? LetterCase.MATCHED : LetterCase.IGNORED);
    }

    /** Enter, Shift+Enter and Esc in the field. */
    private void fieldKey(KeyEvent event) {

        if (event.getCode() == KeyCode.ENTER && !event.isShortcutDown()) {
            step(!event.isShiftDown());
            event.consume();
        } else if (CLOSE.match(event)) {
            close();
            event.consume();
        }
    }

    /** F3, Shift+F3, ⌘G and ⌘⇧G, and Esc while the bar shows, in the text. */
    private void editorKey(KeyEvent event) {

        boolean forward = NEXT.stream().anyMatch(key -> key.match(event));
        boolean back = PREVIOUS.stream().anyMatch(key -> key.match(event));
        if (forward || back) {
            event.consume();
            if (bar.isVisible())
                step(forward);
            else if (!field.sought().isEmpty() && manuscript.isEditable()) {
                // Opening goes to the first place after the caret already; back is the one before.
                openWith(field.sought(), manuscript.caret());
                if (back)
                    previous();
            }
        } else if (CLOSE.match(event) && bar.isVisible()) {
            close();
            event.consume();
        }
    }

    private void step(boolean forward) {

        if (forward)
            next();
        else
            previous();
    }

    /** Tells which place the author is at among how many, or that there are none, as the search changes. */
    private void showCount() {

        boolean none = !field.getText().isEmpty() && search.count() == 0;
        field.pseudoClassStateChanged(NO_MATCHES, none);
        if (field.getText().isEmpty())
            count.setText("");
        else if (none)
            count.setText("No matches");
        else if (search.index() < 0)
            count.setText(search.count() + " found");
        else
            count.setText("%d of %d%s".formatted(search.index() + 1, search.count(),
                    !search.wrapped() ? "" : backwards ? " · from the bottom" : " · from the top"));
    }

    private void show(boolean shown) {

        bar.setVisible(shown);
        bar.setManaged(shown);
    }

    private static Button iconButton(String icon, String tip, Runnable action) {

        SVGPath shape = Icons.shape(icon);
        shape.getStyleClass().add("find-bar-icon");
        Button button = new Button();
        button.setGraphic(shape);
        button.getStyleClass().add("find-bar-button");
        button.setTooltip(new Tooltip(tip));
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        return button;
    }
}
