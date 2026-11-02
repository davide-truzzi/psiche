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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A form for what the author chooses for the app as a whole, rather than for one book, in two groups: General, for
 * the app itself, and New book defaults, for what each new book starts with. Apply puts the choices into effect and
 * keeps the form open, so the author can try a size or a theme and see it at once; Save does the same and closes it.
 * Revert puts every field back as it was when the form opened, and undoes what was applied since; Cancel does the
 * same and closes it. About Psiche opens the About dialog above it.
 */
public final class PreferencesDialog {

    private static final ButtonType SAVE = new ButtonType("Save", ButtonData.OK_DONE);
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonData.APPLY);
    // Placed before Apply, which it undoes.
    private static final ButtonType REVERT = new ButtonType("Revert", ButtonData.OTHER);
    private static final ButtonType ABOUT = new ButtonType("About Psiche", ButtonData.LEFT);
    // The same in both groups, so their fields line up.
    private static final double LABEL_WIDTH = 130;
    private static final String FONT_SIZE = "%d px";

    private PreferencesDialog() {
    }

    /**
     * Shows the form filled in with {@code preferences} and waits for the author, handing {@code apply} the choices
     * each time they apply or save them, and {@code preferences} again if they revert or cancel what they applied.
     *
     * @param browser opens a web address, from the About dialog, in the system's default browser
     */
    public static void show(Window owner, Preferences preferences, Consumer<String> browser,
                            Consumer<Preferences> apply) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Preferences");
        dialog.getDialogPane().getStyleClass().add("preferences");
        dialog.getDialogPane().getButtonTypes().setAll(ABOUT, ButtonType.CANCEL, REVERT, APPLY, SAVE);

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
        LanguageChoice language = new LanguageChoice(preferences.newBookLanguage());
        TextField author = new TextField();
        author.setPromptText("Your name, as it goes on the cover");
        author.setMaxWidth(Double.MAX_VALUE);
        ComboBox<PreviewTheme> theme = choices(List.of(PreviewTheme.values()), PreviewTheme::label);
        ComboBox<Integer> editorFontSize = choices(Preferences.FONT_SIZES, FONT_SIZE::formatted);
        ComboBox<Integer> previewFontSize = choices(Preferences.FONT_SIZES, FONT_SIZE::formatted);
        Consumer<Preferences> fill = shown -> {
            reopen.setSelected(shown.reopenLastBook());
            language.setValue(Locale.forLanguageTag(shown.newBookLanguage()));
            author.setText(shown.newBookAuthor());
            theme.setValue(shown.previewTheme());
            editorFontSize.setValue(shown.editorFontSize());
            previewFontSize.setValue(shown.previewFontSize());
        };
        fill.accept(preferences);

        Label newBooksHint = new Label("A book's own language and authors are in Book properties.");
        newBooksHint.getStyleClass().add("form-hint");

        //groups
        GridPane general = new GridPane();
        general.addRow(0, new Label("Startup"), reopen);
        general.addRow(1, new Label("Editor font size"), editorFontSize);
        general.addRow(2, new Label("Preview theme"), theme);
        general.addRow(3, new Label("Preview font size"), previewFontSize);

        GridPane newBooks = new GridPane();
        newBooks.addRow(0, new Label("Language"), language);
        newBooks.addRow(1, new Label("Author"), author);
        newBooks.add(newBooksHint, 1, 2);

        VBox form = new VBox(Controls.fieldset("General", general, LABEL_WIDTH),
                Controls.fieldset("New book defaults", newBooks, LABEL_WIDTH));
        form.getStyleClass().add("preferences-form");
        dialog.getDialogPane().setContent(form);

        Supplier<Preferences> chosen = () -> new Preferences(reopen.isSelected(),
                language.getValue().toLanguageTag(), author.getText().strip(), theme.getValue(),
                editorFontSize.getValue(), previewFontSize.getValue());

        // Apply and Revert, unlike the dialog's other buttons, leave it open.
        AtomicReference<Preferences> applied = new AtomicReference<>(preferences);
        dialog.getDialogPane().lookupButton(APPLY).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            applied.set(chosen.get());
            apply.accept(applied.get());
        });
        Runnable undoApplied = () -> {
            if (!applied.getAndSet(preferences).equals(preferences))
                apply.accept(preferences);
        };
        dialog.getDialogPane().lookupButton(REVERT).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            fill.accept(preferences);
            undoApplied.run();
        });

        // Cancel, Escape and the window's close button all leave the preferences as the form found them.
        if (dialog.showAndWait().filter(SAVE::equals).isPresent())
            apply.accept(chosen.get());
        else
            undoApplied.run();
    }

    /** A box to pick one of {@code items} by {@code label}, as wide as the form. */
    private static <T> ComboBox<T> choices(List<T> items, Function<T, String> label) {

        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : label.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        return combo;
    }
}
