package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.LetterCase;
import it.infog.psiche.ui.common.Icons;
import it.infog.psiche.ui.common.OperatingSystem;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
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
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * The find bar, along the bottom of the editor, hidden until the author asks for it with Find… or Ctrl+F: the text to
 * look for, previous and next, the Aa switch that tells capitals apart, how many places the text has and which one the
 * author is at, and a cross that closes it, as Esc does. It opens with the selection, or the word at the caret, and
 * goes from place to place with Enter and Shift+Enter in its field, F3 and Shift+F3 in the text (⌘G and ⌘⇧G on macOS,
 * which work everywhere too); F3 with it closed opens it with the last text looked for. It stays open as another
 * chapter opens, and looks for the same text there. The search itself is the {@link TextSearch}'s.
 * <p>
 * The arrow at its left shows a second row, as Ctrl+H does (⌘⌥F on macOS, where ⌘H hides the app): the text to put
 * in, Replace, which Enter in its field presses, and Replace all, after which the count tells how many were replaced.
 * Both wait while the text cannot be edited. Undo and Redo in the bar act on the text, not on its fields, so a
 * replacement made by mistake is taken back at once, the text to put in kept.
 */
final class FindBar {

    static final KeyCombination FIND = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final List<KeyCombination> NEXT = List.of(new KeyCodeCombination(KeyCode.F3),
            new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    private static final List<KeyCombination> PREVIOUS = List.of(
            new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN),
            new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    // ⌘H hides the app on macOS, whose own way is ⌘⌥F; elsewhere Ctrl+Alt+F is a footnote's, as in Word.
    private static final List<KeyCombination> REPLACE = List.of(OperatingSystem.isMac()
            ? new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN)
            : new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN));
    private static final KeyCombination CLOSE = new KeyCodeCombination(KeyCode.ESCAPE);
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    // As the text itself redoes.
    private static final List<KeyCombination> REDO = List.of(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
    // The field's red border while the text is nowhere in the chapter.
    private static final PseudoClass NO_MATCHES = PseudoClass.getPseudoClass("no-matches");

    private final Manuscript manuscript;
    private final TextSearch search;
    private final FindField field = new FindField();
    private final FindField replacement = new FindField();
    private final Label count = new Label();
    private final ToggleButton matchCase = new ToggleButton("Aa");
    private final ToggleButton replacing = new ToggleButton();
    private final HBox replaceRow;
    private final VBox bar;
    // True if the author last went back, so going round the chapter came from its start.
    private boolean backwards;
    // What the count says after Replace all, until the author searches again; null meanwhile.
    private String replacedNote;

    FindBar(Manuscript manuscript) {

        this.manuscript = manuscript;
        search = manuscript.search();
        search.setOnChanged(this::showCount);
        // Any change after Replace all, as its Undo, makes the count tell the places again.
        manuscript.textProperty().addListener((obs, old, text) -> replacedNote = null);

        //the text looked for
        field.getStyleClass().add("find-field");
        field.textProperty().addListener((obs, old, text) -> find());
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> enterKey(event, () -> step(!event.isShiftDown())));

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

        //the arrow that shows the replace row, and the row, its label under Find's
        replacing.setGraphic(icon("next"));
        decorate(replacing, "find-replacing", "Replace (" + REPLACE.getFirst().getDisplayText() + ")");
        Region indent = new Region();
        indent.minWidthProperty().bind(replacing.widthProperty());
        replacement.getStyleClass().add("find-field");
        replacement.addEventFilter(KeyEvent.KEY_PRESSED, event -> enterKey(event, this::replace));
        Button replace = textButton("Replace", this::replace);
        Button replaceAll = textButton("Replace all", this::replaceAll);
        replaceRow = new HBox(indent, label("Replace"), replacement, replace, replaceAll);
        replaceRow.getStyleClass().add("find-row");
        replacing.selectedProperty().addListener((obs, old, shown) -> show(replaceRow, shown));
        show(replaceRow, false);

        HBox findRow = new HBox(replacing, label("Find"), field, previous, next, matchCase, count, space, close);
        findRow.getStyleClass().add("find-row");
        bar = new VBox(findRow, replaceRow);
        bar.getStyleClass().add("find-bar");
        bar.addEventFilter(KeyEvent.KEY_PRESSED, this::barKey);
        show(bar, false);

        // Before the text sees them, as Esc, F3 and Ctrl+H mean nothing to it.
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
        show(bar, false);
    }

    /**
     * Ctrl+H: opens the bar as Find… does, with the replace row, the focus in the replacement once there is a text to
     * look for.
     */
    private void openReplacing() {

        if (!bar.isVisible())
            open();
        replacing.setSelected(true);
        (field.getText().isEmpty() ? field : replacement).requestFocus();
    }

    /** The cross and Esc: hides the bar and gives the text the focus back, the place gone to still selected. */
    private void close() {

        hide();
        manuscript.focus();
    }

    private void next() {

        replacedNote = null;
        backwards = false;
        search.next();
    }

    private void previous() {

        replacedNote = null;
        backwards = true;
        search.previous();
    }

    private void step(boolean forward) {

        if (forward)
            next();
        else
            previous();
    }

    private void replace() {

        if (!manuscript.isEditable())
            return;

        replacedNote = null;
        backwards = false;
        search.replace(replacement.sought());
    }

    private void replaceAll() {

        if (!manuscript.isEditable())
            return;

        int replaced = search.replaceAll(replacement.sought());
        replacedNote = "Replaced " + replaced;
        showCount();
    }

    /** Runs {@code edit} on the text, unless it cannot be edited now, as while the book is exported. */
    private void editText(Runnable edit) {

        if (manuscript.isEditable())
            edit.run();
    }

    /** Opens the bar, if closed, with {@code wanted} in the field, and goes to its first place from {@code origin}. */
    private void openWith(String wanted, int origin) {

        show(bar, true);
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

        replacedNote = null;
        backwards = false;
        search.find(field.sought(), matchCase.isSelected() ? LetterCase.MATCHED : LetterCase.IGNORED);
    }

    /** Enter, without Ctrl, which types a line break, in one of the bar's fields: runs {@code action}. */
    private static void enterKey(KeyEvent event, Runnable action) {

        if (event.getCode() != KeyCode.ENTER || event.isShortcutDown())
            return;

        action.run();
        event.consume();
    }

    /** Esc, Ctrl+H, and Undo and Redo for the text, anywhere in the bar. */
    private void barKey(KeyEvent event) {

        if (CLOSE.match(event))
            close();
        else if (REPLACE.stream().anyMatch(key -> key.match(event)))
            openReplacing();
        else if (UNDO.match(event))
            editText(manuscript::undo);
        else if (REDO.stream().anyMatch(key -> key.match(event)))
            editText(manuscript::redo);
        else
            return;

        event.consume();
    }

    /** F3, Shift+F3, ⌘G and ⌘⇧G, Ctrl+H, and Esc while the bar shows, in the text. */
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
        } else if (REPLACE.stream().anyMatch(key -> key.match(event))) {
            event.consume();
            openReplacing();
        } else if (CLOSE.match(event) && bar.isVisible()) {
            close();
            event.consume();
        }
    }

    /**
     * Tells which place the author is at among how many, or that there are none, as the search changes; after Replace
     * all, how many were replaced.
     */
    private void showCount() {

        boolean none = replacedNote == null && !field.getText().isEmpty() && search.count() == 0;
        field.pseudoClassStateChanged(NO_MATCHES, none);
        if (replacedNote != null)
            count.setText(replacedNote);
        else if (field.getText().isEmpty())
            count.setText("");
        else if (none)
            count.setText("No matches");
        else if (search.index() < 0)
            count.setText(search.count() + " found");
        else
            count.setText("%d of %d%s".formatted(search.index() + 1, search.count(),
                    !search.wrapped() ? "" : backwards ? " · from the bottom" : " · from the top"));
    }

    private static void show(Region region, boolean shown) {

        region.setVisible(shown);
        region.setManaged(shown);
    }

    private static Label label(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("find-label");
        return label;
    }

    /** A button that edits the text: it waits while the text cannot be edited. */
    private Button textButton(String text, Runnable action) {

        Button button = new Button(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        button.disableProperty().bind(Bindings.not(manuscript.editableProperty()));
        return button;
    }

    private static Button iconButton(String icon, String tip, Runnable action) {

        Button button = new Button();
        button.setGraphic(icon(icon));
        decorate(button, "find-bar-button", tip);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Node icon(String name) {

        Node shape = Icons.shape(name);
        shape.getStyleClass().add("find-bar-icon");
        return shape;
    }

    private static void decorate(ButtonBase button, String style, String tip) {

        button.getStyleClass().add(style);
        button.setTooltip(new Tooltip(tip));
        button.setFocusTraversable(false);
    }
}
