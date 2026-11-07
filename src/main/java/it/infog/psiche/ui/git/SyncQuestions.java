package it.infog.psiche.ui.git;

import it.infog.psiche.ui.common.Dialogs;

import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/** What automatic synchronization asks the author: to commit before the book closes. */
final class SyncQuestions {

    private static final ButtonType COMMIT = new ButtonType("Commit", ButtonData.OK_DONE);
    private static final ButtonType CLOSE = new ButtonType("Close without synchronizing", ButtonData.NO);
    private static final String COMMIT_ABOUT = "This book synchronizes automatically, and some of your work is not "
            + "committed yet: describe it, and it is committed and synchronized before the book closes.";

    private SyncQuestions() {
    }

    /**
     * Asks, as a book that synchronizes automatically closes with work not committed, to commit it first.
     *
     * @param message what is typed beside Commit, if anything, to start from
     * @param commit  given the message, commits and synchronizes, then closes the book
     * @param close   closes the book without synchronizing
     * @return false if the author cancels, and nothing is run
     */
    static boolean commitBeforeClosing(Window owner, String message, Consumer<String> commit, Runnable close) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Commit before closing?");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, CLOSE, COMMIT);

        TextField description = new TextField(message);
        description.setPromptText("Describe this revision");
        dialog.getDialogPane().lookupButton(COMMIT).getStyleClass().add("primary");
        dialog.getDialogPane().lookupButton(COMMIT).disableProperty().bind(Bindings.createBooleanBinding(
                () -> description.getText().isBlank(), description.textProperty()));

        Label about = new Label(COMMIT_ABOUT);
        about.setWrapText(true);
        about.setMinHeight(Region.USE_PREF_SIZE);
        about.getStyleClass().add("sync-question-about");
        VBox content = new VBox(about, description);
        content.getStyleClass().add("sync-question-content");
        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> description.requestFocus());

        ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == COMMIT)
            commit.accept(description.getText());
        else if (choice == CLOSE)
            close.run();

        return choice == COMMIT || choice == CLOSE;
    }
}
