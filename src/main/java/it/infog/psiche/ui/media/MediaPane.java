package it.infog.psiche.ui.media;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.media.ChapterMedia;
import it.infog.psiche.media.ImageUses;
import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.common.WorkThread;
import it.infog.psiche.ui.notes.card.FileLabels;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static it.infog.psiche.ui.common.Controls.button;
import static it.infog.psiche.ui.common.Controls.hSpacer;

/**
 * The Media tab: the images in the open chapter's folder, the selected one large above the cards of all of them, and
 * at the foot how many there are and Add image. The folder is read on a thread of its own when a chapter opens and
 * when an image is copied in; which images the text shows is found again a moment after the author stops typing.
 * Clicking an image takes the author to it in the text, and to its next place on the next click.
 */
public final class MediaPane extends BorderPane {

    private static final String TITLE = "Media";
    // As the preview's: the text is read again once the author pauses, not on every keystroke.
    private static final Duration MATCH_DELAY = Duration.millis(500);
    // The sizes the pictures are read at, enough for a card and for the preview at a large window's size.
    private static final double THUMBNAIL_SIZE = 320;
    private static final double LARGE_SIZE = 1200;

    private final ChapterEditor editor;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final WorkThread thread = new WorkThread("Media");
    private final PauseTransition matchDelay = new PauseTransition(MATCH_DELAY);
    private final BooleanProperty showing = new SimpleBooleanProperty();
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(TITLE);
    private final ReadOnlyBooleanWrapper notInChapter = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper();
    private final Map<Path, Image> thumbnails = new HashMap<>();
    private final Map<Path, Image> pictures = new HashMap<>();

    private final MediaCards cards = new MediaCards(this::pick);
    private final MediaPreview preview;
    private final Label summary = new Label();
    private final HBox footer;
    private final SplitPane content;
    private final Label empty = new Label("No images in this chapter yet.\n"
            + "Right-click in the text and choose Insert image…, or Add image below.");

    private BookFolder book;
    private Chapter chapter;
    private List<MediaImage> images = List.of();
    private List<ChapterImage> shown = List.of();
    private Path selected;
    private int place;
    // How tall the preview is, in pixels, as last placed or dragged.
    private double previewHeight;

    /**
     * @param previewHeight     how tall the preview is, in pixels, as the author last dragged it
     * @param savePreviewHeight remembers it, each time the author drags it
     */
    public MediaPane(ChapterEditor editor, ImageInsertion insertion, StatusBar statusBar, ErrorReporter errors,
                     Supplier<Window> window, double previewHeight, DoubleConsumer savePreviewHeight) {

        this.editor = editor;
        this.statusBar = statusBar;
        this.errors = errors;
        preview = new MediaPreview(new MediaActions(editor, insertion, statusBar, window, () -> place),
                this::revealSelected);

        //layout
        summary.getStyleClass().add("media-summary");
        footer = new HBox(summary, hSpacer(), button("Add image", insertion::insert));
        footer.getStyleClass().add("media-footer");
        this.previewHeight = previewHeight;
        content = previewAndCards(savePreviewHeight);
        empty.getStyleClass().add("media-empty");
        empty.setWrapText(true);
        getStyleClass().add("media-pane");
        setBottom(footer);

        // listeners
        matchDelay.setOnFinished(event -> match());
        editor.textProperty().addListener((obs, old, text) -> {
            if (chapter != null)
                matchDelay.playFromStart();
        });
        showing.addListener((obs, was, is) -> {
            if (is)
                render();
        });
        insertion.setOnCopied(this::reload);
        render();
    }

    /** Whether the tab is on screen: the cards are drawn only while it is. Bind it to the tab's selection. */
    public BooleanProperty showingProperty() {

        return showing;
    }

    /** The tab's title: "Media (5)", the images in the chapter's folder, or "Media" with none. */
    public ReadOnlyStringProperty titleProperty() {

        return title.getReadOnlyProperty();
    }

    /** True while the chapter's folder holds an image the text does not show; the tab's title turns red then. */
    public ReadOnlyBooleanProperty notInChapterProperty() {

        return notInChapter.getReadOnlyProperty();
    }

    /** True while a chapter is open; the tab is disabled otherwise. */
    public ReadOnlyBooleanProperty availableProperty() {

        return available.getReadOnlyProperty();
    }

    /**
     * Keeps the chapter's images from changing while {@code frozen}, as while the book is exported: the actions and Add
     * image are disabled, the cards still show where an image is. The pane itself is not disabled, as its tab enables
     * and disables it as the tab is.
     */
    public void setFrozen(boolean frozen) {

        preview.setFrozen(frozen);
        footer.setDisable(frozen);
    }

    /** Shows the images of {@code chapter} of {@code book}, as it opens; none with {@code null}. */
    public void show(BookFolder book, Chapter chapter) {

        this.book = book;
        this.chapter = chapter;
        images = List.of();
        selected = null;
        place = 0;
        thumbnails.clear();
        pictures.clear();
        matchDelay.stop();
        available.set(chapter != null);
        match();
        reload();
    }

    /** Reads the chapter's folder again, as when an image was copied into it. */
    public void reload() {

        if (chapter == null)
            return;

        Chapter reading = chapter;
        ChapterMedia media = new ChapterMedia(book.mediaDir());
        thread.run(() -> media.images(reading.title()), read -> {
            if (reading.equals(chapter)) {
                images = read;
                Set<Path> files = read.stream().map(MediaImage::file).collect(Collectors.toSet());
                thumbnails.keySet().retainAll(files);
                pictures.keySet().retainAll(files);
                match();
            }
        }, failure -> errors.report("Cannot read the images of " + reading.title(), failure));
    }

    /** Stops the thread, interrupting a read that runs. */
    public void shutdown() {

        thread.shutdown();
    }

    /**
     * The preview above the cards, the divider between them dragged by the author: the preview keeps its height in
     * pixels as the window or the tab changes size, and whichever image it shows.
     */
    private SplitPane previewAndCards(DoubleConsumer savePreviewHeight) {

        SplitPane split = new SplitPane(preview.node(), cards.node());
        split.setOrientation(Orientation.VERTICAL);
        split.getStyleClass().add("media-split");
        SplitPane.setResizableWithParent(preview.node(), false);

        // Placed once the split first has a height, as a share of it; from then on the preview keeps its pixels.
        split.heightProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number old, Number total) {

                if (total.doubleValue() <= 0)
                    return;

                split.setDividerPositions(Math.clamp(previewHeight / total.doubleValue(), 0.1, 0.9));
                split.heightProperty().removeListener(this);
            }
        });
        split.setOnMouseReleased(event -> {
            double now = preview.node().getBoundsInParent().getHeight();
            if (Math.round(now) != Math.round(previewHeight)) {
                previewHeight = now;
                savePreviewHeight.accept(now);
            }
        });

        return split;
    }

    /** Finds which images the text shows, and where, then the title and, if on screen, the tab follow. */
    private void match() {

        shown = chapter == null || !editor.isOpen(chapter)
                ? List.of()
                : ImageUses.of(images, ImageLines.of(editor.text()), book.chaptersDir());
        title.set(shown.isEmpty() ? TITLE : "%s (%d)".formatted(TITLE, shown.size()));
        notInChapter.set(shown.stream().anyMatch(image -> !image.inChapter()));
        if (showing.get())
            render();
    }

    /** Draws the tab as {@link #match} last found it. */
    private void render() {

        if (shown.isEmpty()) {
            setCenter(chapter == null ? null : empty);
            summary.setText("");
            return;
        }

        ChapterImage current = current();
        Path file = current.image().file();
        cards.show(shown, path -> thumbnails.computeIfAbsent(path, read -> picture(read, THUMBNAIL_SIZE)), file);
        preview.show(current, pictures.computeIfAbsent(file, path -> picture(path, LARGE_SIZE)), inText(current));
        summary.setText(summary());
        setCenter(content);
    }

    /** Selects {@code image}, and takes the author to it in the text: to its next place if it was selected already. */
    private void pick(ChapterImage image) {

        boolean again = image.image().file().equals(selected);
        place = again && image.inChapter() ? (place + 1) % image.uses().size() : 0;
        selected = image.image().file();
        render();
        reveal(image);
    }

    /** Takes the author to the shown image's place in the text, or the next one, as clicking its card again does. */
    private void revealSelected() {

        if (!shown.isEmpty())
            pick(current());
    }

    private void reveal(ChapterImage image) {

        if (!image.inChapter()) {
            statusBar.info("%s is in the chapter's images, not in its text".formatted(image.image().name()));
            return;
        }

        ImageLine line = image.uses().get(Math.min(place, image.uses().size() - 1));
        editor.reveal(line.range().start());
        int count = image.uses().size();
        String paragraph = "Paragraph " + paragraph(line);
        statusBar.info(count == 1 ? paragraph
                : "%s: place %d of %d, click again for the next".formatted(paragraph, place + 1, count));
    }

    /** The image the author picked, or the first while none is picked, or the one picked is gone. */
    private ChapterImage current() {

        return shown.stream().filter(image -> image.image().file().equals(selected)).findFirst()
                .orElse(shown.getFirst());
    }

    /** Where the text shows {@code image}: "Paragraph 6", "Paragraphs 10, 14", or "Not in the text". */
    private String inText(ChapterImage image) {

        if (!image.inChapter())
            return "Not in the text";

        String numbers = image.uses().stream().map(line -> String.valueOf(paragraph(line)))
                .collect(Collectors.joining(", "));
        return (image.uses().size() == 1 ? "Paragraph " : "Paragraphs ") + numbers;
    }

    /** The number of the editor's paragraph, counted from 1, where {@code line} starts. */
    private int paragraph(ImageLine line) {

        String text = editor.text();
        int end = Math.min(line.range().start(), text.length());
        return (int) text.substring(0, end).chars().filter(character -> character == '\n').count() + 1;
    }

    /** "5 images · 10.8 MB · 2 not in the chapter". */
    private String summary() {

        long bytes = shown.stream().mapToLong(image -> image.image().bytes()).sum();
        long notShown = shown.stream().filter(image -> !image.inChapter()).count();
        String count = shown.size() == 1 ? "1 image" : shown.size() + " images";
        String text = "%s · %s".formatted(count, FileLabels.readableSize(bytes));

        return notShown == 0 ? text : "%s · %d not in the chapter".formatted(text, notShown);
    }

    /** The picture in {@code file}, read in the background at {@code size} pixels at most on its longer side. */
    private static Image picture(Path file, double size) {

        return new Image(file.toUri().toString(), size, size, true, true, true);
    }
}
