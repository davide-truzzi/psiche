package it.infog.psiche.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.ImageFormat;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/** Reads what the Media tab and the Insert image dialog tell about an image file, without decoding its pixels. */
public final class ImageFiles {

    private ImageFiles() {
    }

    /**
     * The image in {@code file}: its format, read from its content whatever its name says, its size and what will
     * make it look poor in the exports.
     *
     * @throws IllegalArgumentException if it is not a JPEG or PNG image
     * @throws UncheckedIOException     if it cannot be read
     */
    public static MediaImage read(Path file) {

        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext())
                throw notAnImage(file);

            ImageReader reader = readers.next();
            try {
                ImageFormat format = ImageFormat.ofReader(reader.getFormatName()).orElseThrow(() -> notAnImage(file));
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                return new MediaImage(file, format, width, height, Files.size(file),
                        ImageQuality.of(file, format, width, height));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the image " + file, e);
        }
    }

    /** The image in {@code file}; empty if it is not a JPEG or PNG image, or cannot be read. */
    static Optional<MediaImage> tryRead(Path file) {

        try {
            return Optional.of(read(file));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    private static IllegalArgumentException notAnImage(Path file) {

        return new IllegalArgumentException(file.getFileName() + " is not a JPEG or PNG image");
    }
}
