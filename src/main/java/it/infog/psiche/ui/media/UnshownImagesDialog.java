package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.common.Dialogs;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * The first step of Insert image while the chapter's images include some its text does not show: those images as small
 * cards, one of which the author picks to insert, or Choose another file… for a new one. One place puts both into the
 * text.
 */
final class UnshownImagesDialog {

    static final ButtonType CHOOSE_FILE = new ButtonType("Choose another file…", ButtonData.LEFT);

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private MediaImage picked;

    /** @param images the chapter's images its text does not show, by name */
    UnshownImagesDialog(Window owner, List<MediaImage> images) {

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Insert image");
        dialog.getDialogPane().getButtonTypes().setAll(CHOOSE_FILE, ButtonType.CANCEL);

        Label intro = new Label(images.size() == 1
                ? "This image is in the chapter's images, but not in its text yet:"
                : "These images are in the chapter's images, but not in its text yet:");
        intro.getStyleClass().add("unshown-images-intro");

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("unshown-images-cards");
        for (MediaImage image : images)
            cards.getChildren().add(pickable(image));

        VBox content = new VBox(intro, cards);
        content.getStyleClass().add("unshown-images");
        dialog.getDialogPane().setContent(content);
    }

    /**
     * Shows the dialog until the author picks an image, asks for another file, or cancels.
     *
     * @return the image picked; empty for another file, as {@link #wantsFile} then says, or for Cancel
     */
    Optional<MediaImage> showAndWait() {

        dialog.showAndWait();
        return Optional.ofNullable(picked);
    }

    /** True if the author asked for another file rather than one of the images. */
    boolean wantsFile() {

        return CHOOSE_FILE.equals(dialog.getResult());
    }

    /** {@code image}'s card as a button, which picks it and closes the dialog. */
    private Button pickable(MediaImage image) {

        Button button = new Button();
        button.setGraphic(ImageLabels.smallCard(image));
        button.getStyleClass().add("unshown-image");
        button.setOnAction(event -> {
            picked = image;
            dialog.setResult(ButtonType.OK);
            dialog.close();
        });
        return button;
    }
}
