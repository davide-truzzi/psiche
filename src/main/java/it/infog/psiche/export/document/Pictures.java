package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.markdown.ImageLines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The images a chapter shows, found as the chapter is rendered. Each one is read once, to learn its format and size,
 * and named for its entry in the document; an image used twice is packed once.
 */
public final class Pictures {

    private static final Logger log = LoggerFactory.getLogger(Pictures.class);

    // An image is drawn at 96 pixels to the inch, as a browser shows it, and shrunk if it would not fit the text area
    // of an A4 page with 2.5cm margins.
    private static final double CM_PER_PIXEL = 2.54 / 96;
    private static final double MAX_WIDTH_CM = 16;
    private static final double MAX_HEIGHT_CM = 22;
    // The cover leaves room below it for the title page's title, subtitle and authors, as in the PDF.
    private static final double COVER_MAX_WIDTH_CM = 12;
    private static final double COVER_MAX_HEIGHT_CM = 13;

    private final Path baseDirectory;
    private final String entryPrefix;
    private final Map<Path, Picture> found = new LinkedHashMap<>();

    /**
     * @param baseDirectory the directory image links are relative to, the book's chapters
     * @param entryPrefix   where the format packs its images, and how it names them, such as {@code Pictures/image};
     *                      each one adds its number and extension
     */
    public Pictures(Path baseDirectory, String entryPrefix) {

        this.baseDirectory = baseDirectory;
        this.entryPrefix = entryPrefix;
    }

    /**
     * The image a chapter links to as {@code destination}, such as {@code ../media/map.png}. Empty if it is not a local
     * file, is missing, or is in a format Java cannot read; the chapter is exported without it, as the PDF is.
     */
    Optional<Picture> find(String destination) {

        return localFile(destination).flatMap(file -> picture(file, MAX_WIDTH_CM, MAX_HEIGHT_CM));
    }

    /** The book's cover, for the title page, smaller than the text area so the title fits below it. */
    Optional<Picture> cover(Path file) {

        return picture(file.toAbsolutePath().normalize(), COVER_MAX_WIDTH_CM, COVER_MAX_HEIGHT_CM);
    }

    /** Every image found so far, in the order the chapter shows them. */
    public Collection<Picture> all() {

        return List.copyOf(found.values());
    }

    /**
     * The image in {@code file}, read the first time it is asked for. An image shown twice keeps the size it was first
     * read at, and is packed once.
     */
    private Optional<Picture> picture(Path file, double maxWidth, double maxHeight) {

        if (found.containsKey(file))
            return Optional.of(found.get(file));

        Optional<Picture> picture = read(file, entryPrefix + (found.size() + 1), maxWidth, maxHeight);
        picture.ifPresent(read -> found.put(read.file(), read));

        return picture;
    }

    /** The file {@code destination} names, resolved against the chapters; empty for a web address. */
    private Optional<Path> localFile(String destination) {

        Optional<Path> file = ImageLines.file(baseDirectory, destination);
        if (file.isEmpty() && !ImageLines.isWebAddress(destination))
            log.warn("Leaving out the image {}: not a file", destination);

        return file;
    }

    /**
     * Reads the format and size of the image in {@code file}, without decoding its pixels.
     *
     * @param entry    where to pack it in the document, without the extension, which follows the format
     * @param maxWidth the widest it may be drawn, in centimetres; {@code maxHeight} likewise
     */
    private static Optional<Picture> read(Path file, String entry, double maxWidth, double maxHeight) {

        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {

            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                log.warn("Leaving out the image {}: missing or not an image", file);
                return Optional.empty();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return Optional.of(measured(file, entry, reader, maxWidth, maxHeight));
            } finally {
                reader.dispose();
            }

        } catch (IOException e) {
            log.warn("Leaving out the image {}", file, e);
            return Optional.empty();
        }
    }

    /** The image {@code reader} reads, at its own size, shrunk to fit {@code maxWidth} by {@code maxHeight}. */
    private static Picture measured(Path file, String entry, ImageReader reader, double maxWidth, double maxHeight)
            throws IOException {

        double width = reader.getWidth(0) * CM_PER_PIXEL;
        double height = reader.getHeight(0) * CM_PER_PIXEL;
        double scale = Math.min(1, Math.min(maxWidth / width, maxHeight / height));

        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String extension = format.equals("jpeg") ? "jpg" : format;

        return new Picture(file, entry + "." + extension, "image/" + format, width * scale, height * scale);
    }
}
