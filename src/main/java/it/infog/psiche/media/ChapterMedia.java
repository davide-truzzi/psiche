package it.infog.psiche.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.ImageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static it.infog.psiche.common.SafeFiles.isTemporary;
import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.common.SafeFiles.temporaryBeside;

/**
 * The images of a book's chapters, in {@code media/}: each chapter with images has a folder there named like the
 * chapter's file, without {@code .md}, holding them. Only those folders are chapter images, kept out of Git by the
 * line {@value #GIT_IGNORE}; the files directly in {@code media/}, such as the book's properties and cover, are the
 * book's own and never touched here. No image belongs to the whole book.
 */
public final class ChapterMedia {

    /** The line of the book's {@code .gitignore} that keeps the chapters' images out of Git. */
    public static final String GIT_IGNORE = "media/*/";

    private static final Logger log = LoggerFactory.getLogger(ChapterMedia.class);
    // Files whose names start with it are Psiche's own work in progress, never images.
    private static final String HIDDEN_PREFIX = ".";

    private final Path root;

    /** @param root the book's {@code media/} folder */
    public ChapterMedia(Path root) {

        this.root = root;
    }

    /** The folder of {@code chapter}'s images, which may not exist yet. */
    public Path folder(String chapter) {

        if (chapter.isBlank() || chapter.contains("/") || chapter.contains("\\") || chapter.equals("..")
                || chapter.equals("."))
            throw new IllegalArgumentException("'%s' cannot name a chapter's images".formatted(chapter));

        return root.resolve(chapter);
    }

    /**
     * The images in {@code chapter}'s folder, by name; none if it has no folder. A file that is not a JPEG or PNG
     * image, or cannot be read, is left out and logged: one damaged image never hides the rest.
     */
    public List<MediaImage> images(String chapter) {

        Path folder = folder(chapter);
        if (!Files.isDirectory(folder))
            return List.of();

        List<Path> files;
        try (Stream<Path> entries = Files.list(folder)) {
            files = entries.filter(ChapterMedia::isImageFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the images in " + folder, e);
        }

        return files.stream().map(ChapterMedia::readLogging).flatMap(Optional::stream).toList();
    }

    /**
     * Copies the image in {@code source} into {@code chapter}'s folder, creating it if needed, under its own name. A
     * file of that name with the same content is used as it is; one with other content keeps it, and the copy takes
     * the first free name with a number, as {@code map-2.png}. Nothing is ever overwritten.
     *
     * @return the image's file in the chapter's folder
     * @throws IllegalArgumentException if {@code source} is not named as a JPEG or PNG image
     */
    public Path add(String chapter, Path source) {

        if (ImageFormat.ofFile(source).isEmpty())
            throw new IllegalArgumentException(source.getFileName() + " is not a JPEG or PNG image");

        Path folder = folder(chapter);
        String name = source.getFileName().toString();
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(name);
            for (int number = 2; Files.exists(target); number++) {
                if (Files.mismatch(source, target) == -1)
                    return target;
                target = folder.resolve(numbered(name, number));
            }

            copy(source, target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + source + " into " + folder, e);
        }
    }

    /** Copies {@code source} to a temporary file beside {@code target}, which then takes its place whole. */
    private static void copy(Path source, Path target) throws IOException {

        Path temporary = temporaryBeside(target);
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** {@code name} with {@code number} before its extension, as {@code map-2.png}. */
    private static String numbered(String name, int number) {

        int dot = name.lastIndexOf('.');
        return dot <= 0
                ? name + "-" + number
                : name.substring(0, dot) + "-" + number + name.substring(dot);
    }

    private static boolean isImageFile(Path file) {

        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && !name.startsWith(HIDDEN_PREFIX) && !isTemporary(name)
                && ImageFormat.ofFile(file).isPresent();
    }

    private static Optional<MediaImage> readLogging(Path file) {

        Optional<MediaImage> image = ImageFiles.tryRead(file);
        if (image.isEmpty())
            log.warn("Skipping {}: it is not a JPEG or PNG image Psiche can read", file);

        return image;
    }
}
