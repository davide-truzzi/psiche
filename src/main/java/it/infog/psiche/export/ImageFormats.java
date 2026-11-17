package it.infog.psiche.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which image files each export can show. The documents, PDF, ODT and DOCX, show any image Java can read; an EPUB only
 * the types every e-reader must show, JPEG, PNG, GIF, WebP and SVG. Only a file's start is read, never its pixels.
 */
public final class ImageFormats {

    /** Types an EPUB packs by their extension, without reading them, as Java cannot read an SVG, by media type. */
    public static final Map<String, String> EPUB_UNREAD = Map.of("webp", "image/webp", "svg", "image/svg+xml");
    /** Types an EPUB packs once Java has read their size, as Java names them. */
    public static final List<String> EPUB_READ = List.of("jpeg", "png", "gif");

    private static final Logger log = LoggerFactory.getLogger(ImageFormats.class);

    private ImageFormats() {
    }

    /** True if a PDF, ODT or DOCX can show the image in {@code file}: it is there, and Java can read it. */
    public static boolean fitsDocument(Path file) {

        return format(file).isPresent();
    }

    /** True if an EPUB can show the image in {@code file}: it is there, and of a type every e-reader shows. */
    public static boolean fitsEpub(Path file) {

        if (!Files.isRegularFile(file))
            return false;

        return EPUB_UNREAD.containsKey(extension(file)) || format(file).filter(EPUB_READ::contains).isPresent();
    }

    /** The extension of {@code file}'s name, in small letters; empty if it has none. */
    public static String extension(Path file) {

        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The type of the image in {@code file}, in small letters, as Java names it; empty if Java cannot read it. */
    private static Optional<String> format(Path file) {

        if (!Files.isRegularFile(file))
            return Optional.empty();

        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {

            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext())
                return Optional.empty();

            ImageReader reader = readers.next();
            try {
                return Optional.of(reader.getFormatName().toLowerCase(Locale.ROOT));
            } finally {
                reader.dispose();
            }

        } catch (IOException e) {
            log.warn("Cannot read the image {}", file, e);
            return Optional.empty();
        }
    }
}
