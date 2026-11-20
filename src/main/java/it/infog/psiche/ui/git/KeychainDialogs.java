package it.infog.psiche.ui.git;

import it.infog.psiche.settings.Keychain;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * The dialogs of Psiche's {@link Keychain}: choosing the master password the first time a token is saved, typing it
 * once a session when a token is first needed, typing it again to confirm a token's deletion, changing it, and
 * resetting a keychain whose password is forgotten.
 */
public final class KeychainDialogs {

    // Short ones fall quickly to guessing, if the file is ever copied off the computer.
    static final int MIN_LENGTH = 8;

    private static final ButtonType CREATE = new ButtonType("Create", ButtonData.OK_DONE);
    private static final ButtonType UNLOCK = new ButtonType("Unlock", ButtonData.OK_DONE);
    private static final ButtonType CONFIRM = new ButtonType("Confirm", ButtonData.OK_DONE);
    private static final ButtonType CHANGE = new ButtonType("Change", ButtonData.OK_DONE);
    private static final ButtonType RESET = new ButtonType("Reset keychain", ButtonData.LEFT);
    private static final String ABOUT = "Psiche keeps the tokens of your books' remote repositories in its own "
            + "keychain, locked by a master password, which it asks once a session, when a token is first needed.";
    private static final String CREATE_ABOUT = ABOUT + "\n\nKeep it somewhere safe, such as a password manager: it "
            + "can never be recovered, not even by Psiche. If you lose it, the keychain can only be reset, which "
            + "deletes every saved token, and you then make new access tokens on the hosts, such as GitHub or GitLab, "
            + "and type them in each book's properties. Your books and their history are not touched: not a big deal, "
            + "but a chore worth avoiding.\n\nYou can change it later in Preferences, while you still know it.";
    private static final String CHANGE_ABOUT = "Type the master password you have now, then the new one. The saved "
            + "tokens stay as they are, locked by the new password, which can never be recovered either: keep it "
            + "somewhere safe.";
    private static final String CONFIRM_ABOUT = "Removing the remote also deletes this book's token from Psiche's "
            + "keychain. Type the keychain's master password to confirm.";
    private static final String RESET_ABOUT = "Every saved token is deleted, for every book. You can make new ones "
            + "on the hosts, such as GitHub or GitLab, and type them in each book's properties. Revoke the old ones "
            + "there too: they keep working until you do.";

    private KeychainDialogs() {
    }

    /**
     * Makes sure {@code keychain} is unlocked: asks its master password, or the first time asks for one and creates
     * it. Nothing to ask if it is unlocked already.
     *
     * @return false if the author cancels, resets the keychain, or it cannot be created
     */
    public static boolean unlocked(Window owner, Keychain keychain) {

        if (keychain.unlocked())
            return true;

        return keychain.exists()
                ? askPassword(owner, keychain, "Unlock the keychain", ABOUT, UNLOCK)
                : create(owner, keychain);
    }

    /**
     * Asks the master password of {@code keychain} even if it is unlocked, to confirm something that deletes a token,
     * and leaves it unlocked.
     *
     * @return false if the author cancels or resets the keychain
     */
    public static boolean confirmed(Window owner, Keychain keychain) {

        return askPassword(owner, keychain, "Confirm with the master password", CONFIRM_ABOUT, CONFIRM);
    }

    /**
     * Asks the master password of {@code keychain} and a new one, typed twice, and locks the keychain with the new
     * one, which leaves it unlocked. A wrong password keeps the dialog open, saying so, for another try.
     *
     * @return true if the password was changed
     */
    public static boolean changePassword(Window owner, Keychain keychain) {

        Dialog<ButtonType> dialog = dialog(owner, "Change the master password", CHANGE);

        PasswordField current = new PasswordField();
        GridPane form = form();
        form.addRow(0, new Label("Current password"), current);
        PasswordField chosen = newPassword(dialog, CHANGE, form, 2, "New password",
                current.textProperty().isEmpty());
        Label problem = retried(dialog, CHANGE, current, typed -> changed(keychain, typed, chosen),
                "The keychain cannot be changed: %s.");
        form.add(problem, 1, 1);
        content(dialog, CHANGE_ABOUT, form, current);

        return dialog.showAndWait().filter(CHANGE::equals).isPresent();
    }

    /** Asks for a new master password, typed twice, and creates the keychain with it. */
    private static boolean create(Window owner, Keychain keychain) {

        Dialog<ButtonType> dialog = dialog(owner, "Choose a master password", CREATE);

        GridPane form = form();
        PasswordField password = newPassword(dialog, CREATE, form, 0, "Master password",
                new SimpleBooleanProperty(false));
        content(dialog, CREATE_ABOUT, form, password);

        if (dialog.showAndWait().filter(CREATE::equals).isEmpty())
            return false;

        char[] chosen = characters(password);
        try {
            keychain.create(chosen);
            return true;
        } catch (UncheckedIOException e) {
            Dialogs.showError(owner, "The keychain cannot be created", e);
            return false;
        } finally {
            Arrays.fill(chosen, '\0');
        }
    }

    /**
     * Asks the master password until it is the right one, which unlocks the keychain, the author cancels, or resets
     * the keychain.
     */
    private static boolean askPassword(Window owner, Keychain keychain, String header, String about,
                                       ButtonType action) {

        Dialog<ButtonType> dialog = dialog(owner, header, action);
        dialog.getDialogPane().getButtonTypes().addFirst(RESET);

        PasswordField password = new PasswordField();
        dialog.getDialogPane().lookupButton(action).disableProperty().bind(password.textProperty().isEmpty());
        Label problem = retried(dialog, action, password, keychain::unlock,
                "The keychain cannot be read: %s. Reset it to start again.");

        // Reset asks first, and closes the dialog only once done.
        Button reset = (Button) dialog.getDialogPane().lookupButton(RESET);
        ButtonBar.setButtonUniformSize(reset, false);
        reset.addEventFilter(ActionEvent.ACTION, event -> {
            if (!confirmReset(dialog.getDialogPane().getScene().getWindow(), keychain))
                event.consume();
        });

        GridPane form = form();
        form.addRow(0, new Label("Master password"), password);
        form.add(problem, 1, 1);
        content(dialog, about, form, password);

        return dialog.showAndWait().filter(action::equals).isPresent() && keychain.unlocked();
    }

    /**
     * Adds the rows of a new password, typed twice, to {@code form} from {@code row} on, and keeps {@code action} off
     * while it is too short, the two differ, or {@code missing} is true.
     *
     * @return the field of the new password
     */
    private static PasswordField newPassword(Dialog<ButtonType> dialog, ButtonType action, GridPane form, int row,
                                             String label, BooleanExpression missing) {

        PasswordField password = new PasswordField();
        PasswordField repeated = new PasswordField();
        Label different = wrong("The two passwords differ.");
        different.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !repeated.getText().isEmpty() && !repeated.getText().equals(password.getText()),
                password.textProperty(), repeated.textProperty()));

        dialog.getDialogPane().lookupButton(action).disableProperty().bind(missing.or(Bindings.createBooleanBinding(
                () -> password.getLength() < MIN_LENGTH || !repeated.getText().equals(password.getText()),
                password.textProperty(), repeated.textProperty())));

        form.addRow(row, new Label(label), password);
        form.add(Controls.hint("At least %d characters.".formatted(MIN_LENGTH)), 1, row + 1);
        form.addRow(row + 2, new Label("Repeat it"), repeated);
        form.add(different, 1, row + 3);
        return password;
    }

    /**
     * Keeps the dialog open while {@code attempt} refuses the password in {@code field}, saying so under it, for
     * another try; {@code failure} tells, with its message, a keychain that cannot be read or written.
     *
     * @return the label that says what went wrong, hidden until then, for the caller to place under the field
     */
    private static Label retried(Dialog<ButtonType> dialog, ButtonType action, PasswordField field,
                                 Predicate<char[]> attempt, String failure) {

        Label problem = wrong("");
        problem.setWrapText(true);
        Controls.setShown(problem, false);
        field.textProperty().addListener((obs, old, text) -> Controls.setShown(problem, false));

        dialog.getDialogPane().lookupButton(action).addEventFilter(ActionEvent.ACTION, event -> {
            // Emptied before the problem is told, which typing hides.
            char[] typed = characters(field);
            field.clear();
            try {
                if (attempt.test(typed))
                    return;

                say(problem, "Wrong password.");
            } catch (UncheckedIOException e) {
                say(problem, failure.formatted(e.getMessage()));
            } finally {
                Arrays.fill(typed, '\0');
            }

            event.consume();
            field.requestFocus();
        });

        return problem;
    }

    /** Changes the master password from {@code current} to the one in {@code chosen}; false if it is the wrong one. */
    private static boolean changed(Keychain keychain, char[] current, PasswordField chosen) {

        char[] replacement = characters(chosen);
        try {
            return keychain.changePassword(current, replacement);
        } finally {
            Arrays.fill(replacement, '\0');
        }
    }

    /** Asks whether to delete every token, and does it; false if the author changes their mind or it fails. */
    private static boolean confirmReset(Window owner, Keychain keychain) {

        ButtonType reset = new ButtonType("Reset keychain", ButtonData.OK_DONE);
        if (Dialogs.choose(owner, "Reset the keychain?", RESET_ABOUT, ButtonType.CANCEL, reset)
                .filter(reset::equals).isEmpty())
            return false;

        try {
            keychain.reset();
            return true;
        } catch (UncheckedIOException e) {
            Dialogs.showError(owner, "The keychain cannot be reset", e);
            return false;
        }
    }

    /** A dialog headed {@code header}, with Cancel and {@code action}. */
    private static Dialog<ButtonType> dialog(Window owner, String header, ButtonType action) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, action);
        dialog.getDialogPane().lookupButton(action).getStyleClass().add("primary");
        return dialog;
    }

    /** Puts {@code about} above {@code form} in the dialog, with the caret in {@code first} once it shows. */
    private static void content(Dialog<ButtonType> dialog, String about, GridPane form, Node first) {

        Label intro = new Label(about);
        intro.setWrapText(true);
        // Never cut short when a problem shows under the password.
        intro.setMinHeight(Region.USE_PREF_SIZE);
        intro.getStyleClass().add("keychain-about");

        VBox content = new VBox(intro, form);
        content.getStyleClass().add("keychain-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> first.requestFocus());
    }

    /** A form of labels and fields, the labels never cut short, the fields as wide as the dialog. */
    private static GridPane form() {

        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);

        GridPane form = new GridPane();
        form.getStyleClass().add("book-form");
        form.getColumnConstraints().setAll(labels, fields);
        return form;
    }

    /** A hint that tells something is wrong, in attention orange. */
    private static Label wrong(String text) {

        Label wrong = Controls.hint(text);
        wrong.getStyleClass().add("keychain-wrong");
        return wrong;
    }

    private static void say(Label problem, String text) {

        problem.setText(text);
        Controls.setShown(problem, true);
        problem.getScene().getWindow().sizeToScene();
    }

    /** What {@code field} holds, as characters the caller clears after use, unlike a string. */
    private static char[] characters(PasswordField field) {

        CharSequence text = field.getCharacters();
        char[] characters = new char[text.length()];
        for (int i = 0; i < characters.length; i++)
            characters[i] = text.charAt(i);

        return characters;
    }
}
