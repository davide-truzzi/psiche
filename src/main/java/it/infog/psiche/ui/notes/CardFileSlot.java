package it.infog.psiche.ui.notes;

import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The place under a note's text for the file it carries: the file, or, while one is copied in, how far the copy has
 * come, until the note is given the file. In a note made small, it is cut at the card's edge, never past it.
 */
final class CardFileSlot extends VBox {

    private final Runnable open;
    private AttachmentView attachment;
    // Shown in the file's place while a file is copied into the note.
    private AttachmentView copying;

    /** @param open opens the file, on a double-click */
    CardFileSlot(Runnable open) {

        this.open = open;
        getStyleClass().add("note-file-slot");

        setMinHeight(0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
    }

    /** The file shown, if any, on disk or missing. */
    Optional<Path> file() {

        return Optional.ofNullable(attachment).map(AttachmentView::file);
    }

    /** Shows {@code file}, or nothing, in place of what was shown, a copy in progress included. */
    void setFile(Optional<Path> file) {

        attachment = file.map(path -> new AttachmentView(path, open)).orElse(null);
        copying = null;
        getChildren().setAll(file.isPresent() ? List.of(attachment) : List.of());
    }

    /** True if the file is shown as a picture, which the board draws for it. */
    boolean wantsPicture() {

        return attachment != null && attachment.hasPicture();
    }

    void showPicture(Image picture) {

        if (attachment != null)
            attachment.showPicture(picture);
    }

    /** Shows how far the copy of {@code name} into the note has come, from 0 to 1. */
    void showCopying(String name, double done) {

        if (copying == null) {
            copying = new AttachmentView(Path.of(name), () -> {});
            getChildren().setAll(copying);
        }
        copying.showProgress(name, done);
    }
}
