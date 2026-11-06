package it.infog.psiche.ui.media;

import it.infog.psiche.media.model.DetachedGroup;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.common.DateFormats;
import it.infog.psiche.ui.common.DetachedDialog;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.notes.card.FileLabels;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.button;

/**
 * Detached images: the groups of images left without a chapter, the most recently detached first, each with the
 * chapter it belonged to, why and when it was left, a small picture of each image with its name, and the buttons to
 * put it back in a chapter's images or to delete it for good. Deleting for good is asked first: images are not in Git,
 * so nothing brings them back. See {@link DetachedDialog} for how the dialog behaves.
 */
final class DetachedImagesDialog {

    // The pictures' bounds on the cards, read at that size, in the background.
    private static final double PICTURE_WIDTH = 120;
    private static final double PICTURE_HEIGHT = 80;

    private final DetachedDialog dialog;
    private final List<String> chapters;
    private BiConsumer<DetachedGroup, String> restore = (group, chapter) -> {};
    private Consumer<DetachedGroup> delete = group -> {};

    /** @param chapters the book's chapters, by name, which images can be restored to */
    DetachedImagesDialog(Window owner, List<String> chapters) {

        this.chapters = chapters;
        dialog = new DetachedDialog(owner, "Detached images",
                "Images are not in Git: what is deleted from here is gone for good.", "No detached images.");
    }

    /** Runs {@code restore} to put a group's images in the chapter named; then hand the groups left to showGroups. */
    void setOnRestore(BiConsumer<DetachedGroup, String> restore) {

        this.restore = restore;
    }

    /** Runs {@code delete} to delete a group's images for good, once confirmed; then hand the groups to showGroups. */
    void setOnDelete(Consumer<DetachedGroup> delete) {

        this.delete = delete;
    }

    /** Shows {@code groups} and waits until the author closes the dialog. */
    void showAndWait(List<DetachedGroup> groups) {

        showGroups(groups);
        dialog.showAndWait();
    }

    /** Shows {@code groups} in place of those shown, and lets the author act on them again. */
    void showGroups(List<DetachedGroup> groups) {

        dialog.setGroups(groups.stream().map(group -> (Node) groupView(group)).toList());
    }

    void close() {

        dialog.close();
    }

    /**
     * Asks, as a deleted chapter is restored, whether the images it had go back with it.
     *
     * @return true for Restore images, the default
     */
    static boolean askRestore(Window owner, DetachedGroup group) {

        ButtonType chapterOnly = new ButtonType("Chapter only", ButtonData.CANCEL_CLOSE);
        ButtonType restoreImages = new ButtonType("Restore images", ButtonData.OK_DONE);
        String when = group.deletedAt()
                .map(at -> ", on %s at %s".formatted(DateFormats.DAY.format(at), DateFormats.TIME.format(at)))
                .orElse("");

        return Dialogs.choose(owner, "Restore its images too?",
                        "%s had %s when it was deleted%s.".formatted(group.chapter(), count(group), when),
                        chapterOnly, restoreImages)
                .filter(restoreImages::equals)
                .isPresent();
    }

    /** "1 image", or "3 images". */
    static String count(DetachedGroup group) {

        int images = group.images().size();
        return images == 1 ? "1 image" : images + " images";
    }

    /** The chapter above the cause and date, the buttons beside them, and below them a small card for each image. */
    private VBox groupView(DetachedGroup group) {

        String when = group.deletedAt().map(at -> " · " + DateFormats.DAY_AND_TIME.format(at)).orElse("");
        return DetachedDialog.group(group.chapter(), group.cause().label() + when,
                group.images().stream().map(image -> (Node) card(image)).toList(),
                button("Restore to chapter", () -> restore(group)),
                button("Delete permanently", () -> delete(group)));
    }

    /** An image as a small card: its picture, read in the background, and its name under it. */
    private static VBox card(MediaImage image) {

        ImageView picture = new ImageView(new Image(image.file().toUri().toString(), PICTURE_WIDTH, PICTURE_HEIGHT,
                true, true, true));
        StackPane frame = new StackPane(picture);
        frame.getStyleClass().add("detached-image-picture");

        Label name = new Label(FileLabels.shortened(image.name()));
        name.getStyleClass().add("detached-image-name");

        VBox card = new VBox(frame, name);
        card.getStyleClass().add("detached-image");
        return card;
    }

    // Restoring and deleting

    /** Asks which chapter the images go to, their own chapter first if the book still has it. */
    private void restore(DetachedGroup group) {

        if (chapters.isEmpty()) {
            Dialogs.inform(dialog.window(), "No chapter to restore to",
                    "Create a chapter first, then restore the images to it.");
            return;
        }

        String suggested = chapters.contains(group.chapter()) ? group.chapter() : chapters.getFirst();
        Optional<String> chapter = Dialogs.chooseChapter(dialog.window(),
                "Restore %s to a chapter".formatted(count(group)), chapters, suggested, "Restore");
        if (chapter.isEmpty())
            return;

        dialog.setBusy();
        restore.accept(group, chapter.get());
    }

    private void delete(DetachedGroup group) {

        ButtonType deleteButton = new ButtonType("Delete", ButtonData.OK_DONE);
        String what = group.images().size() == 1
                ? "The image of %s is not in Git, so it can't be recovered."
                : "The images of %s are not in Git, so they can't be recovered.";
        Optional<ButtonType> choice = Dialogs.choose(dialog.window(), "Delete %s for good?".formatted(count(group)),
                what.formatted(group.chapter()), ButtonType.CANCEL, deleteButton);
        if (choice.filter(deleteButton::equals).isEmpty())
            return;

        dialog.setBusy();
        delete.accept(group);
    }
}
