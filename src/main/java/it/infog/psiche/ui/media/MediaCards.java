package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.ChapterImage;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The cards under the Media tab's preview, four to a row: each image's thumbnail and caption, no file name. A band on
 * top tells what needs the author: red for an image not in the chapter, which wins over anything else, amber for one
 * that will look poor in the exports. Clicking a card hands its image to the pane.
 */
final class MediaCards {

    static final String NOT_IN_CHAPTER = "NOT IN THE CHAPTER";

    private static final String LOW_QUALITY = "LOW QUALITY";
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final int COLUMNS = 4;
    private static final double GAP = 10;
    private static final double THUMBNAIL_HEIGHT = 84;

    private final GridPane grid = new GridPane(GAP, GAP);
    private final ScrollPane scroll = new ScrollPane(grid);
    private final DoubleBinding cardWidth;
    private final Consumer<ChapterImage> picked;

    /** @param picked given the image of a card the author clicks */
    MediaCards(Consumer<ChapterImage> picked) {

        this.picked = picked;

        // Four columns of a quarter each, whatever width the scroll pane shows; a thumbnail is as wide as its card.
        for (int column = 0; column < COLUMNS; column++) {
            ColumnConstraints quarter = new ColumnConstraints();
            quarter.setPercentWidth(100.0 / COLUMNS);
            grid.getColumnConstraints().add(quarter);
        }
        cardWidth = Bindings.createDoubleBinding(
                () -> Math.max(0, (grid.getWidth() - grid.snappedLeftInset() - grid.snappedRightInset()
                        - GAP * (COLUMNS - 1)) / COLUMNS),
                grid.widthProperty(), grid.paddingProperty());
        grid.getStyleClass().add("media-cards");

        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("media-cards-scroll");
    }

    Node node() {

        return scroll;
    }

    /**
     * Shows a card for each of {@code images}, in their order, {@code selected} marked.
     *
     * @param thumbnails the small picture of an image's file
     */
    void show(List<ChapterImage> images, Function<Path, Image> thumbnails, Path selected) {

        grid.getChildren().clear();
        for (int index = 0; index < images.size(); index++) {
            ChapterImage image = images.get(index);
            grid.add(card(image, thumbnails.apply(image.image().file()), image.image().file().equals(selected)),
                    index % COLUMNS, index / COLUMNS);
        }
    }

    private Node card(ChapterImage image, Image thumbnail, boolean selected) {

        //band
        Label band = new Label();
        band.getStyleClass().add("media-card-band");
        band.setMaxWidth(Double.MAX_VALUE);
        if (!image.inChapter()) {
            band.setText(NOT_IN_CHAPTER);
            band.getStyleClass().add("not-in-chapter");
        } else if (image.image().poor()) {
            band.setText(LOW_QUALITY);
            band.getStyleClass().add("low-quality");
        }

        //thumbnail
        ImageView view = new ImageView(thumbnail);
        view.setPreserveRatio(true);
        view.setFitHeight(THUMBNAIL_HEIGHT);
        view.fitWidthProperty().bind(cardWidth);
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("media-card-thumbnail");
        frame.setMinHeight(THUMBNAIL_HEIGHT);
        frame.setPrefHeight(THUMBNAIL_HEIGHT);

        //caption, or the file's name while the chapter gives it none
        Label caption = new Label(image.caption().isEmpty() ? image.image().name() : image.caption());
        caption.getStyleClass().add("media-card-caption");
        if (image.caption().isEmpty())
            caption.getStyleClass().add("unnamed");
        caption.setWrapText(true);

        Button card = new Button(null, new VBox(band, frame, caption));
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.getStyleClass().add("media-card");
        card.pseudoClassStateChanged(SELECTED, selected);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnAction(event -> picked.accept(image));

        return card;
    }
}
