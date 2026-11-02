package it.infog.psiche.ui.notes.card;

import it.infog.psiche.notes.model.enums.FileKind;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file a note carries, at the bottom of its card: an image by a picture of it as wide as the card, a PDF by a small
 * picture of its first page beside its name and size, once the board has them; any other file by an icon of its kind,
 * its name and its size; a file gone from disk as missing; a
 * file still being copied by its progress. A double-click opens it.
 */
final class AttachmentView extends VBox {

    // A picture never makes the card taller than this, whatever the image's shape.
    private static final double PICTURE_HEIGHT = 160;
    private static final double COVER_HEIGHT = 100;

    private final Path file;
    private final FileKind kind;

    /** @param open opens the file in the system's default program */
    AttachmentView(Path file, Runnable open) {

        this.file = file;
        this.kind = FileKind.of(file);

        getStyleClass().add("note-attachment");
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && Files.exists(file))
                open.run();
        });
        showFile();
    }

    Path file() {

        return file;
    }

    /** True if the board draws this file as a picture: an image, or a PDF's first page. */
    boolean hasPicture() {

        return Files.exists(file) && (kind == FileKind.IMAGE || kind == FileKind.PDF);
    }

    /**
     * Shows {@code picture} in place of the icon: an image as wide as the card, up to {@value #PICTURE_HEIGHT} pixels
     * tall, above its name; a PDF's first page {@value #COVER_HEIGHT} pixels tall, beside its name and size.
     */
    void showPicture(Image picture) {

        ImageView view = new ImageView(picture);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        if (kind == FileKind.PDF) {
            view.setFitHeight(COVER_HEIGHT);
            getChildren().setAll(fileRow(view, fileLabel(shortName() + "\n" + size()), Pos.BOTTOM_LEFT));
            return;
        }

        view.fitWidthProperty().bind(widthProperty());
        view.setFitHeight(PICTURE_HEIGHT);
        getChildren().setAll(view, fileLabel(shortName()));
    }

    /** Shows how far the copy of {@code name} has come, from 0 to 1. */
    void showProgress(String name, double done) {

        Label label = fileLabel("%s · Copying… %d%%".formatted(FileLabels.shortened(name), Math.round(done * 100)));

        ProgressBar bar = new ProgressBar(done);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("note-copy-progress");

        getChildren().setAll(label, bar);
    }

    /** An icon of the file's kind, or of a missing file, beside its name and its size. */
    private void showFile() {

        boolean exists = Files.exists(file);
        Label name = fileLabel(shortName() + "\n" + (exists ? size() : "File missing"));
        if (!exists)
            name.getStyleClass().add("note-file-missing");

        getChildren().setAll(fileRow(exists ? FileIcons.of(kind) : FileIcons.missing(), name, Pos.CENTER_LEFT));
    }

    /** {@code picture}, an icon or a PDF's cover, beside {@code label}. */
    private static HBox fileRow(Node picture, Label label, Pos alignment) {

        HBox row = new HBox(picture, label);
        row.getStyleClass().add("note-file-row");
        row.setAlignment(alignment);
        return row;
    }

    private static Label fileLabel(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("note-file-name");
        return label;
    }

    private String shortName() {

        return FileLabels.shortened(file.getFileName().toString());
    }

    private String size() {

        try {
            return FileLabels.readableSize(Files.size(file));
        } catch (IOException e) {
            return "";
        }
    }
}
