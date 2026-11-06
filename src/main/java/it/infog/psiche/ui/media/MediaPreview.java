package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.notes.card.FileLabels;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import static it.infog.psiche.ui.common.Controls.button;

/**
 * The top of the Media tab: the selected image large, and beside it its caption, its file's name, format and sizes,
 * where the chapter shows it, the quality warning if it will look poor in the exports, and what the author can do with
 * it. Clicking the picture takes the author to the image in the text, as its card does.
 */
final class MediaPreview {

    private final MediaActions actions;
    private final Runnable reveal;
    private final ImageView picture = new ImageView();
    private final StackPane frame = new PictureFrame(picture);
    private final VBox about = new VBox();
    private final ScrollPane aboutScroll = new ScrollPane(about);
    private final HBox box = new HBox(frame, aboutScroll);
    private boolean frozen;

    /** @param reveal takes the author to the shown image in the text, or to its next place there */
    MediaPreview(MediaActions actions, Runnable reveal) {

        this.actions = actions;
        this.reveal = reveal;

        picture.setPreserveRatio(true);
        // The picture fits inside the frame's padding, as large as the frame is; at 0 it would take its own size.
        picture.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(1, frame.getWidth() - frame.snappedLeftInset() - frame.snappedRightInset()),
                frame.widthProperty(), frame.paddingProperty()));
        picture.fitHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(1, frame.getHeight() - frame.snappedTopInset() - frame.snappedBottomInset()),
                frame.heightProperty(), frame.paddingProperty()));
        frame.getStyleClass().add("media-preview-frame");
        frame.setOnMouseClicked(event -> reveal.run());
        HBox.setHgrow(frame, Priority.ALWAYS);

        // The details scroll when the preview is dragged shorter than they are.
        about.getStyleClass().add("media-preview-about");
        aboutScroll.setFitToWidth(true);
        aboutScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        aboutScroll.getStyleClass().add("media-preview-about-scroll");
        box.getStyleClass().add("media-preview");
        // Its height is the divider's alone: sized from what it shows, the split would move it for each image.
        box.setMinHeight(0);
        box.setPrefHeight(0);
    }

    Node node() {

        return box;
    }

    /** Disables the actions while {@code frozen}, as while the book is exported, and those shown later too. */
    void setFrozen(boolean frozen) {

        this.frozen = frozen;
        about.getChildren().stream().filter(Button.class::isInstance).forEach(button -> button.setDisable(frozen));
    }

    /**
     * Shows {@code image} with {@code large}, its picture.
     *
     * @param inText where the chapter shows it, as "Paragraph 6"
     */
    void show(ChapterImage image, Image large, String inText) {

        picture.setImage(large);
        MediaImage file = image.image();

        //caption and file
        Label caption = new Label(image.caption().isEmpty() ? "No caption" : image.caption());
        caption.getStyleClass().add("media-preview-caption");
        if (image.caption().isEmpty())
            caption.getStyleClass().add("unnamed");
        caption.setWrapText(true);
        Label name = new Label(file.name());
        name.getStyleClass().add("media-preview-name");
        about.getChildren().setAll(caption);
        if (!image.inChapter()) {
            Label tag = new Label(MediaCards.NOT_IN_CHAPTER);
            tag.getStyleClass().add("media-preview-tag");
            about.getChildren().add(tag);
        }
        about.getChildren().add(name);

        //details
        GridPane details = new GridPane();
        details.getStyleClass().add("media-preview-details");
        details.addRow(0, key("Format"), value(file.format().label()));
        details.addRow(1, key("Size"), value("%d × %d px".formatted(file.width(), file.height())));
        details.addRow(2, key("File"), value(FileLabels.readableSize(file.bytes())));
        Label where = value(inText);
        if (!image.inChapter())
            where.getStyleClass().add("not-in-chapter");
        details.addRow(3, key("In text"), where);
        about.getChildren().add(details);

        //warning and actions
        if (file.poor())
            about.getChildren().add(ImageLabels.qualityWarning(file.issues()));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        about.getChildren().add(spacer);
        about.getChildren().addAll(image.inChapter() ? usedActions(image) : unusedActions(image));
    }

    private Button[] usedActions(ChapterImage image) {

        return new Button[]{
                action("Remove from text", () -> actions.remove(image)),
                action("Edit caption", () -> actions.editCaption(image)),
                action("Replace image", () -> actions.replace(image))};
    }

    private Button[] unusedActions(ChapterImage image) {

        return new Button[]{action("Insert at the caret", () -> actions.insertAtCaret(image))};
    }

    private Button action(String text, Runnable run) {

        Button button = button(text, run);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setDisable(frozen);
        return button;
    }

    private static Label key(String text) {

        Label key = new Label(text);
        key.getStyleClass().add("media-preview-key");
        return key;
    }

    private static Label value(String text) {

        Label value = new Label(text);
        value.getStyleClass().add("media-preview-value");
        return value;
    }

    /**
     * The frame around the picture, whose size never follows the picture's: it takes the room the preview gives it, and
     * the picture fits in it. Sized from the picture, each new picture would resize the frame, then itself again.
     */
    private static final class PictureFrame extends StackPane {

        PictureFrame(Node picture) {

            super(picture);
        }

        @Override
        protected double computeMinWidth(double height) {

            return snappedLeftInset() + snappedRightInset();
        }

        @Override
        protected double computeMinHeight(double width) {

            return snappedTopInset() + snappedBottomInset();
        }

        @Override
        protected double computePrefWidth(double height) {

            return computeMinWidth(height);
        }

        @Override
        protected double computePrefHeight(double width) {

            return computeMinHeight(width);
        }
    }
}
