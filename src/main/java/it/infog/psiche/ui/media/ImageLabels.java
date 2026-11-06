package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.QualityIssue;
import it.infog.psiche.ui.common.Icons;
import it.infog.psiche.ui.notes.card.FileLabels;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Set;

/** How an image is told about, in the Insert image dialog and the Media tab: its details and its quality warning. */
final class ImageLabels {

    private static final String WARNING_HEADING = "Will look poor in the exports";

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

    /** The amber box listing what will make an image look poor in the exports, each in plain words. */
    static Node qualityWarning(Set<QualityIssue> issues) {

        //heading
        Node icon = Icons.shape("warning");
        icon.getStyleClass().add("quality-warning-icon");
        Label heading = new Label(WARNING_HEADING, icon);
        heading.getStyleClass().add("quality-warning-heading");

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
