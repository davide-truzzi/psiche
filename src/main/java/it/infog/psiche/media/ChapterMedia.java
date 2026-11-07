package it.infog.psiche.media;

import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.ImageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static it.infog.psiche.common.ChapterFolders.DELETED_FILE;
import static it.infog.psiche.common.ChapterFolders.deletedFolder;
import static it.infog.psiche.common.ChapterFolders.hasCause;
import static it.infog.psiche.common.ChapterFolders.markCause;
import static it.infog.psiche.common.SafeFiles.changeCase;
import static it.infog.psiche.common.SafeFiles.isTemporary;
import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.common.SafeFiles.temporaryBeside;

/**
 * The images of a book's chapters, in {@code media/}: each chapter with images has a folder there named like the
 * chapter's file, without {@code .md}, holding them. Only those folders are chapter images, which Git keeps with the
 * chapters; the files directly in {@code media/}, such as the book's properties and cover, are the book's own and never
 * touched here. No image belongs to the whole book.
 * <p>
 * A renamed chapter's images move with it. Those of a deleted chapter, and an image the author detaches, are kept in a
 * deleted folder named after the chapter and the time, as {@link ChapterFolders} names them, where
 * {@link DetachedMedia} finds them. Nothing is ever overwritten: an image that meets another of its name keeps it, or
 * takes a number.
 */
public final class ChapterMedia {

    /** The line of the book's {@code .gitignore} that kept the chapters' images out of Git, before 0.5.0. */
    public static final String FORMER_GIT_IGNORE = "media/*/";
    /**
     * The line of the book's {@code .gitignore} that keeps the deleted folders out of Git: what was deleted or detached
     * stays on this device. A digit after the mark, as the time starts, so a chapter such as "fine-del-mondo" is kept.
     */
    public static final String GIT_IGNORE = "media/*" + ChapterFolders.DELETED_MARK + "[0-9]*/";

    private static final Logger log = LoggerFactory.getLogger(ChapterMedia.class);
    // Files whose names start with it are Psiche's own work in progress, never images.
    private static final String HIDDEN_PREFIX = ".";

    // In a deleted folder: what was deleted, an image on its own or its chapter.
    static final String DETACHED_CAUSE = "detached";
    static final String CHAPTER_CAUSE = "chapter";

    private final Path root;
    private final Clock clock;

    /** @param root the book's {@code media/} folder */
    public ChapterMedia(Path root) {

        this(root, Clock.systemDefaultZone());
    }

    /**
     * @param root  the book's {@code media/} folder
     * @param clock the time deleted folders are named after, in its zone
     */
    ChapterMedia(Path root, Clock clock) {

        this.root = root;
        this.clock = clock;
    }

    /** The images off their chapters, which Detached images shows. */
    public DetachedMedia detached() {

        return new DetachedMedia(this, root);
    }

    /** The folder of {@code chapter}'s images, which may not exist yet. */
    public Path folder(String chapter) {

        return ChapterFolders.chapterFolder(root, chapter, "images");
    }

    /**
     * The images in {@code chapter}'s folder, by name; none if it has no folder. A file that is not a JPEG or PNG
     * image, or cannot be read, is left out and logged: one damaged image never hides the rest.
     */
    public List<MediaImage> images(String chapter) {

        return imagesIn(folder(chapter));
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
        try {
            Files.createDirectories(folder);
            Path target = placeIn(folder, source);
            if (!Files.exists(target))
                copy(source, target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + source + " into " + folder, e);
        }
    }

    /**
     * Moves the images of {@code from}, a chapter just renamed, to the folder of {@code to}, its new name. Should that
     * folder have images already, as when it was made by hand, each image joins them as {@link #add} would.
     */
    public void rename(String from, String to) {

        Path source = folder(from);
        Path target = folder(to);
        if (!Files.isDirectory(source) || source.equals(target))
            return;

        try {
            if (!Files.exists(target))
                move(source, target);
            else if (Files.isSameFile(source, target))
                // The same folder under another letter case.
                changeCase(source, target);
            else
                moveImages(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the images of " + from + " to " + to, e);
        }
    }

    /**
     * Moves the images of {@code chapter}, just deleted, to a folder named after it and the time, which frees the
     * name: a chapter that takes it later starts with no images.
     */
    public void binChapter(String chapter) {

        Path source = folder(chapter);
        if (!Files.isDirectory(source))
            return;

        Path bin = deletedFolder(root, chapter, clock);
        try {
            if (Files.exists(bin))
                moveImages(source, bin);
            else
                move(source, bin);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot keep the images of " + chapter + " in " + bin, e);
        }
        markCause(bin, CHAPTER_CAUSE);
    }

    /**
     * Moves {@code image}, one of {@code chapter}'s that its text no longer shows, to a folder named after the chapter
     * and the time, where Detached images finds it.
     */
    public void detach(String chapter, Path image) {

        Path bin = deletedFolder(root, chapter, clock);
        try {
            Files.createDirectories(bin);
            moveInto(image, bin);
            if (!hasCause(bin))
                markCause(bin, DETACHED_CAUSE);
            // A chapter with no images left keeps no folder.
            removeIfEmpty(folder(chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot detach " + image + " from " + chapter, e);
        }
    }

    // Folders, for DetachedMedia too

    /**
     * The images in {@code folder}, by name; none if it is gone. A file that is not a JPEG or PNG image, or cannot be
     * read, is left out and logged: one damaged image never hides the rest.
     */
    static List<MediaImage> imagesIn(Path folder) {

        return imageFiles(folder).stream().map(ChapterMedia::readLogging).flatMap(Optional::stream).toList();
    }

    /** The files in {@code folder} named as JPEG or PNG images, by name, read or not; none if it is gone. */
    static List<Path> imageFiles(Path folder) {

        if (!Files.isDirectory(folder))
            return List.of();

        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(ChapterMedia::isImageFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the images in " + folder, e);
        }
    }

    /**
     * Moves each image of {@code source} into {@code target}, creating it if needed, as {@link #moveInto} does, and
     * removes {@code source} once nothing but Psiche's own files is left in it.
     */
    static void moveImages(Path source, Path target) throws IOException {

        Files.createDirectories(target);
        for (Path image : imageFiles(source))
            moveInto(image, target);
        removeIfEmpty(source);
    }

    /**
     * Moves {@code image} into {@code folder} under its own name; should a file there have it, the image is dropped
     * if that file has its content, or takes the first free name with a number.
     */
    private static void moveInto(Path image, Path folder) throws IOException {

        Path target = placeIn(folder, image);
        if (Files.exists(target))
            Files.delete(image);
        else
            move(image, target);
    }

    /** Removes {@code folder} once it holds nothing but the file saying why it was deleted, if that. */
    private static void removeIfEmpty(Path folder) throws IOException {

        if (!Files.isDirectory(folder))
            return;

        try (Stream<Path> entries = Files.list(folder)) {
            if (entries.anyMatch(entry -> !entry.getFileName().toString().equals(DELETED_FILE)))
                return;
        }
        Files.deleteIfExists(folder.resolve(DELETED_FILE));
        Files.delete(folder);
    }

    /**
     * Where {@code source} goes in {@code folder}: the file there with the same content, if one of its name, or of its
     * name with a number, has it; otherwise its own name, or the first free one with a number, as {@code map-2.png}.
     */
    private static Path placeIn(Path folder, Path source) throws IOException {

        String name = source.getFileName().toString();
        Path target = folder.resolve(name);
        for (int number = 2; Files.exists(target); number++) {
            if (Files.mismatch(source, target) == -1)
                return target;
            target = folder.resolve(numbered(name, number));
        }

        return target;
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
