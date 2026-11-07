package it.infog.psiche.ui.book;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.DictionaryMark;
import it.infog.psiche.ui.common.ImageFilter;
import it.infog.psiche.ui.common.LanguageChoice;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.time.Year;
import java.util.Optional;

/**
 * A form for the book's properties and its cover, everything an ebook of the book needs besides the chapters, and,
 * under them, its remote repository.
 */
public final class BookPropertiesDialog {

    /**
     * What the author saved.
     *
     * @param cover the cover the book should have: the current one, a new image to copy in, or empty for none
     */
    record Edit(BookProperties properties,
                Optional<Path> cover) {
    }

    private static final ButtonType SAVE = new ButtonType("Save", ButtonData.OK_DONE);
    private static final double COVER_WIDTH = 120;
    private static final double COVER_HEIGHT = 192;

    private BookPropertiesDialog() {
    }

    /**
     * Shows the form filled in with {@code properties} and waits for the author.
     *
     * @param cover  the book's cover, if it has one
     * @param remote the Remote repository section, applied by the caller once the form is saved
     * @return empty if the author cancels
     */
    static Optional<Edit> show(Window owner, BookProperties properties, Optional<Path> cover, RemoteSection remote) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Book properties");
        dialog.getDialogPane().getStyleClass().add("book-properties");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, SAVE);

        //text fields
        TextField title = new TextField(properties.title());
        remote.followTitle(title.textProperty());
        TextField subtitle = new TextField(properties.subtitle());
        TextArea authors = new TextArea(String.join("\n", properties.authors()));
        authors.setPrefRowCount(3);
        authors.setPromptText("One name per line");
        LanguageChoice language = new LanguageChoice(properties.language());
        TextField rights = new TextField(properties.rights());
        suggestRights(rights, authors);

        // Also while the remote's fields are changed and not tested.
        Button save = (Button) dialog.getDialogPane().lookupButton(SAVE);
        save.disableProperty().bind(Bindings.createBooleanBinding(() -> title.getText().isBlank(),
                title.textProperty()).or(remote.savable().not()));
        save.addEventFilter(ActionEvent.ACTION, event -> {
            if (!remote.readyToSave())
                event.consume();
        });

        //cover
        CoverPicker coverPicker = new CoverPicker(cover);

        GridPane form = new GridPane();
        form.getStyleClass().add("book-form");
        form.addRow(0, new Label("Title"), title);
        form.addRow(1, new Label("Subtitle"), subtitle);
        form.addRow(2, new Label("Authors"), authors);
        form.addRow(3, new Label("Language"), DictionaryMark.after(language));
        form.addRow(4, new Label("Copyright"), rights);

        HBox book = new HBox(form, coverPicker.node());
        book.getStyleClass().add("book-properties-content");
        dialog.getDialogPane().setContent(new VBox(book, remote.node()));
        dialog.setOnShown(event -> title.requestFocus());

        if (dialog.showAndWait().filter(SAVE::equals).isEmpty())
            return Optional.empty();

        BookProperties edited = new BookProperties(
                title.getText(),
                subtitle.getText(),
                authors.getText().lines().filter(name -> !name.isBlank()).map(String::strip).toList(),
                language.getValue().toLanguageTag(),
                rights.getText(),
                properties.identifier());
        return Optional.of(new Edit(edited, coverPicker.chosen()));
    }

    /**
     * Keeps the copyright filled in as "© year first author" while it is empty or still that suggestion, and stops
     * once the author writes their own.
     */
    private static void suggestRights(TextField rights, TextArea authors) {

        Runnable suggest = () -> {
            String firstAuthor = authors.getText().lines().map(String::strip).filter(name -> !name.isEmpty())
                    .findFirst().orElse("");
            rights.setText(firstAuthor.isEmpty() ? "" : "© %d %s".formatted(Year.now().getValue(), firstAuthor));
        };

        boolean[] suggesting = {rights.getText().isBlank()};
        if (suggesting[0])
            suggest.run();

        // Setting the suggestion changes the text too, so only the author's own typing ends the suggestions.
        rights.setOnKeyTyped(event -> suggesting[0] = false);
        authors.textProperty().addListener((obs, old, text) -> {
            if (suggesting[0])
                suggest.run();
        });
    }

    /** The cover's thumbnail, with buttons to choose another image or remove it. Nothing is copied until Save. */
    private static final class CoverPicker {

        private final ImageView thumbnail = new ImageView();
        private final Label placeholder = new Label("No cover");
        private Optional<Path> chosen;

        CoverPicker(Optional<Path> cover) {

            thumbnail.setFitWidth(COVER_WIDTH);
            thumbnail.setFitHeight(COVER_HEIGHT);
            thumbnail.setPreserveRatio(true);
            placeholder.getStyleClass().add("cover-placeholder");
            use(cover);
        }

        Optional<Path> chosen() {
            return chosen;
        }

        VBox node() {

            StackPane frame = new StackPane(placeholder, thumbnail);
            frame.getStyleClass().add("cover-frame");
            frame.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
            frame.setMaxSize(COVER_WIDTH, COVER_HEIGHT);

            Label hint = new Label("JPEG or PNG, portrait,\nabout 1600 × 2560 pixels");
            hint.getStyleClass().add("cover-hint");

            Button choose = Controls.button("Choose image", () -> choose(frame.getScene().getWindow()));
            Button remove = Controls.button("Remove", () -> use(Optional.empty()));

            VBox box = new VBox(new Label("Cover"), frame, choose, remove, hint);
            box.getStyleClass().add("cover-picker");
            return box;
        }

        private void choose(Window owner) {

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the cover image");
            chooser.getExtensionFilters().add(ImageFilter.jpegOrPng());
            chosen.map(Path::getParent).map(Path::toFile).filter(File::isDirectory).ifPresent(chooser::setInitialDirectory);

            File file = chooser.showOpenDialog(owner);
            if (file != null)
                use(Optional.of(file.toPath()));
        }

        private void use(Optional<Path> cover) {

            chosen = cover;
            thumbnail.setImage(cover.map(path -> new Image(path.toUri().toString(), true)).orElse(null));
            placeholder.setVisible(cover.isEmpty());
        }
    }
}
