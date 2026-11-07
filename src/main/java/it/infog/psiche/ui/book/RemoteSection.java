package it.infog.psiche.ui.book;

import it.infog.psiche.git.RemoteAddresses;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.AddressProblem;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.model.Credentials;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.book.RemoteTest.Outcome;
import it.infog.psiche.ui.book.RemoteTest.Tone;
import it.infog.psiche.ui.git.KeychainDialogs;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The Remote repository section of Book properties, closed unless the book has a remote: its address, an
 * {@code https://} one with a username and a token, or a folder; Test, which {@link RemoteTest} runs, and Remove
 * remote.
 * <p>
 * Save waits for a good Test while the remote's fields are changed: editing a field after a good test asks for
 * another. Nothing is changed until Save, when {@link #apply} connects or removes the remote on the Git thread.
 */
final class RemoteSection {

    private static final String ABOUT = "For advanced use: the book's own history and the Psiche project export are "
            + "enough for most authors. A remote repository keeps a copy of the book's history on a host, such as "
            + "GitHub, or on another drive, and Synchronize keeps the two in step.";
    private static final String ADDRESS_HINT = "An https:// address, as GitHub or GitLab show it, or a folder.";
    private static final String DRIVE_NOTE = "Choose a folder on another drive than the book's, so one failing disk "
            + "cannot take both copies. Psiche keeps the book in a PsicheRepository folder inside it.";
    private static final String SAVED = "Saved in the keychain";
    private static final String REMOVE_ABOUT = "The book stays as it is, and so does the repository, with what was "
            + "sent to it. Synchronize goes away until you connect a remote again.";

    private final GitControls git;
    private final RemoteTest remoteTest;
    private final Keychain keychain;
    private final Path book;
    private final Optional<String> saved;
    private final boolean inGit;

    private final TextField address = new TextField();
    private final TextField username = new TextField();
    private final PasswordField token = new PasswordField();
    private final Label addressHint = Controls.hint(ADDRESS_HINT);
    private final Label driveNote = Controls.hint(DRIVE_NOTE);
    private final Label result = Controls.hint("");
    private final BooleanProperty tested = new SimpleBooleanProperty();
    private final BooleanProperty testing = new SimpleBooleanProperty();
    private final BooleanProperty removing = new SimpleBooleanProperty();
    private final BooleanBinding changed;
    private final BooleanBinding savable;

    // Counts the edits, so a Test that ends after one does not count.
    private int edits;
    private RemoteAddress testedAddress;
    private Optional<Credentials> testedCredentials = Optional.empty();
    private boolean takesBook;
    // The title in the form, which an empty book must share with the repository's to take it.
    private ObservableValue<String> title = new SimpleStringProperty("");

    /**
     * @param git   runs Test, and at Save connects or removes the remote, on the Git thread
     * @param book  the book's folder
     * @param saved the book's remote's address, if it has one
     * @param inGit false for a book without a Git repository, which cannot have a remote
     */
    RemoteSection(GitControls git, Keychain keychain, Path book, Optional<String> saved, boolean inGit) {

        this.git = git;
        remoteTest = new RemoteTest(git, book);
        this.keychain = keychain;
        this.book = book;
        this.saved = saved;
        this.inGit = inGit;

        address.setText(saved.orElse(""));
        Observable[] fields = {address.textProperty(), username.textProperty(), token.textProperty()};
        changed = Bindings.createBooleanBinding(() -> !address.getText().strip().equals(saved.orElse(""))
                || !username.getText().isBlank() || token.getLength() > 0, fields);
        savable = Bindings.createBooleanBinding(() -> !testing.get()
                && (removing.get() || !changed.get() || tested.get()), changed, tested, testing, removing);
        for (Observable field : fields)
            field.addListener(observable -> edited());
    }

    /** Takes the book's title from the form's {@code title} field; editing it asks for another Test. */
    void followTitle(ObservableValue<String> title) {

        this.title = title;
        title.addListener(observable -> edited());
    }

    /** True while Save may close the form: the remote's fields are as they were, tested, or the remote removed. */
    BooleanBinding savable() {

        return savable;
    }

    /** The section, closed unless the book has a remote. */
    TitledPane node() {

        //fields
        address.setPromptText("https://github.com/you/book.git");
        Button choose = Controls.button("Choose folder", this::chooseFolder);
        HBox addressRow = new HBox(address, choose);
        addressRow.getStyleClass().add("remote-address");
        HBox.setHgrow(address, Priority.ALWAYS);

        boolean savedHttps = saved.map(RemoteAddresses::kind).filter(RemoteKind.HTTPS::equals).isPresent();
        username.setPromptText(savedHttps ? SAVED : "Your account's name on the host");
        token.setPromptText(savedHttps ? SAVED : "A personal access token that can write");
        Label usernameLabel = new Label("Username");
        Label tokenLabel = new Label("Token");

        // Wrapped on as many lines as they need, never cut short.
        Label about = Controls.hint(ABOUT);
        for (Label text : new Label[]{about, driveNote, result}) {
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            text.getStyleClass().add("remote-text");
        }

        GridPane form = new GridPane();
        form.getStyleClass().addAll("book-form", "remote-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labels, values);
        form.addRow(0, new Label("Address"), addressRow);
        form.add(addressHint, 1, 1);
        form.addRow(2, usernameLabel, username);
        form.addRow(3, tokenLabel, token);

        //buttons
        Button test = Controls.button("Test", this::test);
        test.disableProperty().bind(Bindings.createBooleanBinding(() -> !testable(), address.textProperty(),
                username.textProperty(), token.textProperty(), testing, removing));
        Button remove = Controls.button("Remove remote", this::remove);
        remove.disableProperty().bind(testing.or(removing));
        Controls.setShown(remove, saved.isPresent());
        HBox buttons = new HBox(test, remove);
        buttons.getStyleClass().add("remote-buttons");

        address.disableProperty().bind(testing.or(removing));
        choose.disableProperty().bind(address.disableProperty());
        username.disableProperty().bind(address.disableProperty());
        token.disableProperty().bind(address.disableProperty());

        VBox content = new VBox(about, form, driveNote, buttons, result);
        content.getStyleClass().add("remote-section-content");
        TitledPane section = new TitledPane("Remote repository", content);
        section.getStyleClass().add("remote-section");
        section.setExpanded(saved.isPresent());
        // At once, so the window fits the section's height, not a height on the way to it.
        section.setAnimated(false);
        section.setDisable(!inGit);
        section.expandedProperty().addListener((obs, was, expanded) -> fit());

        //fields shown as the address is of one kind or the other
        Runnable showFields = () -> {
            boolean https = !address.getText().isBlank() && RemoteAddresses.kind(address.getText()) == RemoteKind.HTTPS;
            for (Region field : new Region[]{usernameLabel, username, tokenLabel, token})
                Controls.setShown(field, https);
            Controls.setShown(driveNote, !https && RemoteAddresses.problem(address.getText()).isEmpty());
            showAddressProblem();
            fit();
        };
        address.textProperty().addListener(observable -> showFields.run());
        showFields.run();
        Controls.setShown(result, false);

        return section;
    }

    /**
     * Readies Save: a new token needs the keychain unlocked, or created the first time, to be kept in it.
     *
     * @return false if the author cancels that, so the form stays open
     */
    boolean readyToSave() {

        if (removing.get() || !changed.get() || testedCredentials.isEmpty())
            return true;

        return KeychainDialogs.unlocked(window(), keychain);
    }

    /** Once the form is saved: connects the tested remote, or removes the book's, on the Git thread. */
    void apply() {

        if (removing.get())
            git.removeRemote();
        else if (changed.get() && tested.get())
            git.connectRemote(testedAddress, testedCredentials, takesBook);
    }

    private void edited() {

        edits++;
        tested.set(false);
        if (!removing.get())
            Controls.setShown(result, false);
    }

    /** True if Test can run: an address that may be a remote, and for HTTPS the credentials, typed or saved. */
    private boolean testable() {

        if (testing.get() || removing.get() || RemoteAddresses.problem(address.getText()).isPresent())
            return false;

        if (RemoteAddresses.kind(address.getText()) == RemoteKind.FOLDER)
            return true;

        boolean typed = !username.getText().isBlank() && token.getLength() > 0;
        boolean savedOnes = username.getText().isBlank() && token.getLength() == 0
                && saved.filter(address.getText().strip()::equals).isPresent();
        return typed || savedOnes;
    }

    private void test() {

        RemoteAddress target = RemoteAddresses.read(address.getText());
        Optional<Credentials> typed = target.kind() == RemoteKind.HTTPS && token.getLength() > 0
                ? Optional.of(new Credentials(username.getText().strip(), token.getText()))
                : Optional.empty();
        // The saved token is read from the keychain, which may need unlocking first.
        if (target.kind() == RemoteKind.HTTPS && typed.isEmpty() && !KeychainDialogs.unlocked(window(), keychain))
            return;

        int started = edits;
        String bookTitle = title.getValue();
        testing.set(true);
        show(new Outcome(false, "Testing…", Tone.NOTE));
        git.ask(repository -> remoteTest.outcome(repository, target, typed, bookTitle), outcome -> {
            testing.set(false);
            if (started != edits)
                return;

            tested.set(outcome.accepted());
            testedAddress = target;
            testedCredentials = typed;
            takesBook = outcome.takesBook();
            show(outcome);
        }, error -> {
            testing.set(false);
            show(new Outcome(false, error.getMessage(), Tone.WRONG));
        });
    }

    /** Asks to confirm, with the master password for a token in the keychain, then marks the remote for removal. */
    private void remove() {

        boolean hasToken = saved.map(RemoteAddresses::kind).filter(RemoteKind.HTTPS::equals).isPresent()
                && keychain.exists();
        ButtonType removeType = new ButtonType("Remove remote", ButtonData.OK_DONE);
        boolean confirmed = hasToken
                ? KeychainDialogs.confirmed(window(), keychain)
                : Dialogs.choose(window(), "Remove the remote?", REMOVE_ABOUT, ButtonType.CANCEL, removeType)
                        .filter(removeType::equals).isPresent();
        if (!confirmed)
            return;

        removing.set(true);
        address.clear();
        username.clear();
        token.clear();
        show(new Outcome(true, "The remote is removed when you save the book's properties.", Tone.NOTE));
    }

    private void chooseFolder() {

        Optional<Path> current = RemoteAddresses.problem(address.getText()).isEmpty()
                && RemoteAddresses.kind(address.getText()) == RemoteKind.FOLDER
                ? Optional.of(Path.of(RemoteAddresses.read(address.getText()).address()))
                : Optional.empty();
        Dialogs.chooseDirectory(window(), "Choose the folder for the book's remote", current.orElse(null))
                .ifPresent(folder -> address.setText(folder.toString()));
    }

    /** The problem of the address as typed, in place of the hint; nothing while it is blank. */
    private void showAddressProblem() {

        Optional<AddressProblem> problem = RemoteAddresses.problem(address.getText())
                .filter(found -> found != AddressProblem.BLANK);
        addressHint.setText(problem.map(AddressProblem::message).orElse(ADDRESS_HINT));
        addressHint.getStyleClass().remove(Tone.WRONG.styleClass());
        if (problem.isPresent())
            addressHint.getStyleClass().add(Tone.WRONG.styleClass());
    }

    private void show(Outcome outcome) {

        result.setText(outcome.message());
        for (Tone tone : Tone.values())
            result.getStyleClass().remove(tone.styleClass());
        result.getStyleClass().add(outcome.tone().styleClass());
        Controls.setShown(result, true);
        fit();
    }

    /**
     * Fits the form's window to the section, opened, closed, or with more or fewer fields and texts. The form's
     * height is taken at its width and set as its own: measured without one, as {@code sizeToScene} does, wrapped
     * texts count as one line and the window comes out short.
     */
    private void fit() {

        Platform.runLater(() -> {
            Scene scene = address.getScene();
            if (scene == null || scene.getWindow() == null || !(scene.getRoot() instanceof Region form))
                return;

            form.setPrefHeight(Region.USE_COMPUTED_SIZE);
            form.setPrefHeight(form.prefHeight(scene.getWidth()));
            scene.getWindow().sizeToScene();
        });
    }

    private Window window() {

        return address.getScene().getWindow();
    }
}
