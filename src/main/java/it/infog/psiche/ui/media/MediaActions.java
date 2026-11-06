package it.infog.psiche.ui.media;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.media.ImageUses;
import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.StatusBar;

import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * What the Media tab's buttons do to the open chapter: take an image out of the text, give it another caption, show a
 * better file in its place, or put an image of the folder into the text at the caret. Each change to the text is one
 * change Undo reverts; the images' paragraphs are found in the text as it is now, not as the tab last read it.
 */
final class MediaActions {

    private final ChapterEditor editor;
    private final ImageInsertion insertion;
    private final StatusBar statusBar;
    private final Supplier<Window> window;
    private final IntSupplier place;

    /** @param place which of the image's places in the text the author was last taken to, counted from 0 */
    MediaActions(ChapterEditor editor, ImageInsertion insertion, StatusBar statusBar, Supplier<Window> window,
                 IntSupplier place) {

        this.editor = editor;
        this.insertion = insertion;
        this.statusBar = statusBar;
        this.window = window;
        this.place = place;
    }

    /**
     * Takes {@code image} out of the text at the place the author was last taken to, with the blank line it leaves; the
     * file stays in the chapter's folder.
     */
    void remove(ChapterImage image) {

        Optional<Chapter> chapter = editor.chapter();
        Optional<BookFolder> book = editor.book();
        if (chapter.isEmpty() || book.isEmpty())
            return;

        List<ImageLine> lines = ImageUses.lines(editor.text(), book.get().chaptersDir(), image.image().file());
        if (lines.isEmpty())
            return;

        ImageLine line = lines.get(Math.min(place.getAsInt(), lines.size() - 1));
        if (editor.change(chapter.get(), text -> ImageLines.without(text, line)))
            statusBar.info(lines.size() == 1
                    ? "Took %s out of the text; the file stays in the chapter's images".formatted(image.image().name())
                    : "Took one of the %d places of %s out of the text".formatted(lines.size(), image.image().name()));
    }

    /** Asks for another caption for {@code image}, and gives it to every place the text shows it. */
    void editCaption(ChapterImage image) {

        Optional<Chapter> chapter = editor.chapter();
        Optional<BookFolder> book = editor.book();
        if (chapter.isEmpty() || book.isEmpty())
            return;

        TextInputDialog dialog = new TextInputDialog(image.caption());
        Dialogs.ownAndStyle(dialog, window.get());
        dialog.setHeaderText("Edit caption");
        dialog.setContentText("Caption");
        dialog.getEditor().getStyleClass().add("insert-image-caption");

        Path chaptersDir = book.get().chaptersDir();
        dialog.showAndWait().ifPresent(caption -> {
            if (editor.change(chapter.get(),
                    text -> ImageUses.withCaption(text, chaptersDir, image.image().file(), caption)))
                statusBar.info("Caption of %s changed".formatted(image.image().name()));
        });
    }

    /** Asks for a better file to show wherever the text shows {@code image}; see {@link ImageInsertion#replace}. */
    void replace(ChapterImage image) {

        insertion.replace(image.image());
    }

    /**
     * Puts {@code image}, which the text does not show, after the caret's line, with a caption made of its file's name
     * for the author to change.
     */
    void insertAtCaret(ChapterImage image) {

        Optional<Chapter> chapter = editor.chapter();
        if (chapter.isEmpty())
            return;

        String name = image.image().name();
        String line = ImageLines.markdown(ImageLabels.suggestedCaption(name),
                ImageLines.destination(chapter.get().title(), name));
        if (editor.insertImage(chapter.get(), line))
            statusBar.info("Inserted %s after the caret's line; Edit caption gives it its own".formatted(name));
    }
}
