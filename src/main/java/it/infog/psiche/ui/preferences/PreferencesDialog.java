package it.infog.psiche.ui.preferences;

import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.LanguageChoice;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A form for what the author chooses for the app as a whole, rather than for one book, in two groups: General, for
 * the app itself, and New book defaults, for what each new book starts with. About Psiche opens the About dialog
 * above it.
 */
public final class PreferencesDialog {

    private static final ButtonType SAVE = new ButtonType("Save", ButtonData.OK_DONE);
    private static final ButtonType ABOUT = new ButtonType("About Psiche", ButtonData.LEFT);
    // The same in both groups, so their fields line up.
    private static final double LABEL_WIDTH = 80;

    private PreferencesDialog() {
    }

    /**
     * Shows the form filled in with {@code preferences} and waits for the author.
     *
     * @param browser opens a web address, from the About dialog, in the system's default browser
     * @return empty if the author cancels
     */
    public static Optional<Preferences> show(Window owner, Preferences preferences, Consumer<String> browser) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Preferences");
        dialog.getDialogPane().getStyleClass().add("preferences");
        dialog.getDialogPane().getButtonTypes().setAll(ABOUT, ButtonType.CANCEL, SAVE);

        // About opens over the form, which stays open, instead of closing it as a dialog's buttons do.
        Button about = (Button) dialog.getDialogPane().lookupButton(ABOUT);
        about.getStyleClass().add("primary");
        // Sized to its own text, so Cancel and Save keep theirs.
        ButtonBar.setButtonUniformSize(about, false);
        about.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            AboutDialog.show(dialog.getDialogPane().getScene().getWindow(), browser);
        });

        //fields
        CheckBox reopen = new CheckBox("Reopen the last book when Psiche Writer starts");
        reopen.setSelected(preferences.reopenLastBook());
        LanguageChoice language = new LanguageChoice(preferences.newBookLanguage());
        TextField author = new TextField(preferences.newBookAuthor());
        author.setPromptText("Your name, as it goes on the cover");
        author.setMaxWidth(Double.MAX_VALUE);
        ComboBox<PreviewTheme> theme = themes(preferences.previewTheme());

        Label newBooksHint = new Label("A book's own language and authors are in Book properties.");
        newBooksHint.getStyleClass().add("form-hint");

        //groups
        GridPane general = new GridPane();
        general.addRow(0, new Label("Startup"), reopen);
        general.addRow(1, new Label("Preview"), theme);

        GridPane newBooks = new GridPane();
        newBooks.addRow(0, new Label("Language"), language);
        newBooks.addRow(1, new Label("Author"), author);
        newBooks.add(newBooksHint, 1, 2);

        VBox form = new VBox(Controls.fieldset("General", general, LABEL_WIDTH),
                Controls.fieldset("New book defaults", newBooks, LABEL_WIDTH));
        form.getStyleClass().add("preferences-form");
        dialog.getDialogPane().setContent(form);

        if (dialog.showAndWait().filter(SAVE::equals).isEmpty())
            return Optional.empty();

        return Optional.of(new Preferences(reopen.isSelected(), language.getValue().toLanguageTag(),
                author.getText().strip(), theme.getValue()));
    }

    /** The preview's themes by name, with {@code current} selected. */
    private static ComboBox<PreviewTheme> themes(PreviewTheme current) {

        ComboBox<PreviewTheme> combo = new ComboBox<>();
        combo.getItems().setAll(PreviewTheme.values());
        combo.setValue(current);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(PreviewTheme theme) {
                return theme == null ? "" : theme.label();
            }

            @Override
            public PreviewTheme fromString(String text) {
                return null;
            }
        });
        return combo;
    }
}
