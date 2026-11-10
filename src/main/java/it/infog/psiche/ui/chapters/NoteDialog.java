package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.NumberedNote;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;

import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asks for a footnote's note, in Default mode, where a note is changed only here: its text as the author writes it,
 * paragraphs between blank lines, without the label or the spaces that set its lines in, which Psiche writes. A new
 * footnote can instead lead to a note the chapter has, picked by the number readers see it by, its text shown as it
 * is. Insert needs a note picked or some text; saving a note left empty removes it, once the author confirms.
 */
final class NoteDialog {

    private static final String WHERE_IT_GOES = "The note stays here in your text. Readers find it numbered at the end "
            + "of the chapter, or at the foot of the page in PDF, ODT and DOCX.";
    private static final int ROWS = 8;

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ButtonType confirm;
    private final TextArea text = new TextArea();
    // A new note, or one the chapter has; shown only for a new footnote in a chapter with notes.
    private final ComboBox<Optional<NumberedNote>> choice;
    // What the author wrote for a new note, kept while they look at the chapter's notes.
    private String draft = "";

    /**
     * @param note     the text of the note being modified; empty for a new footnote
     * @param existing the chapter's notes a new footnote can lead to instead of a note of its own; none to offer none
     */
    NoteDialog(Window owner, Optional<String> note, List<NumberedNote> existing) {

        confirm = new ButtonType(note.isPresent() ? "Save" : "Insert", ButtonData.OK_DONE);
        List<Optional<NumberedNote>> choices = new ArrayList<>(List.of(Optional.empty()));
        existing.forEach(found -> choices.add(Optional.of(found)));
        choice = Controls.choices(choices, NoteDialog::choiceLabel);

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText(note.isPresent() ? "Modify note" : "Insert note");
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        dialog.getDialogPane().lookupButton(confirm).getStyleClass().add("primary");
        dialog.getDialogPane().setContent(content(!existing.isEmpty()));

        text.setText(note.orElse(""));
        choice.setValue(Optional.empty());
        choice.valueProperty().addListener((obs, old, chosen) -> show(old, chosen));
        if (note.isEmpty())
            dialog.getDialogPane().lookupButton(confirm).disableProperty().bind(Bindings.createBooleanBinding(
                    () -> choice.getValue().isEmpty() && text.getText().isBlank(), text.textProperty(),
                    choice.valueProperty()));
        dialog.setOnShown(event -> text.requestFocus());
    }

    /** Shows the dialog until the author confirms or cancels it; true if they confirmed. */
    boolean showAndWait() {

        return dialog.showAndWait().filter(confirm::equals).isPresent();
    }

    /** The note's text as the author left it; blank if they emptied it. */
    String text() {

        return text.getText();
    }

    /** The chapter's note the new footnote leads to; empty for a note of its own, with {@link #text()}. */
    Optional<NumberedNote> chosen() {

        return choice.getValue();
    }

    private VBox content(boolean offerNotes) {

        //which note, for a new footnote in a chapter with notes
        GridPane form = new GridPane();
        form.getStyleClass().add("book-form");
        form.addRow(0, new Label("Note"), choice);
        Controls.setShown(form, offerNotes);

        //its text, and where readers find it
        text.setWrapText(true);
        text.setPrefRowCount(ROWS);
        Label hint = Controls.hint(WHERE_IT_GOES);
        hint.setWrapText(true);
        // Its lines whole, never cut to fit the dialog's first height.
        hint.setMinHeight(Region.USE_PREF_SIZE);

        VBox content = new VBox(form, text, hint);
        VBox.setVgrow(text, Priority.ALWAYS);
        content.getStyleClass().add("note-dialog-content");
        return content;
    }

    /** Shows {@code chosen}'s text, which only its own dialog changes, or the new note's draft again. */
    private void show(Optional<NumberedNote> old, Optional<NumberedNote> chosen) {

        if (old.isEmpty())
            draft = text.getText();

        text.setText(chosen.map(NumberedNote::text).orElse(draft));
        text.setEditable(chosen.isEmpty());
    }

    private static String choiceLabel(Optional<NumberedNote> note) {

        return note.map(found -> found.number() + " · " + found.summary()).orElse("New note");
    }
}
