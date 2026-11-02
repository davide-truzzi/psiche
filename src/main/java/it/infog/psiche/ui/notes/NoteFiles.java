package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.enums.FileKind;
import it.infog.psiche.ui.notes.card.NoteCard;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The files notes carry, for the board: copying them into notes, on a thread of their own so a long copy never holds
 * up the notes being saved, with its progress on the card; an image pasted from the clipboard, saved as a PNG first;
 * the pictures of images and PDFs, made in the background, the PDF's from its first page; and opening a file, or its
 * folder, in the system's programs.
 */
final class NoteFiles {

    // Pictures are made twice as wide as the widest note, so they stay sharp on screens that scale the window up.
    private static final int PICTURE_WIDTH = 1200;
    private static final DateTimeFormatter PASTED_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExecutorService copies =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("note-copies").daemon().factory());
    private final ExecutorService pictures =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("note-pictures").daemon().factory());
    private final Consumer<String> browser;

    /** @param browser opens an address, here a file's or a folder's, in the system's default program */
    NoteFiles(Consumer<String> browser) {

        this.browser = browser;
    }

    /**
     * Copies {@code file} into the note of {@code card}, showing the copy's progress on the card, then hands the file,
     * as the note now carries it, to {@code attached}; or the failure to {@code failed}. Both run on the FX thread.
     */
    void attach(NoteStore store, String chapter, NoteCard card, Path file, Consumer<Path> attached,
                Consumer<Throwable> failed) {

        copyIn(store, chapter, card, file.getFileName().toString(), () -> file, false, attached, failed);
    }

    /** Saves {@code image}, from the clipboard, as a PNG and copies it into the note of {@code card}, like a file. */
    void attachImage(NoteStore store, String chapter, NoteCard card, Image image, Consumer<Path> attached,
                     Consumer<Throwable> failed) {

        // The image's pixels are read here, on the FX thread; the PNG is written on the copies thread.
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        String name = "pasted-image-" + LocalDateTime.now().format(PASTED_TIME) + ".png";

        copyIn(store, chapter, card, name, () -> {
            BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            Path file = Files.createTempDirectory("psiche-paste").resolve(name);
            ImageIO.write(png, "png", file.toFile());
            return file;
        }, true, attached, failed);
    }

    /**
     * Copies the file {@code source} gives into the note, on the copies thread.
     *
     * @param temporary true if the file was made only to be copied, and goes once copied
     */
    private void copyIn(NoteStore store, String chapter, NoteCard card, String name, Callable<Path> source,
                        boolean temporary, Consumer<Path> attached, Consumer<Throwable> failed) {

        String id = card.id();
        card.showCopying(name, 0);
        AtomicLong shownPercent = new AtomicLong();

        copies.execute(() -> {
            Path file = null;
            try {
                file = source.call();
                Path copy = store.attach(chapter, id, file, (bytes, total) -> {
                    // The card follows the copy a percent at a time, not at every block read.
                    long percent = total == 0 ? 100 : bytes * 100 / total;
                    if (shownPercent.getAndSet(percent) != percent)
                        Platform.runLater(() -> card.showCopying(name, percent / 100.0));
                });
                Platform.runLater(() -> attached.accept(copy));
            } catch (Exception e) {
                Platform.runLater(() -> failed.accept(e));
            } finally {
                if (temporary && file != null)
                    deleteQuietly(file);
            }
        });
    }

    /**
     * Gives the card the picture of its file, if the board draws one: a PDF's first page, made on the pictures thread;
     * an image read by JavaFX in the background, or, for those it cannot read, such as WebP and TIFF, by ImageIO on
     * the pictures thread. A file that cannot be read keeps its icon.
     */
    void showPicture(NoteCard card) {

        if (!card.wantsPicture())
            return;

        Path file = card.attachment().orElseThrow();
        if (FileKind.of(file) == FileKind.PDF) {
            makePicture(card, () -> pdfCover(file));
            return;
        }

        Image image = new Image(file.toUri().toString(), PICTURE_WIDTH, 0, true, true, true);
        image.progressProperty().addListener((obs, old, progress) -> {
            if (progress.doubleValue() >= 1 && !image.isError())
                card.showPicture(image);
        });
        image.errorProperty().addListener((obs, was, error) -> {
            if (error)
                makePicture(card, () -> imageIoPicture(file));
        });
    }

    void open(Path file) {

        browser.accept(file.toUri().toString());
    }

    void showFolder(Path file) {

        browser.accept(file.getParent().toUri().toString());
    }

    /** Stops the threads; a copy cut short leaves only a temporary file, which notes never show. */
    void shutdown() {

        copies.shutdownNow();
        pictures.shutdownNow();
    }

    /** Makes the card's picture with {@code maker} on the pictures thread; if it fails, the card keeps its icon. */
    private void makePicture(NoteCard card, Callable<Image> maker) {

        pictures.execute(() -> {
            try {
                Image picture = maker.call();
                Platform.runLater(() -> card.showPicture(picture));
            } catch (Exception e) {
                // The file cannot be read: the card keeps its icon.
            }
        });
    }

    /** {@code file}, an image JavaFX cannot read, read by ImageIO, at most {@value #PICTURE_WIDTH} pixels wide. */
    private static Image imageIoPicture(Path file) throws IOException {

        BufferedImage read = ImageIO.read(file.toFile());
        if (read == null)
            throw new IOException("No reader for " + file);
        if (read.getWidth() <= PICTURE_WIDTH)
            return toImage(read);

        int height = Math.max(1, (int) Math.round((double) read.getHeight() * PICTURE_WIDTH / read.getWidth()));
        BufferedImage scaled = new BufferedImage(PICTURE_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(read, 0, 0, PICTURE_WIDTH, height, null);
        graphics.dispose();
        return toImage(scaled);
    }

    /** The first page of {@code pdf}, {@value #PICTURE_WIDTH} pixels wide. */
    private static Image pdfCover(Path pdf) throws IOException {

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            float scale = PICTURE_WIDTH / 2f / document.getPage(0).getMediaBox().getWidth();
            BufferedImage page = new PDFRenderer(document).renderImage(0, scale);
            return toImage(page);
        }
    }

    /** {@code picture} as a JavaFX image, pixel by pixel: the app has no {@code javafx.swing} to convert it. */
    private static Image toImage(BufferedImage picture) {

        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] pixels = picture.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    /** Deletes a file made only to be copied, with the folder made for it. */
    private static void deleteQuietly(Path file) {

        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        } catch (IOException e) {
            // A leftover in the system's temporary folder, which the system clears.
        }
    }
}
