package it.infog.psiche.ui.export;

import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.git.model.BookCommit;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.DateFormats;
import it.infog.psiche.ui.common.Dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * Every revision of the book, newest first, to choose one and the format to export the book in as it was then: for a
 * jury that asks to look closely at one point of the writing history.
 */
final class RevisionChooser {

    private static final ButtonType EXPORT = new ButtonType("Export", ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);

    private RevisionChooser() {
    }

    /**
     * Lists {@code commits} and waits for the author.
     *
     * @return the revision and format chosen; empty if the author cancelled
     */
    static Optional<Choice> show(Window owner, List<BookCommit> commits) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setResizable(true);
        dialog.setHeaderText("Export the book at a revision");
        dialog.getDialogPane().getStyleClass().add("revision-chooser");
        dialog.getDialogPane().getButtonTypes().setAll(CANCEL, EXPORT);

        //list
        ListView<BookCommit> list = new ListView<>();
        list.getItems().setAll(commits);
        list.setCellFactory(view -> new CommitCell());
        list.getStyleClass().addAll("history", "revision-list");
        VBox.setVgrow(list, Priority.ALWAYS);

        //format
        ComboBox<ExportFormat> format = Controls.choices(Exports.BOOK_FORMATS, ExportFormat::label);
        format.getSelectionModel().selectFirst();
        Label formatLabel = new Label("Format");
        formatLabel.setLabelFor(format);
        // A form, as the app's other dialogs lay out their fields, so the box looks as theirs do.
        GridPane formatRow = new GridPane();
        formatRow.addRow(0, formatLabel, format);
        formatRow.getStyleClass().addAll("book-form", "revision-format");

        //export the selected revision, also on double-click or Enter, which the list keeps from the default button
        Button export = (Button) dialog.getDialogPane().lookupButton(EXPORT);
        export.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !export.isDisable())
                export.fire();
        });
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !export.isDisable())
                export.fire();
        });

        Label hint = new Label("The book is exported with its chapters, images and properties as they were in that "
                + "revision. The revision's day and first words are added to the file's name.");
        hint.getStyleClass().add("history-hint");
        hint.setWrapText(true);
        VBox content = new VBox(list, formatRow, hint);
        content.getStyleClass().add("revision-choice");
        dialog.getDialogPane().setContent(content);

        return dialog.showAndWait()
                .filter(EXPORT::equals)
                .map(button -> new Choice(list.getSelectionModel().getSelectedItem(), format.getValue()));
    }

    /** The revision to export the book at, and the format to export it in. */
    record Choice(BookCommit commit,
                  ExportFormat format) {
    }

    private static final class CommitCell extends ListCell<BookCommit> {

        private final Label message = new Label();
        private final Label details = new Label();
        private final VBox content = new VBox(message, details);

        CommitCell() {

            message.getStyleClass().add("revision-message");
            details.getStyleClass().add("revision-details");
        }

        @Override
        protected void updateItem(BookCommit commit, boolean empty) {

            super.updateItem(commit, empty);
            setText(null);
            if (empty || commit == null) {
                setGraphic(null);
                return;
            }

            message.setText(commit.message());
            details.setText("%s · %s".formatted(DateFormats.DAY_AND_TIME.format(commit.time()), commit.shortId()));
            setGraphic(content);
        }
    }
}
