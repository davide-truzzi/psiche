package it.infog.psiche.export;

import it.infog.psiche.export.model.BrokenReference;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.export.model.enums.ReferenceProblem;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextLink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The images and links of chapters that an export would leave out, so the author can be told before exporting: images
 * whose file is missing or of a type the format cannot show, and links that lead nowhere in the book, as the editor
 * marks them. A web address is never visited, and an image at one is never fetched.
 */
public final class BrokenReferences {

    private BrokenReferences() {
    }

    /**
     * What {@code checked}, some of {@code book}'s chapters, would leave out exported to {@code format}, in the order
     * of the chapters and of their lines.
     *
     * @param book        every chapter of the book, whose headings the links may lead to
     * @param chaptersDir the folder the chapters' images are written from
     */
    public static List<BrokenReference> in(List<ChapterText> checked, List<ChapterText> book, Path chaptersDir,
                                           ExportFormat format) {

        Map<String, Set<String>> anchors = new LinkedHashMap<>();
        book.forEach(chapter -> anchors.put(chapter.fileTitle(), HeadingAnchors.anchors(chapter.markdown())));
        Predicate<Path> shows = format == ExportFormat.EPUB || format == ExportFormat.EPUB_KINDLE
                ? ImageFormats::fitsEpub
                : ImageFormats::fitsDocument;

        List<BrokenReference> found = new ArrayList<>();
        for (ChapterText chapter : checked)
            found.addAll(inChapter(chapter, anchors, chaptersDir, shows));
        return found;
    }

    /** What {@code chapter} would leave out, in the order of its lines. */
    private static List<BrokenReference> inChapter(ChapterText chapter, Map<String, Set<String>> anchors,
                                                   Path chaptersDir, Predicate<Path> shows) {

        String text = chapter.markdown();
        List<Found> found = new ArrayList<>();

        //images
        for (ImageLine image : ImageLines.of(text))
            imageProblem(image.destination(), chaptersDir, shows)
                    .ifPresent(problem -> found.add(new Found(image.range().start(), problem, image.destination())));

        //links
        List<TextLink> links = TextLinks.of(text);
        for (TextLink link : TextLinks.broken(text, links, chapter.fileTitle(), anchors))
            found.add(new Found(link.range().start(), ReferenceProblem.BROKEN_LINK, link.destination()));

        return found.stream()
                .sorted(Comparator.comparingInt(Found::offset))
                .map(each -> new BrokenReference(chapter.fileTitle(), line(text, each.offset()), each.problem(),
                        each.destination()))
                .toList();
    }

    /** What is wrong with the image at {@code destination}; nothing if it shows, or is at a web address. */
    private static Optional<ReferenceProblem> imageProblem(String destination, Path chaptersDir,
                                                           Predicate<Path> shows) {

        if (ImageLines.isWebAddress(destination))
            return Optional.empty();

        Optional<Path> file = ImageLines.file(chaptersDir, destination);
        if (file.isEmpty() || !Files.isRegularFile(file.get()))
            return Optional.of(ReferenceProblem.MISSING_IMAGE);

        return shows.test(file.get()) ? Optional.empty() : Optional.of(ReferenceProblem.UNUSABLE_IMAGE);
    }

    /** The line, from 1, that the character at {@code offset} of {@code text} is on. */
    private static int line(String text, int offset) {

        return (int) text.chars().limit(offset).filter(c -> c == '\n').count() + 1;
    }

    /** A problem found at a character of the chapter, before its line is counted. */
    private record Found(int offset,
                         ReferenceProblem problem,
                         String destination) {
    }
}
