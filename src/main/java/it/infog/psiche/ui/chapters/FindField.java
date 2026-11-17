package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.FieldCaret;

import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

/**
 * The find bar's field, where a line break is a character like the others: it shows as ↵, the Enter key's sign (see
 * {@link LineBreakSigns}), is typed with Ctrl+Enter (⌘+Enter on macOS), as Enter alone goes to the next place, and
 * stays in text pasted.
 */
final class FindField extends TextField {

    private static final KeyCombination TYPE_LINE_BREAK =
            new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);

    FindField() {

        FieldCaret.stopsWithFocus(this);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (TYPE_LINE_BREAK.match(event)) {
                replaceSelection(LineBreakSigns.SIGN);
                event.consume();
            }
        });
    }

    /** What the author looks for, each line break as {@code \n}. */
    String sought() {

        return LineBreakSigns.text(getText());
    }

    /** Shows {@code text}, its line breaks as ↵, all selected, so typing replaces it. */
    void show(String text) {

        setText(LineBreakSigns.shown(text));
        selectAll();
    }

    @Override
    public void paste() {

        String text = Clipboard.getSystemClipboard().getString();
        if (text != null)
            replaceSelection(LineBreakSigns.shown(text));
    }
}
