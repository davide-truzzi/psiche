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
 * Shows the image the author chose before it goes into the chapter: a preview, its file's name, format and sizes, a
 * caption to write, and the quality warning if it will look poor in the exports. Insert, Choose another or Cancel.
 */
final class InsertImageDialog {

    static final ButtonType INSERT = new ButtonType("Insert", ButtonData.OK_DONE);
    static final ButtonType CHOOSE_ANOTHER = new ButtonType("Choose another", ButtonData.LEFT);

    // The preview's box; the image is scaled down to fit it, keeping its proportions.
    private static final double PREVIEW_WIDTH = 220;
    private static final double PREVIEW_HEIGHT = 180;

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final TextField caption = new TextField();

    /**
     * @param chapter  the chapter the image goes into, named where its copy is kept
     * @param inFolder true for one of the chapter's images already, which is not copied again
     */
    InsertImageDialog(Window owner, MediaImage image, String chapter, boolean inFolder) {

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Insert image");
        dialog.getDialogPane().getButtonTypes().setAll(CHOOSE_ANOTHER, ButtonType.CANCEL, INSERT);
        dialog.getDialogPane().lookupButton(INSERT).getStyleClass().add("primary");

        dialog.getDialogPane().setContent(content(image, chapter, inFolder));
        Platform.runLater(caption::requestFocus);
    }

    /** Shows the dialog until the author picks Insert, Choose another or Cancel; empty if it is closed otherwise. */
    Optional<ButtonType> showAndWait() {

        return dialog.showAndWait();
    }

    /** The caption as the author left it. */
    String caption() {

        return caption.getText();
    }

    private Node content(MediaImage image, String chapter, boolean inFolder) {

        //preview
        ImageView view = new ImageView(new Image(image.file().toUri().toString(), PREVIEW_WIDTH, PREVIEW_HEIGHT, true,
                true, true));
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("insert-image-preview");
        frame.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        frame.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);

        //file
        Label name = new Label(image.name());
        name.getStyleClass().add("insert-image-name");
        Label details = new Label(ImageLabels.details(image));
        details.getStyleClass().add("insert-image-details");

        //caption
        Label captionLabel = new Label("Caption");
        captionLabel.setLabelFor(caption);
        caption.setText(ImageLabels.suggestedCaption(image.name()));
        caption.getStyleClass().add("insert-image-caption");

        VBox about = new VBox(name, details, captionLabel, caption);
        about.getStyleClass().add("insert-image-about");
        if (image.poor())
            about.getChildren().add(ImageLabels.qualityWarning(image.issues()));

        //where it goes
        Label hint = new Label((inFolder ? "Already in media/%s/; inserted after the caret's line."
                : "Copied to media/%s/ and inserted after the caret's line.").formatted(chapter));
        hint.getStyleClass().add("insert-image-hint");

        VBox content = new VBox(new HBox(frame, about), hint);
        content.getStyleClass().add("insert-image-content");
        return content;
    }
}
