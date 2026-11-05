package it.infog.psiche.ui.common;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Dialogs owned by the main window and styled like it. */
public final class Dialogs {

    private static final String APP_TITLE = "Psiche Writer";

    private Dialogs() {
    }

    /**
     * Asks for a title; empty if the author cancels. The button that accepts it, labelled {@code action}, stays
     * disabled while the title is blank.
     */
    public static Optional<String> askTitle(Window owner, String header, String initialText, String action) {

        TextInputDialog dialog = new TextInputDialog(initialText);
        ownAndStyle(dialog, owner);
        dialog.setHeaderText(header);
        dialog.setContentText("Title");
        Button create = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        create.setText(action);
        create.disableProperty().bind(Bindings.createBooleanBinding(
                () -> dialog.getEditor().getText().isBlank(), dialog.getEditor().textProperty()));
        // Put the caret after the suggested text instead of selecting it, so typing appends to it.
        dialog.setOnShown(event -> Platform.runLater(dialog.getEditor()::end));
        return dialog.showAndWait();
    }

    /** Asks the author to pick one of {@code choices}; empty if they close the dialog without picking. */
    public static Optional<ButtonType> choose(Window owner, String header, String content, ButtonType... choices) {

        return confirmationAlert(owner, header, content, choices).showAndWait();
    }

    /**
     * Asks which of {@code chapters}, by name, something goes to, with {@code suggested} picked at first; the button
     * that accepts is labelled {@code action}. Empty if the author cancels.
     */
    public static Optional<String> chooseChapter(Window owner, String header, List<String> chapters, String suggested,
                                                 String action) {

        ChoiceDialog<String> choice = new ChoiceDialog<>(suggested, chapters);
        ownAndStyle(choice, owner);
        choice.setHeaderText(header);
        choice.setContentText("Chapter");
        choice.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, new ButtonType(action, ButtonData.OK_DONE));
        return choice.showAndWait();
    }

    /** Tells the author something and waits until they close the alert. */
    public static void inform(Window owner, String header, String content) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        ownAndStyle(alert, owner);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    /** Shows the error's message; returns at once instead of waiting for the author to close the alert. */
    public static void showError(Window owner, String header, Throwable error) {

        errorAlert(owner, header, error).show();
    }

    /**
     * @param initialDirectory where the chooser starts, or {@code null} for the platform default
     * @return empty if the author cancels
     */
    public static Optional<Path> chooseDirectory(Window owner, String title, Path initialDirectory) {

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initialDirectory != null)
            chooser.setInitialDirectory(initialDirectory.toFile());

        return Optional.ofNullable(chooser.showDialog(owner)).map(File::toPath);
    }

    /**
     * Asks where to save a file, starting in {@code initialDirectory} with {@code initialName}; empty if the author
     * cancels. The extension is added when the author leaves it out.
     *
     * @param kind      what the file holds, such as "PDF", named in the chooser's filter
     * @param extension the file's extension, without the dot
     */
    public static Optional<Path> chooseSaveFile(Window owner, String title, Path initialDirectory, String initialName,
                                                String kind, String extension) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialName + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(kind, "*." + extension));
        if (initialDirectory != null && Files.isDirectory(initialDirectory))
            chooser.setInitialDirectory(initialDirectory.toFile());

        return Optional.ofNullable(chooser.showSaveDialog(owner))
                .map(File::toPath)
                .map(file -> file.getFileName().toString().endsWith("." + extension)
                        ? file
                        : file.resolveSibling(file.getFileName() + "." + extension));
    }

    /**
     * Asks for a file to open, starting in {@code initialDirectory}; empty if the author cancels.
     *
     * @param kind      what the file holds, such as "Zip archive", named in the chooser's filter
     * @param extension the file's extension, without the dot
     */
    public static Optional<Path> chooseOpenFile(Window owner, String title, Path initialDirectory, String kind,
                                                String extension) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(kind, "*." + extension));
        if (initialDirectory != null && Files.isDirectory(initialDirectory))
            chooser.setInitialDirectory(initialDirectory.toFile());

        return Optional.ofNullable(chooser.showOpenDialog(owner)).map(File::toPath);
    }

    /** {@code title} as a file name: without the characters file systems reserve for paths. */
    public static String fileName(String title) {

        return title.replaceAll("[/\\\\:*?\"<>|]", "-").strip();
    }

    /** Makes {@code owner} own the dialog, titles it after the app and gives it the window's stylesheets. */
    public static void ownAndStyle(Dialog<?> dialog, Window owner) {

        dialog.initOwner(owner);
        dialog.setTitle(APP_TITLE);
        dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
    }

    /** Prepare an error alert*/
    private static Alert errorAlert(Window owner, String header, Throwable error) {

        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        ownAndStyle(alert, owner);
        alert.setHeaderText(header);

        return alert;
    }

    /** Prepare a confirmation alert*/
    private static Alert confirmationAlert(Window owner, String header, String content, ButtonType[] choices) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, choices);
        ownAndStyle(alert, owner);
        alert.setHeaderText(header);

        return alert;
    }
}
