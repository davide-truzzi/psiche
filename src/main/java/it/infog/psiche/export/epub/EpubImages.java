package it.infog.psiche.export.epub;

import it.infog.psiche.export.ImageLinks;
import it.infog.psiche.export.epub.model.EpubImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The images an EPUB packs: the cover and those the chapters show, each packed once however often it is shown.
 * <p>
 * Only the image types every e-reader must show are packed: JPEG, PNG, GIF, WebP and SVG. A JPEG or PNG larger than
 * {@value #MAX_PIXELS} pixels is packed scaled down, as Apple Books limits images to about that; others are packed as
 * they are. Images that are missing or of another type are left out, and listed by {@link #leftOut}.
 */
final class EpubImages implements ImageLinks {

    static final int MAX_PIXELS = 4_000_000;

    private static final Logger log = LoggerFactory.getLogger(EpubImages.class);

    // Types Java cannot read, packed by their extension without being measured.
    private static final Map<String, String> UNMEASURED = Map.of("webp", "image/webp", "svg", "image/svg+xml");

    private final Path baseDirectory;
    private final Map<Path, EpubImage> packed = new LinkedHashMap<>();
    private final List<String> leftOut = new ArrayList<>();

    /** @param baseDirectory the directory the chapters' image links are relative to, the book's chapters */
    EpubImages(Path baseDirectory) {

        this.baseDirectory = baseDirectory;
    }

    /** The cover, packed as {@code images/cover.jpg} or {@code .png}; empty unless it is a JPEG or PNG Java reads. */
    Optional<EpubImage> cover(Path file) {

        Optional<EpubImage> cover = measure(file.toAbsolutePath().normalize(), "images/cover")
                .filter(image -> image.mediaType().equals("image/jpeg") || image.mediaType().equals("image/png"));
        cover.ifPresent(image -> packed.put(image.source(), image));

        return cover;
    }

    /** The address, from a chapter's page, of the image the chapter links as {@code destination}. */
    @Override
    public Optional<String> href(String destination) {

        return image(destination).map(image -> "../" + image.path());
    }

    /** The image a chapter links as {@code destination}, packed the first time it is asked for. */
    Optional<EpubImage> image(String destination) {

        Optional<Path> file = localFile(destination);
        if (file.isEmpty()) {
            leftOut.add(destination + " (not a file of the book)");
            return Optional.empty();
        }

        if (packed.containsKey(file.get()))
            return Optional.of(packed.get(file.get()));

        Optional<EpubImage> image = measure(file.get(), "images/image-" + (packed.size() + 1));
        if (image.isEmpty())
            leftOut.add(destination + (Files.isRegularFile(file.get())
                    ? " (not a JPEG, PNG, GIF, WebP or SVG image)"
                    : " (not found)"));
        image.ifPresent(found -> packed.put(found.source(), found));

        return image;
    }

    /** Every image packed so far, the cover included. */
    List<EpubImage> all() {

        return List.copyOf(packed.values());
    }

    /** The images the chapters link that could not be packed, each with the reason. */
    List<String> leftOut() {

        return List.copyOf(leftOut);
    }

    /** The image's bytes as packed: the file itself, or a smaller copy of it. */
    static byte[] bytes(EpubImage image) throws IOException {

        if (!image.scaled())
            return Files.readAllBytes(image.source());

        BufferedImage source = ImageIO.read(image.source().toFile());
        boolean jpeg = image.mediaType().equals("image/jpeg");
        BufferedImage scaled = new BufferedImage(image.width(), image.height(),
                jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, image.width(), image.height(), null);
        graphics.dispose();

        return jpeg ? jpeg(scaled) : png(scaled);
    }

    /** The file {@code destination} names, resolved against the chapters; empty for a web address. */
    private Optional<Path> localFile(String destination) {

        try {
            URI uri = baseDirectory.toUri().resolve(destination);
            return "file".equals(uri.getScheme()) ? Optional.of(Path.of(uri).normalize()) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the type and size of the image in {@code file}, without decoding its pixels.
     *
     * @param name where to pack it, without the extension, which follows the type
     * @return empty if the file is missing or not of a type every e-reader shows
     */
    private static Optional<EpubImage> measure(Path file, String name) {

        if (!Files.isRegularFile(file))
            return Optional.empty();

        String extension = extension(file);
        if (UNMEASURED.containsKey(extension))
            return Optional.of(new EpubImage(file, name + "." + extension, UNMEASURED.get(extension), 0, 0, false));

        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {

            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext())
                return Optional.empty();

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("jpeg", "png", "gif").contains(format))
                    return Optional.empty();

                return Optional.of(packedSize(file, name, format, reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }

        } catch (IOException e) {
            log.warn("Leaving out the image {}", file, e);
            return Optional.empty();
        }
    }

    /** The image as packed: a JPEG or PNG over {@value #MAX_PIXELS} pixels shrinks to just under it. */
    private static EpubImage packedSize(Path file, String name, String format, int width, int height) {

        String path = name + "." + (format.equals("jpeg") ? "jpg" : format);
        String mediaType = "image/" + format;
        if ((long) width * height <= MAX_PIXELS || format.equals("gif"))
            return new EpubImage(file, path, mediaType, width, height, false);

        double scale = Math.sqrt((double) MAX_PIXELS / ((long) width * height));
        return new EpubImage(file, path, mediaType, (int) (width * scale), (int) (height * scale), true);
    }

    private static String extension(Path file) {

        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam quality = writer.getDefaultWriteParam();
        quality.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        quality.setCompressionQuality(0.92f);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), quality);
        } finally {
            writer.dispose();
        }

        return bytes.toByteArray();
    }

    private static byte[] png(BufferedImage image) {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return bytes.toByteArray();
    }
}
