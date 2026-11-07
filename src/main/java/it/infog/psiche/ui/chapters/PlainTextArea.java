package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.TextBlocks;
import it.infog.psiche.markdown.model.TextRange;
import javafx.scene.control.IndexRange;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyledDocument;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * The text, cut, copied and pasted as plain text, from the menu and the keys alike. RichTextFX's own copy also
 * puts its styled text on the clipboard, which JavaFX hands back in a form its paste cannot read; and here the
 * styles come from the text itself, so plain text is all a copy needs. Pasted line breaks become the editor's.
 * <p>
 * It can keep blocks, as Default mode keeps links and image lines: the author's edits go as {@link TextBlocks} says,
 * refused inside a block and widened over part of one, and a caret with no selection that a key or a click puts
 * inside one goes to its edge. Every edit ends in {@link #replace}, but moving the selection by dragging it; the app's
 * own edits, and Undo and Redo, go through {@link #freely}.
 */
final class PlainTextArea extends StyleClassedTextArea {

    private Supplier<List<TextRange>> blocks = List::of;
    // True while an edit of the app's own runs, which no block refuses.
    private boolean free;
    // Where the caret was before the key being handled, to tell which way it moved.
    private int caretBeforeKey;

    PlainTextArea() {

        addEventFilter(KeyEvent.KEY_PRESSED, event -> caretBeforeKey = getCaretPosition());
        // A double-click selects the word at the caret, which the first click's release took to a block's edge: back
        // under the pointer first, so a word of a link's text can be selected to copy.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() > 1)
                moveTo(hit(event.getX(), event.getY()).getInsertionIndex());
        });
        // Handlers, which run after the area's own have moved the caret.
        addEventHandler(KeyEvent.KEY_PRESSED, event -> snapCaret(caret ->
                TextBlocks.caret(blocks.get(), caret, caret > caretBeforeKey)));
        // On release, so a drag that starts inside a block selects from there.
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1)
                snapCaret(caret -> TextBlocks.nearestEdge(blocks.get(), caret));
        });
    }

    /** Keeps the parts {@code blocks} gives, in order, as blocks; with none, the text is free to edit anywhere. */
    void setBlocks(Supplier<List<TextRange>> blocks) {

        this.blocks = blocks;
    }

    /** Runs {@code edit}, an edit of the app's own, which blocks do not refuse or widen. */
    void freely(Runnable edit) {

        free = true;
        try {
            edit.run();
        } finally {
            free = false;
        }
    }

    @Override
    public void replace(int start, int end,
                        StyledDocument<Collection<String>, String, Collection<String>> replacement) {

        if (free) {
            super.replace(start, end, replacement);
            return;
        }

        allowed(start, end).ifPresent(range -> super.replace(range.start(), range.end(), replacement));
    }

    /** Moves the selection, dragged to {@code position}, unless that would cut into a block or drop it inside one. */
    @Override
    public void moveSelectedText(int position) {

        IndexRange selection = getSelection();
        boolean whole = allowed(selection.getStart(), selection.getEnd())
                .filter(range -> range.start() == selection.getStart() && range.end() == selection.getEnd())
                .isPresent();
        if (free || whole && allowed(position, position).isPresent()) {
            super.moveSelectedText(position);
            return;
        }

        moveTo(TextBlocks.nearestEdge(blocks.get(), position));
    }

    @Override
    public void undo() {

        freely(super::undo);
    }

    @Override
    public void redo() {

        freely(super::redo);
    }

    /** Cuts the selection, widened first over the blocks it covers part of, so the clipboard has what is removed. */
    @Override
    public void cut() {

        IndexRange selection = getSelection();
        allowed(selection.getStart(), selection.getEnd()).ifPresent(range -> selectRange(range.start(), range.end()));
        copy();
        replaceSelection("");
    }

    @Override
    public void copy() {

        if (getSelection().getLength() == 0)
            return;

        ClipboardContent content = new ClipboardContent();
        content.putString(getSelectedText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void paste() {

        String text = Clipboard.getSystemClipboard().getString();
        if (text != null)
            replaceSelection(text.replace("\r\n", "\n").replace('\r', '\n'));
    }

    private Optional<TextRange> allowed(int start, int end) {

        return TextBlocks.edit(blocks.get(), start, end);
    }

    /** Moves a caret with no selection to where {@code snapped} says, if it says elsewhere. */
    private void snapCaret(IntUnaryOperator snapped) {

        if (getSelection().getLength() > 0)
            return;

        int caret = getCaretPosition();
        int snappedCaret = snapped.applyAsInt(caret);
        if (snappedCaret != caret)
            moveTo(snappedCaret);
    }
}
