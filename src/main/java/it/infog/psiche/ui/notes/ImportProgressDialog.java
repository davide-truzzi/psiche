package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.Dialogs;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import static it.infog.psiche.ui.common.Controls.setShown;

/**
 * "Importing notes", over the window while an import runs: what it is doing, the backup first, and how far it has come.
 * It keeps the author's hands off the notes until it is done, so nothing typed or opened meanwhile can clash with the
 * import; only the import closes it.
 */
final class ImportProgressDialog {

    private final Dialog<Void> dialog = new Dialog<>();
    private final Label step = new Label();
    private final ProgressBar bar = new ProgressBar(0);
    private boolean done;

    ImportProgressDialog(Window owner) {

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Importing notes");

        // A dialog closes only if it has a button; this one's stays hidden, and the window's close button is ignored.
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        setShown(cancel, false);
        dialog.setOnCloseRequest(event -> {
            if (!done)
                event.consume();
        });

        bar.setMaxWidth(Double.MAX_VALUE);
        VBox content = new VBox(step, bar);
        content.getStyleClass().add("import-progress");
        dialog.getDialogPane().setContent(content);
    }

    /** Shows the dialog, and returns at once: the import runs meanwhile. */
    void show() {

        dialog.show();
    }

    /**
     * Shows {@code text} as what the import is doing now, with the bar back at the start, and returns what hears how
     * far it has come. Both may be called from any thread.
     */
    NoteStore.CopyProgress step(String text) {

        Platform.runLater(() -> {
            step.setText(text);
            bar.setProgress(0);
        });
        return NotesTransfer.percent(percent -> bar.setProgress(percent / 100.0));
    }

    void close() {

        done = true;
        dialog.close();
    }
}
