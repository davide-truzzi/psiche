package it.infog.psiche.ui.chapters;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * The text, cut, copied and pasted as plain text, from the menu and the keys alike. RichTextFX's own copy also
 * puts its styled text on the clipboard, which JavaFX hands back in a form its paste cannot read; and here the
 * styles come from the text itself, so plain text is all a copy needs. Pasted line breaks become the editor's.
 */
final class PlainTextArea extends StyleClassedTextArea {

    @Override
    public void cut() {

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
}
