package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.common.Dialogs;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Shows an image with its caption, to insert it into the chapter or to modify one there: a preview, its file's name,
 * format and sizes, the caption to write, and the quality warning if it will look poor in the exports. An image whose
 * file is missing shows no preview, and its file's name in orange. Insert or Modify, Choose another, or Cancel.
 */
final class InsertImageDialog {

    static final ButtonType INSERT = new ButtonType("Insert", ButtonData.OK_DONE);
    static final ButtonType MODIFY = new ButtonType("Modify", ButtonData.OK_DONE);
    static final ButtonType CHOOSE_ANOTHER = new ButtonType("Choose another", ButtonData.LEFT);

    // The preview's box; the image is scaled down to fit it, keeping its proportions.
    private static final double PREVIEW_WIDTH = 220;
    private static final double PREVIEW_HEIGHT = 180;
    private static final String MISSING = "missing";

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final TextField caption = new TextField();

    private InsertImageDialog(Window owner, String header, ButtonType accept, Optional<MediaImage> image,
                              String fileName, String captionText, String hint) {

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().setAll(CHOOSE_ANOTHER, ButtonType.CANCEL, accept);
        dialog.getDialogPane().lookupButton(accept).getStyleClass().add("primary");

        caption.setText(captionText);
        dialog.getDialogPane().setContent(content(image, fileName, hint));
        Platform.runLater(caption::requestFocus);
    }

    /**
     * The dialog of Insert image, for {@code image}, with a caption suggested from its file's name.
     *
     * @param chapter  the chapter the image goes into, named where its copy is kept
     * @param inFolder true for one of the chapter's images already, which is not copied again
     */
    static InsertImageDialog inserting(Window owner, MediaImage image, String chapter, boolean inFolder) {

        String hint = inFolder
                ? "Already in media/%s/; inserted after the caret's line."
                : "Copied to media/%s/ and inserted after the caret's line.";
        return new InsertImageDialog(owner, "Insert image", INSERT, Optional.of(image), image.name(),
                ImageLabels.suggestedCaption(image.name()), hint.formatted(chapter));
    }

    /**
     * The dialog of Modify image, for an image of the chapter with {@code caption}.
     *
     * @param image    the image's file as read; empty if it is missing
     * @param fileName the name of the image's file, which a missing one has too
     * @param chapter  the chapter the image is in, named where its file is kept
     * @param chosen   true for a file the author chose in place of the image's, which Modify copies into the folder
     */
    static InsertImageDialog modifying(Window owner, Optional<MediaImage> image, String fileName, String caption,
                                       String chapter, boolean chosen) {

        String hint;
        if (chosen)
            hint = "Copied to media/%s/ in place of the image's file, which stays in the folder.";
        else if (image.isEmpty())
            hint = "Its file is not in media/%s/: choose another to repair the image.";
        else
            hint = "In media/%s/; Modify writes the caption in place.";
        return new InsertImageDialog(owner, "Modify image", MODIFY, image, fileName, caption,
                hint.formatted(chapter));
    }

    /** Shows the dialog until the author picks a button; empty if it is closed otherwise. */
    Optional<ButtonType> showAndWait() {

        return dialog.showAndWait();
    }

    /** The caption as the author left it. */
    String caption() {

        return caption.getText();
    }

    private Node content(Optional<MediaImage> image, String fileName, String hintText) {

        //preview, none for a missing file
        StackPane frame = new StackPane();
        image.ifPresent(shown -> frame.getChildren().add(new ImageView(new Image(shown.file().toUri().toString(),
                PREVIEW_WIDTH, PREVIEW_HEIGHT, true, true, true))));
        frame.getStyleClass().add("insert-image-preview");
        frame.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        frame.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);

        //file
        Label name = new Label(fileName);
        name.getStyleClass().add("insert-image-name");
        if (image.isEmpty())
            name.getStyleClass().add(MISSING);
        Label details = new Label(image.map(ImageLabels::details).orElse("Missing file"));
        details.getStyleClass().add("insert-image-details");

        //caption
        Label captionLabel = new Label("Caption");
        captionLabel.setLabelFor(caption);
        caption.getStyleClass().add("insert-image-caption");

        VBox about = new VBox(name, details, captionLabel, caption);
        about.getStyleClass().add("insert-image-about");
        image.filter(MediaImage::poor).ifPresent(poor ->
                about.getChildren().add(ImageLabels.qualityWarning(poor.issues())));

        //where it is
        Label hint = new Label(hintText);
        hint.getStyleClass().add("insert-image-hint");

        VBox content = new VBox(new HBox(frame, about), hint);
        content.getStyleClass().add("insert-image-content");
        return content;
    }
}
