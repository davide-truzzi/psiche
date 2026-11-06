package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.QualityIssue;
import it.infog.psiche.ui.common.FileLabels;
import it.infog.psiche.ui.common.Icons;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Set;

/**
 * How an image is told about, in the Insert image dialog, the Media tab and the lists of images: its details, its
 * quality warning, and a small card.
 */
final class ImageLabels {

    private static final String WARNING_HEADING = "Will look poor in the exports";
    private static final String LOW_QUALITY = "Low quality";
    // A small card's picture bounds, read at that size, in the background.
    private static final double SMALL_WIDTH = 120;
    private static final double SMALL_HEIGHT = 80;

    private ImageLabels() {
    }

    /** The image's format and sizes, as "PNG · 2400 × 1600 px · 1.2 MB". */
    static String details(MediaImage image) {

        return "%s · %d × %d px · %s".formatted(image.format().label(), image.width(), image.height(),
                FileLabels.readableSize(image.bytes()));
    }

    /** A caption to start from, made of the file's name: {@code coast_map-2.png} gives "coast map 2". */
    static String suggestedCaption(String fileName) {

        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;

        return name.replaceAll("[-_]+", " ").strip();
    }

    /** An image as a small card: its picture, read in the background, its name under it, and "Low quality" if so. */
    static VBox smallCard(MediaImage image) {

        ImageView picture = new ImageView(new Image(image.file().toUri().toString(), SMALL_WIDTH, SMALL_HEIGHT, true,
                true, true));
        StackPane frame = new StackPane(picture);
        frame.getStyleClass().add("small-image-picture");

        Label name = new Label(FileLabels.shortened(image.name()));
        name.getStyleClass().add("small-image-name");

        VBox card = new VBox(frame, name);
        if (image.poor()) {
            Label warning = new Label(LOW_QUALITY);
            warning.getStyleClass().add("small-image-warning");
            card.getChildren().add(warning);
        }
        card.getStyleClass().add("small-image");
        return card;
    }

    /** The amber box listing what will make an image look poor in the exports, each in plain words. */
    static Node qualityWarning(Set<QualityIssue> issues) {

        //heading
        Node icon = Icons.shape("warning");
        icon.getStyleClass().add("quality-warning-icon");
        Label heading = new Label(WARNING_HEADING, icon);
        heading.getStyleClass().add("quality-warning-heading");
        heading.setWrapText(true);

        //issues
        VBox box = new VBox(heading);
        for (QualityIssue issue : issues) {
            Label title = new Label(issue.title());
            title.getStyleClass().add("quality-warning-title");
            Label detail = new Label(issue.detail());
            detail.getStyleClass().add("quality-warning-detail");
            box.getChildren().add(new VBox(title, detail));
        }

        box.getStyleClass().add("quality-warning");
        return box;
    }
}
