package it.infog.psiche.ui.preferences;

import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.enums.EditorMode;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.settings.model.enums.WindowTheme;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.DictionaryMark;
import it.infog.psiche.ui.common.LanguageChoice;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A form for what the author chooses for the app as a whole, rather than for one book, in three tabs, so it fits a
 * small screen: General, for how the window looks, how the app starts and what each new book starts with; Editor, for
 * how the text is shown and checked; and Preview, for how the page looks. Apply puts the choices into effect and
 * keeps the form open, so the author can try a size or a theme and see it at once; Save does the same and closes it.
 * Revert puts every field back as it was when the form opened, and undoes what was applied since; Cancel does the
 * same and closes it. About Psiche opens the About dialog above it; Check for updates asks psiche.app for a newer
 * release.
 */
public final class PreferencesDialog {

    private static final ButtonType SAVE = new ButtonType("Save", ButtonData.OK_DONE);
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonData.APPLY);
    // Placed before Apply, which it undoes.
    private static final ButtonType REVERT = new ButtonType("Revert", ButtonData.OTHER);
    private static final ButtonType ABOUT = new ButtonType("About Psiche", ButtonData.LEFT);
    private static final ButtonType UPDATES = new ButtonType(UpdateCheck.CHECK, ButtonData.LEFT);
    // The same in every group and tab, so their fields line up.
    private static final double LABEL_WIDTH = 140;
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
        dialog.getDialogPane().getButtonTypes().setAll(ABOUT, UPDATES, ButtonType.CANCEL, REVERT, APPLY, SAVE);

        // About and Check for updates open over the form, which stays open, instead of closing it as a dialog's
        // buttons do.
        Button about = leftButton(dialog, ABOUT);
        about.getStyleClass().add("primary");
        about.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            AboutDialog.show(dialog.getDialogPane().getScene().getWindow(), browser);
        });
        UpdateCheck.attach(leftButton(dialog, UPDATES), browser);

        //fields
        CheckBox reopen = new CheckBox("Reopen the last book when Psiche Writer starts");
        CheckBox checkSpelling = new CheckBox("Check spelling");
        CheckBox strictSpelling = new CheckBox("Strict spell check");
        strictSpelling.disableProperty().bind(checkSpelling.selectedProperty().not());
        CheckBox highlightRepetitions = new CheckBox("Highlight repetitions");
        CheckBox lineNumbers = new CheckBox("Always show line numbers");
        LanguageChoice language = new LanguageChoice(preferences.newBookLanguage());
        TextField author = new TextField();
        author.setPromptText("Your name, as it goes on the cover");
        author.setMaxWidth(Double.MAX_VALUE);
        ComboBox<EditorMode> editorMode = Controls.choices(List.of(EditorMode.values()), EditorMode::label);
        ComboBox<WindowTheme> windowTheme = Controls.choices(List.of(WindowTheme.values()), WindowTheme::label);
        ComboBox<PreviewTheme> theme = Controls.choices(List.of(PreviewTheme.values()), PreviewTheme::label);
        ComboBox<Integer> editorFontSize = Controls.choices(Preferences.FONT_SIZES, FONT_SIZE::formatted);
        ComboBox<Integer> previewFontSize = Controls.choices(Preferences.FONT_SIZES, FONT_SIZE::formatted);
        Consumer<Preferences> fill = shown -> {
            reopen.setSelected(shown.reopenLastBook());
            language.setValue(Locale.forLanguageTag(shown.newBookLanguage()));
            author.setText(shown.newBookAuthor());
            theme.setValue(shown.previewTheme());
            editorFontSize.setValue(shown.editorFontSize());
            previewFontSize.setValue(shown.previewFontSize());
            checkSpelling.setSelected(shown.checkSpelling());
            strictSpelling.setSelected(shown.strictSpelling());
            highlightRepetitions.setSelected(shown.highlightRepetitions());
            editorMode.setValue(shown.editorMode());
            lineNumbers.setSelected(shown.alwaysShowLineNumbers());
            windowTheme.setValue(shown.windowTheme());
        };
        fill.accept(preferences);

        Label editorModeHint = Controls.hint("Default fades the Markdown and keeps links and images whole; Advanced "
                + "shows it as written.");
        Label spellingHint = Controls.hint("In the languages Psiche has a dictionary for.");
        Label strictHint = Controls.hint("Also checks the words with a capital, as names have.");
        Label lineNumbersHint = Controls.hint("Otherwise, hold Alt to see them (Option on macOS).");
        Label repetitionsHint = Controls.hint("Shows where the selected word, or the one being written, comes back.");
        Label newBooksHint = Controls.hint("A book's own language and authors are in Book properties.");
        Label windowThemeHint = Controls.hint("High contrast makes buttons, borders and grey text easier to read on "
                + "a dim screen.");

        //general
        GridPane appearance = new GridPane();
        appearance.addRow(0, new Label("Theme"), windowTheme);
        appearance.add(windowThemeHint, 1, 1);

        GridPane startup = new GridPane();
        startup.addRow(0, new Label("Last book"), reopen);

        GridPane newBooks = new GridPane();
        newBooks.addRow(0, new Label("Language"), DictionaryMark.after(language));
        newBooks.addRow(1, new Label("Author"), author);
        newBooks.add(newBooksHint, 1, 2);

        //editor
        GridPane editor = new GridPane();
        editor.addRow(0, new Label("Editor mode"), editorMode);
        editor.add(editorModeHint, 1, 1);
        editor.addRow(2, new Label("Editor font size"), editorFontSize);
        editor.addRow(3, new Label("Line numbers"), lineNumbers);
        editor.add(lineNumbersHint, 1, 4);
        editor.addRow(5, new Label("Spelling"), checkSpelling);
        editor.add(spellingHint, 1, 6);
        editor.add(strictSpelling, 1, 7);
        editor.add(strictHint, 1, 8);
        editor.addRow(9, new Label("Repetitions"), highlightRepetitions);
        editor.add(repetitionsHint, 1, 10);

        //preview
        GridPane preview = new GridPane();
        preview.addRow(0, new Label("Preview theme"), theme);
        preview.addRow(1, new Label("Preview font size"), previewFontSize);

        TabPane tabs = new TabPane(
                tab("General", Controls.fieldset("Appearance", appearance, LABEL_WIDTH),
                        Controls.fieldset("Startup", startup, LABEL_WIDTH),
                        Controls.fieldset("New book defaults", newBooks, LABEL_WIDTH)),
                tab("Editor", Controls.fieldset("Editor", editor, LABEL_WIDTH)),
                tab("Preview", Controls.fieldset("Preview", preview, LABEL_WIDTH)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("preferences-tabs");
        dialog.getDialogPane().setContent(tabs);

        Supplier<Preferences> chosen = () -> new Preferences(reopen.isSelected(),
                language.getValue().toLanguageTag(), author.getText().strip(), theme.getValue(),
                editorFontSize.getValue(), previewFontSize.getValue(), checkSpelling.isSelected(),
                strictSpelling.isSelected(), highlightRepetitions.isSelected(), editorMode.getValue(),
                lineNumbers.isSelected(), windowTheme.getValue());

        // Apply and Revert, unlike the dialog's other buttons, leave it open: it takes the window's stylesheets again,
        // so it shows the window theme applied.
        Consumer<Preferences> applyHere = applying -> {
            apply.accept(applying);
            Dialogs.restyle(dialog, owner);
        };
        AtomicReference<Preferences> applied = new AtomicReference<>(preferences);
        dialog.getDialogPane().lookupButton(APPLY).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            applied.set(chosen.get());
            applyHere.accept(applied.get());
        });
        Runnable undoApplied = () -> {
            if (!applied.getAndSet(preferences).equals(preferences))
                applyHere.accept(preferences);
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

    /** A tab of the form, its groups one under the other, at the top however tall the tallest tab makes it. */
    /** A button at the bar's left, sized to its own text so Cancel and Save keep theirs. */
    private static Button leftButton(Dialog<ButtonType> dialog, ButtonType type) {

        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        ButtonBar.setButtonUniformSize(button, false);

        return button;
    }

    private static Tab tab(String name, Node... groups) {

        VBox form = new VBox(groups);
        form.getStyleClass().add("preferences-form");
        return new Tab(name, form);
    }
}
