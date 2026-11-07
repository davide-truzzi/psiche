package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.MarkdownFormatter;
import it.infog.psiche.markdown.model.TextEdit;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * The manuscript's right-click menu: Markdown formatting for the selection, each item also run by its shortcut, then
 * Insert image… and the {@link LinkActions}, above the editing commands the text's own menu has, which this menu
 * replaces. Each formatting is one change, which a single Undo reverts.
 */
final class FormattingMenu {

    // Formatting shortcuts, as in common word processors and Markdown editors; Shortcut is Ctrl, or ⌘ on macOS.
    private static final KeyCombination BOLD = new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination ITALIC = new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination NUMBERED_LIST =
            new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination BULLETED_LIST =
            new KeyCodeCombination(KeyCode.DIGIT8, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination QUOTE =
            new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination COMMENT =
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination CLEAR_FORMATTING =
            new KeyCodeCombination(KeyCode.BACK_SLASH, KeyCombination.SHORTCUT_DOWN);
    // Heading 1 to 4 are Shortcut and the digit of their level.
    private static final List<KeyCode> HEADING_KEYS = List.of(KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3,
            KeyCode.DIGIT4);

    private final Manuscript manuscript;
    private final Map<KeyCombination, Runnable> shortcuts = new LinkedHashMap<>();

    private FormattingMenu(Manuscript manuscript) {

        this.manuscript = manuscript;
    }

    /**
     * Gives {@code manuscript} the menu on a right-click, and its shortcuts while it has the focus.
     *
     * @param insertImage asks for an image and writes it into the chapter, for Insert image…
     * @param links       Insert, Modify and Remove link, whose items follow Insert image…
     *
     * @return the menu, for other parts of the editor to add to
     */
    static ContextMenu attach(Manuscript manuscript, Runnable insertImage, LinkActions links) {

        FormattingMenu formatting = new FormattingMenu(manuscript);
        ContextMenu menu = formatting.menu(insertImage, links);
        manuscript.setMenu(menu, formatting.shortcuts);
        return menu;
    }

    private ContextMenu menu(Runnable insertImage, LinkActions links) {

        //formatting
        MenuItem bold = formatItem("Bold", BOLD, MarkdownFormatter::bold);
        MenuItem italic = formatItem("Italic", ITALIC, MarkdownFormatter::italic);
        Menu heading = new Menu("Heading");
        for (int level = 1; level <= HEADING_KEYS.size(); level++)
            heading.getItems().add(formatItem("Heading " + level,
                    new KeyCodeCombination(HEADING_KEYS.get(level - 1), KeyCombination.SHORTCUT_DOWN),
                    headingFormat(level)));
        MenuItem bulletedList = formatItem("Bulleted list", BULLETED_LIST, MarkdownFormatter::bulletedList);
        MenuItem numberedList = formatItem("Numbered list", NUMBERED_LIST, MarkdownFormatter::numberedList);
        MenuItem quote = formatItem("Quote", QUOTE, MarkdownFormatter::quote);
        MenuItem comment = formatItem("Comment", COMMENT, MarkdownFormatter::comment);
        MenuItem clear = formatItem("Clear formatting", CLEAR_FORMATTING, MarkdownFormatter::clearFormatting);

        //inserting
        MenuItem image = menuItem("Insert image…", insertImage);
        shortcuts.put(LinkActions.SHORTCUT, links::edit);

        //editing
        MenuItem undo = menuItem("Undo", manuscript::undo);
        MenuItem redo = menuItem("Redo", manuscript::redo);
        MenuItem cut = menuItem("Cut", manuscript::cut);
        MenuItem copy = menuItem("Copy", manuscript::copy);
        MenuItem paste = menuItem("Paste", manuscript::paste);
        MenuItem delete = menuItem("Delete", manuscript::deleteSelection);
        MenuItem selectAll = menuItem("Select All", manuscript::selectAll);

        ContextMenu menu = new ContextMenu(bold, italic, heading, bulletedList, numberedList, quote, comment, clear,
                new SeparatorMenuItem(), image);
        menu.getItems().addAll(links.items());
        menu.getItems().addAll(new SeparatorMenuItem(), undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete,
                new SeparatorMenuItem(), selectAll);

        // Enable what applies to the text and selection as they are when the menu opens.
        menu.setOnShowing(event -> {
            boolean editable = manuscript.isEditable();
            IndexRange selection = manuscript.selection();
            boolean selected = selection.getLength() > 0;
            boolean words = !manuscript.selectedText().isBlank();
            boolean oneLine = MarkdownFormatter.spansOneLine(manuscript.text(), selection.getStart(),
                    selection.getEnd());

            bold.setDisable(!editable || !words);
            italic.setDisable(!editable || !words);
            heading.setDisable(!editable || !oneLine);
            List.of(bulletedList, numberedList, quote, comment, clear, image)
                    .forEach(item -> item.setDisable(!editable));
            links.update();

            undo.setDisable(!editable || !manuscript.canUndo());
            redo.setDisable(!editable || !manuscript.canRedo());
            cut.setDisable(!editable || !selected);
            copy.setDisable(!selected);
            paste.setDisable(!editable || !Clipboard.getSystemClipboard().hasString());
            delete.setDisable(!editable || !selected);
            selectAll.setDisable(manuscript.length() == 0);
        });

        return menu;
    }

    /** A menu item that formats the selection, also run by {@code shortcut} while the manuscript has the focus. */
    private MenuItem formatItem(String text, KeyCombination shortcut, Formatting formatting) {

        MenuItem item = menuItem(text, () -> format(formatting));
        item.setAccelerator(shortcut);
        shortcuts.put(shortcut, () -> format(formatting));

        return item;
    }

    /** A heading of {@code level}, which a selection over several lines does not become. */
    private static Formatting headingFormat(int level) {

        return (text, start, end) -> MarkdownFormatter.spansOneLine(text, start, end)
                ? MarkdownFormatter.heading(text, start, end, level)
                : TextEdit.selecting(start, end, text.substring(start, end));
    }

    /** Formats the selection as one change, which a single Undo reverts, and selects the result. */
    private void format(Formatting formatting) {

        if (!manuscript.isEditable())
            return;

        String text = manuscript.text();
        IndexRange selection = manuscript.selection();
        TextEdit edit = formatting.apply(text, selection.getStart(), selection.getEnd());

        // Replacing text with itself would still add a step to Undo.
        if (!edit.replacement().equals(text.substring(edit.start(), edit.end())))
            manuscript.replace(edit.start(), edit.end(), edit.replacement());
        manuscript.select(edit.selectionStart(), edit.selectionEnd());
    }

    /** Turns the text and selection into the edit that formats it, as the {@link MarkdownFormatter} methods do. */
    @FunctionalInterface
    private interface Formatting {
        TextEdit apply(String text, int start, int end);
    }
}
