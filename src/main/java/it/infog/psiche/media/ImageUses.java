package it.infog.psiche.media;

import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.media.model.MediaImage;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Matches the images in a chapter's folder with the image paragraphs of its text that show them, and changes those
 * paragraphs by the file they show, whatever way their destination is written.
 */
public final class ImageUses {

    // How every image's Markdown starts: a text without it has none, and need not be parsed.
    private static final String IMAGE_START = "![";

    private ImageUses() {
    }

    /**
     * Each of {@code images} with the {@code lines} that show it, those the chapter shows first, in the order it first
     * shows them, then the others by name.
     *
     * @param chaptersDir where the chapter's file is, which the lines' destinations are written from
     */
    public static List<ChapterImage> of(List<MediaImage> images, List<ImageLine> lines, Path chaptersDir) {

        return images.stream()
                .map(image -> new ChapterImage(image, lines.stream()
                        .filter(line -> shows(chaptersDir, line, image.file()))
                        .toList()))
                .sorted(Comparator.comparing((ChapterImage image) -> !image.inChapter())
                        .thenComparingInt(image -> image.inChapter() ? image.uses().getFirst().range().start() : 0)
                        .thenComparing(image -> image.image().name()))
                .toList();
    }

    /** The image paragraphs of {@code text} that show {@code file}, in order. */
    public static List<ImageLine> lines(String text, Path chaptersDir, Path file) {

        return ImageLines.of(text).stream().filter(line -> shows(chaptersDir, line, file)).toList();
    }

    /**
     * The image paragraphs of {@code text} whose file is not there, as after a file was deleted or a past version of
     * the chapter was restored; images on the web are never looked for.
     */
    public static List<ImageLine> missing(String text, Path chaptersDir) {

        if (!text.contains(IMAGE_START))
            return List.of();

        return ImageLines.of(text).stream()
                .filter(line -> !ImageLines.isWebAddress(line.destination()))
                .filter(line -> file(chaptersDir, line).filter(Files::isRegularFile).isEmpty())
                .toList();
    }

    /** {@code text} with every paragraph that shows {@code file} given {@code caption}. */
    public static String withCaption(String text, Path chaptersDir, Path file, String caption) {

        String captioned = text;
        for (String destination : destinations(text, chaptersDir, file))
            captioned = ImageLines.withCaption(captioned, destination, caption);

        return captioned;
    }

    /** {@code text} with every paragraph that shows {@code file} showing the image at {@code destination} instead. */
    public static String withDestination(String text, Path chaptersDir, Path file, String destination) {

        String moved = text;
        for (String from : destinations(text, chaptersDir, file))
            moved = ImageLines.withDestination(moved, from, destination);

        return moved;
    }

    /**
     * {@code text} with each image paragraph that shows a file in {@code from}, the images folder of a chapter just
     * renamed, showing it in the folder of {@code to}, the chapter's new name, as {@link ChapterMedia#rename} moved it.
     */
    public static String withChapter(String text, Path chaptersDir, Path from, String to) {

        if (!text.contains(IMAGE_START))
            return text;

        Path folder = from.normalize();
        Map<String, String> moves = new LinkedHashMap<>();
        for (ImageLine line : ImageLines.of(text))
            file(chaptersDir, line).filter(file -> file.startsWith(folder) && !file.equals(folder))
                    .ifPresent(file -> moves.putIfAbsent(line.destination(),
                            ImageLines.destination(to, inFolder(folder, file))));

        String moved = text;
        for (Map.Entry<String, String> move : moves.entrySet())
            moved = ImageLines.withDestination(moved, move.getKey(), move.getValue());

        return moved;
    }

    /** The file {@code line} shows; empty if its destination is no path, such as a web address. */
    public static Optional<Path> file(Path chaptersDir, ImageLine line) {

        try {
            return Optional.of(chaptersDir.resolve(line.destination()).normalize());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** Where {@code file} is in {@code folder}, its parts joined by slashes as a link writes them. */
    private static String inFolder(Path folder, Path file) {

        Path relative = folder.relativize(file);
        return String.join("/", StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString).toList());
    }

    /** The destinations, each once, that the paragraphs showing {@code file} write. */
    private static Set<String> destinations(String text, Path chaptersDir, Path file) {

        return lines(text, chaptersDir, file).stream().map(ImageLine::destination)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean shows(Path chaptersDir, ImageLine line, Path file) {

        return file(chaptersDir, line).filter(file.normalize()::equals).isPresent();
    }
}
