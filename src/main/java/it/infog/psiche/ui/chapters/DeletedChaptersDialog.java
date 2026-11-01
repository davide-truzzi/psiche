package it.infog.psiche.ui.chapters;

import it.infog.psiche.git.model.DeletedChapter;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.git.HistoryPane;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * The chapters deleted from the book, most recently deleted first, any of which the author can restore with the text
 * it had when it was deleted.
 */
public final class DeletedChaptersDialog {

    private static final ButtonType RESTORE = new ButtonType("Restore", ButtonData.OK_DONE);
    private static final ButtonType CLOSE = new ButtonType("Close", ButtonData.CANCEL_CLOSE);

    private DeletedChaptersDialog() {
    }

    /**
     * Lists {@code deleted} and waits for the author.
     *
     * @return the chapter the author chose to restore; empty if they closed the dialog
     */
    static Optional<DeletedChapter> show(Window owner, List<DeletedChapter> deleted) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Deleted chapters");
        dialog.getDialogPane().getStyleClass().add("deleted-chapters-dialog");
        dialog.getDialogPane().getButtonTypes().setAll(CLOSE, RESTORE);

        //list
        ListView<DeletedChapter> list = new ListView<>();
        list.getItems().setAll(deleted);
        list.setPlaceholder(new Label("No chapter has been deleted."));
        list.setCellFactory(view -> new DeletedChapterCell());
        list.getStyleClass().add("history");

        //restore the selected chapter, also on double-click or Enter, which the list keeps from the default button
        Button restore = (Button) dialog.getDialogPane().lookupButton(RESTORE);
        restore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !restore.isDisable())
                restore.fire();
        });
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !restore.isDisable())
                restore.fire();
        });

        Label hint = new Label("A restored chapter comes back with the text it had when it was deleted.");
        hint.getStyleClass().add("history-hint");
        VBox content = new VBox(list, hint);
        content.getStyleClass().add("deleted-chapters");
        dialog.getDialogPane().setContent(content);

        return dialog.showAndWait()
                .filter(RESTORE::equals)
                .map(button -> list.getSelectionModel().getSelectedItem());
    }

    private static final class DeletedChapterCell extends ListCell<DeletedChapter> {

        private final Label title = new Label();
        private final Label details = new Label();
        private final VBox content = new VBox(title, details);

        DeletedChapterCell() {
            title.getStyleClass().add("revision-message");
            details.getStyleClass().add("revision-details");
        }

        @Override
        protected void updateItem(DeletedChapter chapter, boolean empty) {

            super.updateItem(chapter, empty);
            setText(null);
            if (empty || chapter == null) {
                setGraphic(null);
                return;
            }

            title.setText(HistoryPane.chapterTitle(chapter.path()));
            details.setText("deleted %s · %s".formatted(HistoryPane.COMMIT_TIME.format(chapter.deletedAt()),
                    chapter.deletedInId()));
            setGraphic(content);
        }
    }
}
